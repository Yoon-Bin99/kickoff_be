package com.kickoff.be.team.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.common.Patchable;
import com.kickoff.be.team.dto.TeamMemberCreateRequest;
import com.kickoff.be.team.dto.TeamMemberResponse;
import com.kickoff.be.team.dto.TeamMemberUpdateRequest;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.TeamMember;
import com.kickoff.be.team.repository.TeamMemberRepository;
import com.kickoff.be.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀원 명단 (계약서 §4-1, v1.8.0).
 *
 * 쓰기 권한은 소유자와 관리자다 (계약서 §4-2 권한표) — 검사는 {@link TeamAuthz} 가 한다.
 * 조회는 인증이 필요 없다. 상대 팀을 보고 신청할지 정하는 정보라 공개가 기본이다.
 */
@Service
@RequiredArgsConstructor
public class TeamMemberService {

    /** 팀당 명단 상한 (계약서 §4-1). */
    private static final int MAX_MEMBERS = 30;

    private final TeamMemberRepository teamMemberRepository;
    private final TeamAuthz teamAuthz;

    /** 인증 불필요. 등번호 오름차순, 등번호 없는 팀원은 뒤에 이름순. */
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> list(Long teamId) {
        teamAuthz.requireTeam(teamId);
        return teamMemberRepository.findByTeamIdOrdered(teamId).stream()
                .map(TeamMemberResponse::of)
                .toList();
    }

    @Transactional
    public TeamMemberResponse add(Long teamId, User user, TeamMemberCreateRequest request) {
        Team team = teamAuthz.requireWriter(teamId, user);
        if (teamMemberRepository.countByTeamId(teamId) >= MAX_MEMBERS) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER_LIMIT);
        }
        return TeamMemberResponse.of(teamMemberRepository.save(TeamMember.builder()
                .team(team)
                .name(request.name())
                .position(request.position())
                .backNumber(request.backNumber())
                .build()));
    }

    /**
     * 이름은 지울 수 없고 포지션·등번호는 명시적 null 로 지운다 (v1.5.1 규칙).
     *
     * 등번호 중복을 막지 않는다. 같은 번호를 쓰는 팀이 실제로 있고, 명단은 앱이 아니라
     * 주장이 아는 사실을 적는 자리다 — 여기서 막으면 사실대로 못 적는다.
     */
    @Transactional
    public TeamMemberResponse update(Long teamId, Long memberId, User user,
                                     TeamMemberUpdateRequest request) {
        teamAuthz.requireWriter(teamId, user);
        TeamMember member = findMember(teamId, memberId);
        Patchable.rejectClear(request.name(), "name");
        requireNameEditable(member, request);
        member.updateName(Patchable.valueOf(request.name()));
        if (Patchable.isPresent(request.position())) {
            member.updatePosition(Patchable.valueOf(request.position()));
        }
        if (Patchable.isPresent(request.backNumber())) {
            member.updateBackNumber(Patchable.valueOf(request.backNumber()));
        }
        return TeamMemberResponse.of(member);
    }

    /**
     * 명단에서 지운다. 계정이 연결된 항목이면 <b>그게 곧 강퇴</b>다 (계약서 §4-3, v1.11.0) —
     * 소속이 명단 항목으로 표현되므로 항목이 사라지면 멤버십도 끝난다.
     *
     * 별도의 강퇴 API 를 두지 않은 건 두 개념이 실제로 하나이기 때문이다. 나눠 놓으면
     * "명단에서만 지웠는데 아직 멤버"인 상태가 생긴다.
     */
    @Transactional
    public void delete(Long teamId, Long memberId, User user) {
        teamAuthz.requireWriter(teamId, user);
        teamMemberRepository.delete(findMember(teamId, memberId));
    }

    /**
     * 계정이 연결된 항목의 이름은 못 바꾼다 (계약서 §4-3, v1.11.0).
     *
     * 그 이름은 닉네임을 따르는 값이라, 여기서 고치면 다음 닉네임 변경 때 되돌아간다.
     * 조용히 무시하면 주장은 바꿨다고 믿고 넘어가므로 400 으로 끊는다. 포지션·등번호는
     * 주장이 채우는 값이라 그대로 고칠 수 있다.
     */
    private void requireNameEditable(TeamMember member, TeamMemberUpdateRequest request) {
        if (member.isLinkedToAccount() && Patchable.isPresent(request.name())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                    new ErrorResponse.FieldError("name",
                            "가입한 팀원의 이름은 닉네임을 따릅니다. 본인이 프로필에서 바꿔야 합니다.")));
        }
    }

    /**
     * 팀에 속한 멤버만 찾는다. id 만으로 찾으면 남의 팀 멤버를 자기 팀 경로로 고칠 수 있다 —
     * 권한 검사는 teamId 로 하는데 대상은 다른 팀 것일 수 있기 때문이다.
     */
    private TeamMember findMember(Long teamId, Long memberId) {
        return teamMemberRepository.findByIdAndTeamId(memberId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
