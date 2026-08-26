package com.kickoff.be.team.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.team.dto.TeamJoinCreateRequest;
import com.kickoff.be.team.dto.TeamJoinRequestItem;
import com.kickoff.be.team.dto.TeamJoinResponse;
import com.kickoff.be.team.entity.JoinStatus;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.TeamJoinRequest;
import com.kickoff.be.team.entity.TeamMember;
import com.kickoff.be.team.repository.TeamJoinRequestRepository;
import com.kickoff.be.team.repository.TeamMemberRepository;
import com.kickoff.be.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 가입 신청 (계약서 §4-3, v1.11.0).
 *
 * 상태 전이는 매칭 신청(§6)과 같은 모양이다 — PENDING 에서만 갈라지고, 거절·취소 이력은
 * 재신청을 막지 않는다. 그래서 "대기 중인 신청이 있는가"만 보고 과거 이력은 보지 않는다.
 *
 * 수락·거절은 소유자와 관리자가 한다 (§4-2 권한표의 "팀 페이지 쓰기"에 해당). 신청·취소·
 * 탈퇴는 본인이 한다.
 */
@Service
@RequiredArgsConstructor
public class TeamJoinService {

    /** 명단 상한. 가입 승인도 명단에 한 줄을 만들므로 같은 제한을 받는다 (계약서 §4-1). */
    private static final int MAX_MEMBERS = 30;

    private final TeamJoinRequestRepository joinRequestRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamAuthz teamAuthz;

    @Transactional
    public TeamJoinResponse apply(Long teamId, User user, TeamJoinCreateRequest request) {
        Team team = teamAuthz.requireTeam(teamId);
        // 이미 소속이면 신청할 게 없다. 소유자·관리자·멤버 전부 해당한다 (계약서 §4-3).
        if (teamAuthz.isMemberOfAnyKind(team, user.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_TEAM_MEMBER);
        }
        if (joinRequestRepository.existsByTeamIdAndUserIdAndStatus(
                teamId, user.getId(), JoinStatus.PENDING)) {
            throw new BusinessException(ErrorCode.JOIN_ALREADY_REQUESTED);
        }
        return TeamJoinResponse.of(joinRequestRepository.save(TeamJoinRequest.builder()
                .team(team)
                .user(user)
                .message(request == null ? null : request.message())
                .build()));
    }

    /** 소유자·관리자만. 오래된 순 — 먼저 온 신청부터 처리한다 (계약서 §4-3). */
    @Transactional(readOnly = true)
    public List<TeamJoinRequestItem> pendingRequests(Long teamId, User user) {
        teamAuthz.requireWriter(teamId, user);
        return joinRequestRepository
                .findByTeamIdAndStatusOrderByIdAsc(teamId, JoinStatus.PENDING).stream()
                .map(TeamJoinRequestItem::of)
                .toList();
    }

    /**
     * 수락 — 신청자가 MEMBER 가 되고 명단에 자동 등재된다 (계약서 §4-3).
     *
     * 명단이 꽉 차 있으면 400 이고 <b>신청은 PENDING 으로 남는다</b>. 예외가 트랜잭션을
     * 되돌리므로 상태 변경도 함께 사라진다 — 자리를 비운 뒤 다시 수락하면 된다.
     * 여기서 신청을 거절 처리해 버리면 주장이 자리를 만든 뒤에도 되살릴 방법이 없다.
     *
     * 등재되는 항목의 name 은 가입자 닉네임이고 position·backNumber 는 비어 있다.
     * 주장·관리자가 나중에 채운다.
     */
    @Transactional
    public void accept(Long teamId, Long joinId, User user) {
        teamAuthz.requireWriter(teamId, user);
        TeamJoinRequest joinRequest = findPending(teamId, joinId);
        if (teamMemberRepository.countByTeamId(teamId) >= MAX_MEMBERS) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER_LIMIT);
        }
        joinRequest.accept();
        teamMemberRepository.save(TeamMember.builder()
                .team(joinRequest.getTeam())
                .name(joinRequest.getUser().getNickname())
                .user(joinRequest.getUser())
                .build());
    }

    @Transactional
    public void reject(Long teamId, Long joinId, User user) {
        teamAuthz.requireWriter(teamId, user);
        findPending(teamId, joinId).reject();
    }

    /** 신청자 본인이 자기 대기 신청을 물린다 (계약서 §4-3). */
    @Transactional
    public void cancel(Long teamId, User user) {
        teamAuthz.requireTeam(teamId);
        joinRequestRepository
                .findByTeamIdAndUserIdAndStatus(teamId, user.getId(), JoinStatus.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOIN_NOT_FOUND))
                .cancel();
    }

    /**
     * 탈퇴 — 명단 항목도 함께 지운다 (계약서 §4-3).
     *
     * 소유자·관리자는 이 경로로 못 나간다. 관리자는 임명을 해제받아야 하고, 소유자가 팀을
     * 떠나는 건 v1 범위 밖이다 — 팀에 주인이 없어지는 상태를 만들 방법이 없어야 한다.
     */
    @Transactional
    public void leave(Long teamId, User user) {
        Team team = teamAuthz.requireTeam(teamId);
        TeamMember membership = teamMemberRepository
                .findByTeamIdAndUser_Id(teamId, user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (team.isOwnedBy(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        teamMemberRepository.delete(membership);
    }

    /**
     * 팀 페이지의 myJoinStatus (계약서 §4-3, FE 제안 반영).
     *
     * 대기 중이면 PENDING, 아니면 null 이다. 거절·취소 이력을 구분해 주지 않는 건 재신청이
     * 허용되기 때문이다 — FE 가 할 일이 "신청 버튼을 보여준다"로 같다.
     */
    @Transactional(readOnly = true)
    public JoinStatus myJoinStatus(Long teamId, Long userId) {
        if (userId == null) {
            return null;
        }
        return joinRequestRepository.existsByTeamIdAndUserIdAndStatus(
                teamId, userId, JoinStatus.PENDING) ? JoinStatus.PENDING : null;
    }

    private TeamJoinRequest findPending(Long teamId, Long joinId) {
        TeamJoinRequest joinRequest = joinRequestRepository.findByIdAndTeamId(joinId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOIN_NOT_FOUND));
        if (!joinRequest.isPending()) {
            throw new BusinessException(ErrorCode.JOIN_NOT_PENDING);
        }
        return joinRequest;
    }
}
