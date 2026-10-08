package com.kickoff.be.team.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.TeamRole;
import com.kickoff.be.team.repository.TeamAdminRepository;
import com.kickoff.be.team.repository.TeamMemberRepository;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀에 대한 권한 검사를 한 곳에 모은다 (계약서 §4-2 권한표, v1.9.0).
 *
 * 흩어 놓으면 다음 권한 변경 때 반드시 하나를 빠뜨린다. 실제로 v1.9.0 이 §4-1 의
 * "소유자만"을 전부 "소유자·관리자"로 바꿨는데, 검사가 서비스마다 흩어져 있었다면
 * 명단은 고치고 기록은 잊는 식으로 갈렸을 것이다.
 *
 * 경계는 계약서 권한표 그대로다.
 * <ul>
 *   <li>{@link #requireWriter} — 팀 정보 PATCH, 팀원 명단·경기 기록 쓰기. OWNER·ADMIN</li>
 *   <li>{@link #requireOwner} — 관리자 임명·해제. OWNER 전용</li>
 * </ul>
 *
 * 모집글·신청·리뷰는 팀이 아니라 각 도메인이 자기 규칙으로 막는다. 그쪽은 "글 작성자"나
 * "신청 팀" 기준이라 팀 역할과 축이 다르고, 계약서도 OWNER 전용으로 남겨 뒀다.
 *
 * 팀 보유 여부(hasTeam)는 여기서 보지 않는다. 팀이 없는 사용자도 남의 팀 관리자가 될 수
 * 있기 때문이다 (계약서 §4-2).
 */
@Component
@RequiredArgsConstructor
public class TeamAuthz {

    private final TeamRepository teamRepository;
    private final TeamAdminRepository teamAdminRepository;
    private final TeamMemberRepository teamMemberRepository;

    /** 팀을 찾는다. 없으면 404. 소유자 닉네임까지 쓰는 자리라 owner 를 함께 가져온다. */
    @Transactional(readOnly = true)
    public Team requireTeam(Long teamId) {
        return teamRepository.findWithOwnerById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
    }

    /**
     * 팀 페이지를 고칠 수 있는 사람 — 소유자 또는 관리자 (계약서 §4-2 권한표).
     *
     * <b>MEMBER 는 통과하지 못한다.</b> v1.11.0 이전에는 "역할이 있으면 통과"로 써도 같은
     * 뜻이었지만, MEMBER 가 생기면서 그 표현이 조용히 틀린 것이 됐다 — 소속이라는 이유만으로
     * 남의 팀 정보를 고칠 수 있게 된다. 그래서 허용 역할을 열거로 못 박는다.
     */
    @Transactional(readOnly = true)
    public Team requireWriter(Long teamId, User user) {
        Team team = requireTeam(teamId);
        // 쓰기 경로는 전부 시큐리티에서 인증을 걸어 user 가 null 일 수 없다. 그래도
        // 보는 이유는 v1.28.0 에서 스쿼드 조회를 permitAll 로 열었기 때문이다 — 나중에
        // 누가 같은 경로의 쓰기까지 열면, 이 줄이 없으면 500(NPE)이 난다.
        TeamRole role = user == null ? null : roleOf(team, user.getId());
        if (role != TeamRole.OWNER && role != TeamRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return team;
    }

    /**
     * 그 팀 소속이면 누구나 — OWNER·ADMIN·MEMBER (계약서 §4-3). 스쿼드 읽기가 이 경계다.
     *
     * <b>비로그인도 403 이다</b>(401 이 아니다). 계약서 §4-4 가 "비소속·비로그인은 403
     * FORBIDDEN"으로 못박았다. 그래서 이 경로는 시큐리티에서 인증을 걸지 않고 — 걸면
     * 토큰 없는 요청이 필터에서 401 로 끊겨 여기까지 오지 않는다 — 여기서 user 가 null 인
     * 것을 권한 없음으로 처리한다.
     *
     * <b>그래서 이 검사를 빠뜨리면 스쿼드가 공개된다.</b> 읽기 경로의 첫 줄이어야 한다.
     */
    @Transactional(readOnly = true)
    public Team requireMember(Long teamId, User user) {
        Team team = requireTeam(teamId);
        if (user == null || roleOf(team, user.getId()) == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return team;
    }

    /** 소유자 전용 행위 — 관리자 임명·해제 (계약서 §4-2 권한표). */
    @Transactional(readOnly = true)
    public Team requireOwner(Long teamId, User user) {
        Team team = requireTeam(teamId);
        if (!team.isOwnedBy(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return team;
    }

    /**
     * 이 사용자의 역할. 관계가 없거나 비로그인이면 <b>null</b> 이다 (계약서 §4-2·§4-3).
     *
     * 판정 순서는 OWNER > ADMIN > MEMBER 다 (v1.11.0). 한 사람이 소유자이면서 명단에도
     * 올라 있을 수 있는데, 그때 MEMBER 로 답하면 화면에서 권한이 사라진다.
     *
     * 소유자를 먼저 보는 건 서열 때문이기도 하고 쿼리가 필요 없어서이기도 하다.
     */
    @Transactional(readOnly = true)
    public TeamRole roleOf(Team team, Long userId) {
        if (userId == null) {
            return null;
        }
        if (team.isOwnedBy(userId)) {
            return TeamRole.OWNER;
        }
        if (teamAdminRepository.existsByTeamIdAndUserId(team.getId(), userId)) {
            return TeamRole.ADMIN;
        }
        return teamMemberRepository.existsByTeamIdAndUser_Id(team.getId(), userId)
                ? TeamRole.MEMBER
                : null;
    }

    /** 이미 이 팀 소속인지 (계약서 §4-3). 가입 신청을 막는 조건이다. */
    @Transactional(readOnly = true)
    public boolean isMemberOfAnyKind(Team team, Long userId) {
        return roleOf(team, userId) != null;
    }
}
