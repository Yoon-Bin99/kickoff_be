package com.kickoff.be.team.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.TeamRole;
import com.kickoff.be.team.repository.TeamAdminRepository;
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

    /** 팀을 찾는다. 없으면 404. 소유자 닉네임까지 쓰는 자리라 owner 를 함께 가져온다. */
    @Transactional(readOnly = true)
    public Team requireTeam(Long teamId) {
        return teamRepository.findWithOwnerById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
    }

    /** 팀 페이지를 고칠 수 있는 사람 — 소유자 또는 관리자 (계약서 §4-2 권한표). */
    @Transactional(readOnly = true)
    public Team requireWriter(Long teamId, User user) {
        Team team = requireTeam(teamId);
        if (roleOf(team, user.getId()) == null) {
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
     * 이 사용자의 역할. 관계가 없거나 비로그인이면 <b>null</b> 이다 (계약서 §4-2).
     *
     * 소유자를 먼저 본다. 소유자는 team_admins 에 들어가지 않으므로 순서가 결과를 바꾸지는
     * 않지만, 소유자 판정에 쿼리가 필요 없다는 점에서 이 순서가 싸다.
     */
    @Transactional(readOnly = true)
    public TeamRole roleOf(Team team, Long userId) {
        if (userId == null) {
            return null;
        }
        if (team.isOwnedBy(userId)) {
            return TeamRole.OWNER;
        }
        return teamAdminRepository.existsByTeamIdAndUserId(team.getId(), userId)
                ? TeamRole.ADMIN
                : null;
    }
}
