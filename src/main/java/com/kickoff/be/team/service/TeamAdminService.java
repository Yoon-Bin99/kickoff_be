package com.kickoff.be.team.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.team.dto.MyTeamResponse;
import com.kickoff.be.team.dto.TeamAdminCreateRequest;
import com.kickoff.be.team.dto.TeamAdminResponse;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.TeamAdmin;
import com.kickoff.be.team.entity.TeamRole;
import com.kickoff.be.team.repository.TeamAdminRepository;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 관리자 임명·해제 (계약서 §4-2, v1.9.0).
 *
 * 임명·해제는 <b>소유자 전용</b>이다. 관리자가 관리자를 임명할 수 있으면 소유자 모르게
 * 권한이 번져 나가고, 소유자가 되돌리려면 누가 누구를 불렀는지부터 추적해야 한다.
 * 목록 조회만 관리자에게도 열려 있다 — 자기와 같은 권한을 가진 사람이 누군지는 알아야 한다.
 */
@Service
@RequiredArgsConstructor
public class TeamAdminService {

    /** 팀당 관리자 수 상한 (계약서 §4-2). */
    private static final int MAX_ADMINS = 5;

    private final TeamAdminRepository teamAdminRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final ReviewRepository reviewRepository;
    private final TeamAuthz teamAuthz;

    /** 소유자·관리자만 볼 수 있다 (계약서 §4-2). 임명순. */
    @Transactional(readOnly = true)
    public List<TeamAdminResponse> list(Long teamId, User user) {
        teamAuthz.requireWriter(teamId, user);
        return teamAdminRepository.findByTeamIdOrderByIdAsc(teamId).stream()
                .map(TeamAdminResponse::of)
                .toList();
    }

    /**
     * 내가 소유·관리하는 팀 목록 (계약서 §4-2, v1.9.1). 없으면 빈 배열이고 에러가 아니다.
     *
     * 소유 팀을 먼저, 관리 팀은 임명순으로 잇는다. 소유는 최대 하나라 정렬이랄 게 없다.
     */
    @Transactional(readOnly = true)
    public List<MyTeamResponse> myTeams(User user) {
        List<Team> owned = teamRepository.findByOwnerId(user.getId())
                .map(List::of)
                .orElseGet(List::of);
        List<Team> administered = teamAdminRepository.findByUserIdOrderByIdAsc(user.getId())
                .stream()
                .map(TeamAdmin::getTeam)
                .toList();
        // 팀마다 평점을 조회하면 팀 수만큼 쿼리가 는다 (계약서 §2, v1.10.0)
        Map<Long, ReviewStats> stats = reviewRepository.statsMapOf(
                Stream.concat(owned.stream(), administered.stream()).map(Team::getId).toList());
        return Stream.concat(
                        owned.stream().map(team -> MyTeamResponse.of(team, TeamRole.OWNER,
                                statOf(stats, team))),
                        administered.stream().map(team -> MyTeamResponse.of(team, TeamRole.ADMIN,
                                statOf(stats, team))))
                .toList();
    }

    private static ReviewStats statOf(Map<Long, ReviewStats> stats, Team team) {
        return stats.getOrDefault(team.getId(), ReviewStats.EMPTY);
    }

    @Transactional
    public TeamAdminResponse grant(Long teamId, User owner, TeamAdminCreateRequest request) {
        Team team = teamAuthz.requireOwner(teamId, owner);
        User target = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 소유자 본인을 임명하는 것도 409 다 (계약서 §4-2). 이미 그보다 넓은 권한을 가졌고,
        // 허용하면 "소유자이면서 관리자"라는 애매한 상태가 생긴다.
        if (team.isOwnedBy(target.getId())
                || teamAdminRepository.existsByTeamIdAndUserId(teamId, target.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_TEAM_ADMIN);
        }
        if (teamAdminRepository.countByTeamId(teamId) >= MAX_ADMINS) {
            throw new BusinessException(ErrorCode.TEAM_ADMIN_LIMIT);
        }
        return TeamAdminResponse.of(teamAdminRepository.save(TeamAdmin.builder()
                .team(team)
                .user(target)
                .build()));
    }

    @Transactional
    public void revoke(Long teamId, Long userId, User owner) {
        teamAuthz.requireOwner(teamId, owner);
        TeamAdmin admin = teamAdminRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        teamAdminRepository.delete(admin);
    }
}
