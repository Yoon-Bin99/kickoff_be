package com.kickoff.be.team.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.team.dto.TeamCreateRequest;
import com.kickoff.be.team.dto.TeamResponse;
import com.kickoff.be.team.dto.TeamUpdateRequest;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final ReviewRepository reviewRepository;

    @Transactional
    public TeamResponse create(User owner, TeamCreateRequest request) {
        if (teamRepository.existsByOwnerId(owner.getId())) {
            throw new BusinessException(ErrorCode.TEAM_ALREADY_EXISTS);
        }
        // 매칭이 성사되면 팀 대표의 전화번호가 상대에게 공개된다. 소셜 가입자는 전화번호가
        // 없을 수 있어 여기서 막고, FE 는 이 코드를 받으면 입력 화면으로 보낸다 (계약서 §4).
        if (!owner.hasPhone()) {
            throw new BusinessException(ErrorCode.PHONE_REQUIRED);
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
        // 방금 만든 팀이라 받은 리뷰가 있을 수 없다.
        return TeamResponse.of(team, owner.getId(), ReviewStats.EMPTY);
    }

    @Transactional(readOnly = true)
    public TeamResponse getMine(User user) {
        Team team = teamRepository.findWithOwnerByOwnerId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
        return TeamResponse.of(team, user.getId(), reviewRepository.statsOf(team.getId()));
    }

    /** 인증 불필요 — viewer 가 null 이면 isMine 은 false. */
    @Transactional(readOnly = true)
    public TeamResponse get(Long teamId, User viewer) {
        Team team = teamRepository.findWithOwnerById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
        return TeamResponse.of(team, viewer == null ? null : viewer.getId(),
                reviewRepository.statsOf(team.getId()));
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
        return TeamResponse.of(team, user.getId(), reviewRepository.statsOf(team.getId()));
    }
}
