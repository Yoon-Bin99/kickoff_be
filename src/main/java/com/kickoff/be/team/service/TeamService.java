package com.kickoff.be.team.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.Patchable;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.review.dto.ReviewStats;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.team.dto.RecordSummary;
import com.kickoff.be.team.dto.TeamCreateRequest;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.team.dto.TeamResponse;
import com.kickoff.be.team.dto.TeamSummary;
import com.kickoff.be.team.dto.TeamUpdateRequest;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.TeamRole;
import com.kickoff.be.team.repository.TeamRecordRepository;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.user.entity.User;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    /** 검색어에서 지울 공백 (계약서 §4, v1.14.0). 탭·줄바꿈까지 포함한다. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final TeamRepository teamRepository;
    private final ReviewRepository reviewRepository;
    private final TeamRecordRepository teamRecordRepository;
    private final TeamAuthz teamAuthz;
    private final TeamJoinService teamJoinService;

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
        requireFoundedYearNotInFuture(request.foundedYear());
        Team team = teamRepository.save(Team.builder()
                .owner(owner)
                .name(request.name())
                .region(request.region())
                .homeGround(request.homeGround())
                .skillLevel(request.skillLevel())
                .ageGroup(request.ageGroup())
                .memberCount(request.memberCount())
                .introduction(request.introduction())
                .foundedYear(request.foundedYear())
                .teamColor(request.teamColor())
                .formation(request.formation())
                .build());
        // 방금 만든 팀이라 리뷰도 기록도 있을 수 없고, 만든 사람이 곧 소유자다.
        // 방금 만든 팀이라 가입 신청도 있을 수 없다.
        return TeamResponse.of(team, owner.getId(), ReviewStats.EMPTY, TeamRole.OWNER,
                RecordSummary.EMPTY, null);
    }

    @Transactional(readOnly = true)
    public TeamResponse getMine(User user) {
        Team team = teamRepository.findWithOwnerByOwnerId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
        // 소유 기준으로 찾아온 팀이라 역할을 조회할 필요가 없다.
        // 소유자가 자기 팀에 가입 신청을 낼 일은 없다 (낼 수도 없다 — 409).
        return TeamResponse.of(team, user.getId(), reviewRepository.statsOf(team.getId()),
                TeamRole.OWNER, teamRecordRepository.summaryOf(team.getId()), null);
    }

    /**
     * 팀 찾기 (계약서 §4, v1.11.0 / 매칭·정렬은 v1.14.0). 인증 불필요.
     *
     * 카드에 평점이 실리므로 팀별 집계를 배치로 모은다 — 카드마다 조회하면 N+1 이다.
     */
    @Transactional(readOnly = true)
    public PageResponse<TeamSummary> search(String keyword, String region, int page, int size) {
        Page<Team> teams = teamRepository.search(normalizeKeyword(keyword), blankToNull(region),
                PageRequest.of(Math.max(page, 0), clampSize(size)));
        Map<Long, ReviewStats> stats = reviewRepository.statsMapOf(
                teams.getContent().stream().map(Team::getId).toList());
        return PageResponse.of(teams, team -> TeamSummary.of(team,
                stats.getOrDefault(team.getId(), ReviewStats.EMPTY)));
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** 빈 문자열은 필터를 안 건 것과 같다 — 목록 조회와 같은 처리다. */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 검색어 정규화 (계약서 §4, v1.14.0) — 공백을 지우고 소문자로 내린다.
     *
     * 쿼리가 팀 이름에 하는 것과 같은 처리를 검색어에 미리 해 둔다. 여기서 하는 이유는
     * 두 가지다. 파라미터를 함수로 감싸면 DB 가 매 행마다 계산해야 하고, 무엇보다
     * <b>자바와 SQL 의 소문자 변환이 갈릴 여지</b>를 한쪽으로 몰아 둘 수 있다.
     *
     * {@code Locale.ROOT} 를 명시하는 게 중요하다. 인자 없는 {@code toLowerCase()} 는
     * <b>실행 환경의 기본 로캘</b>을 쓰는데, 터키어 로캘에서는 'I' 가 점 없는 'ı' 로
     * 내려간다. 그러면 "FC Istanbul" 을 "istanbul" 로 검색했을 때 서버 로캘에 따라
     * 찾히기도 안 찾히기도 한다 — 에러 없이 결과만 달라지고, 우리 환경에서는 재현조차
     * 안 되는 종류의 고장이다.
     *
     * 공백은 자바 쪽에서 모든 공백문자를 지우고 SQL 쪽은 보통 공백만 지운다. 일부러
     * 어긋내 둔 것이다 — 탭이나 줄바꿈이 섞여 들어오는 쪽은 붙여넣기가 일어나는 검색창이지
     * 팀 이름이 아니다. 넓게 지우는 쪽을 사용자 입력에 둔다.
     */
    private String normalizeKeyword(String keyword) {
        String trimmed = blankToNull(keyword);
        if (trimmed == null) {
            return null;
        }
        return WHITESPACE.matcher(trimmed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** 인증 불필요 — viewer 가 null 이면 isMine 은 false 이고 myRole 은 null 이다. */
    @Transactional(readOnly = true)
    public TeamResponse get(Long teamId, User viewer) {
        Team team = teamAuthz.requireTeam(teamId);
        Long viewerId = viewer == null ? null : viewer.getId();
        return TeamResponse.of(team, viewerId, reviewRepository.statsOf(team.getId()),
                teamAuthz.roleOf(team, viewerId), teamRecordRepository.summaryOf(team.getId()),
                teamJoinService.myJoinStatus(teamId, viewerId));
    }

    @Transactional
    public TeamResponse update(Long teamId, User user, TeamUpdateRequest request) {
        // v1.9.0 부터 관리자도 팀 정보를 고칠 수 있다 (계약서 §4-2 권한표).
        Team team = teamAuthz.requireWriter(teamId, user);
        Patchable.rejectClear(request.name(), "name");
        Patchable.rejectClear(request.region(), "region");
        Patchable.rejectClear(request.skillLevel(), "skillLevel");
        Patchable.rejectClear(request.ageGroup(), "ageGroup");
        Patchable.rejectClear(request.memberCount(), "memberCount");
        team.update(Patchable.valueOf(request.name()), Patchable.valueOf(request.region()),
                Patchable.valueOf(request.skillLevel()), Patchable.valueOf(request.ageGroup()),
                Patchable.valueOf(request.memberCount()));
        // 홈 구장·소개만 지울 수 있다 (계약서 §4, v1.5.1). 여기서는 null 이 "지우기"다.
        if (Patchable.isPresent(request.homeGround())) {
            team.updateHomeGround(Patchable.valueOf(request.homeGround()));
        }
        if (Patchable.isPresent(request.introduction())) {
            team.updateIntroduction(Patchable.valueOf(request.introduction()));
        }
        applyProfilePatch(team, request);
        // 고칠 수 있는 사람은 이미 소속이라 대기 중인 신청이 있을 수 없다.
        return TeamResponse.of(team, user.getId(), reviewRepository.statsOf(team.getId()),
                teamAuthz.roleOf(team, user.getId()),
                teamRecordRepository.summaryOf(team.getId()), null);
    }

    /** 팀 프로필 확장 필드 (계약서 §4-1, v1.8.0). 셋 다 명시적 null 로 지울 수 있다. */
    private void applyProfilePatch(Team team, TeamUpdateRequest request) {
        if (Patchable.isPresent(request.foundedYear())) {
            requireFoundedYearNotInFuture(Patchable.valueOf(request.foundedYear()));
            team.updateFoundedYear(Patchable.valueOf(request.foundedYear()));
        }
        if (Patchable.isPresent(request.teamColor())) {
            team.updateTeamColor(Patchable.valueOf(request.teamColor()));
        }
        if (Patchable.isPresent(request.formation())) {
            team.updateFormation(Patchable.valueOf(request.formation()));
        }
    }

    /**
     * 창단 연도 상한은 "올해"다 (계약서 §4-1). 어노테이션 상수로는 못 박는다 — 해가 바뀌면
     * 상한도 같이 올라가야 하는데 @Max 는 컴파일 시점에 굳는다. 하한 1900 만 어노테이션이
     * 맡고 상한은 여기서 본다.
     */
    private void requireFoundedYearNotInFuture(Integer foundedYear) {
        if (foundedYear != null && foundedYear > Year.now().getValue()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                    new ErrorResponse.FieldError("foundedYear", "창단 연도는 올해 이하여야 합니다.")));
        }
    }
}
