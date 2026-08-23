package com.kickoff.be.team;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.team.dto.TeamCreateRequest;
import com.kickoff.be.team.dto.TeamResponse;
import com.kickoff.be.team.dto.TeamUpdateRequest;
import com.kickoff.be.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;

    @Transactional
    public TeamResponse create(User owner, TeamCreateRequest request) {
        if (teamRepository.existsByOwnerId(owner.getId())) {
            throw new BusinessException(ErrorCode.TEAM_ALREADY_EXISTS);
        }
        Team team = teamRepository.save(Team.builder()
                .owner(owner)
                .name(request.name())
                .region(request.region())
                .homeGround(request.homeGround())
                .skillLevel(request.skillLevel())
                .ageGroup(request.ageGroup())
                .memberCount(request.memberCount())
                .introduction(request.introduction())
                .build());
        return TeamResponse.of(team, owner.getId());
    }

    @Transactional(readOnly = true)
    public TeamResponse getMine(User user) {
        Team team = teamRepository.findWithOwnerByOwnerId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
        return TeamResponse.of(team, user.getId());
    }

    /** 인증 불필요 — viewer 가 null 이면 isMine 은 false. */
    @Transactional(readOnly = true)
    public TeamResponse get(Long teamId, User viewer) {
        Team team = teamRepository.findWithOwnerById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
        return TeamResponse.of(team, viewer == null ? null : viewer.getId());
    }

    @Transactional
    public TeamResponse update(Long teamId, User user, TeamUpdateRequest request) {
        Team team = teamRepository.findWithOwnerById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
        if (!team.isOwnedBy(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        team.update(request.name(), request.region(), request.homeGround(), request.skillLevel(),
                request.ageGroup(), request.memberCount(), request.introduction());
        return TeamResponse.of(team, user.getId());
    }
}
