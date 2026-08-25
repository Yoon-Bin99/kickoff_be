package com.kickoff.be.post.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ContactInfo;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.common.Patchable;
import com.kickoff.be.common.PaymentInfo;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.entity.RequestStatus;
import com.kickoff.be.matchrequest.repository.MatchRequestRepository;
import com.kickoff.be.matchrequest.repository.PostRequestCount;
import com.kickoff.be.post.dto.PostCreateRequest;
import com.kickoff.be.post.dto.PostDetail;
import com.kickoff.be.post.dto.PostSummary;
import com.kickoff.be.post.dto.PostUpdateRequest;
import com.kickoff.be.post.entity.FieldType;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.post.repository.MatchPostRepository;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.team.entity.SkillLevel;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final MatchPostRepository postRepository;
    private final TeamRepository teamRepository;
    private final MatchRequestRepository requestRepository;
    private final ReviewRepository reviewRepository;

    /**
     * 정렬은 matchAt 오름차순 고정 — 가까운 경기가 먼저 (계약서 §5).
     * 지난 경기는 status 필터와 무관하게 빠진다.
     */
    @Transactional(readOnly = true)
    public PageResponse<PostSummary> search(String region, FieldType fieldType, SkillLevel skillLevel,
                                            PostStatus status, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                clampSize(size),
                Sort.by(Sort.Direction.ASC, "matchAt"));
        Page<MatchPost> posts = postRepository.search(OffsetDateTime.now(),
                blankToNull(region), fieldType, skillLevel, status, blankToNull(keyword), pageable);
        return toSummaryPage(posts);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostSummary> getMine(User user, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Team team = teamRepository.findByOwnerId(user.getId()).orElse(null);
        if (team == null) {
            // 팀이 없으면 쓴 글도 없다. 목록 조회는 TEAM_REQUIRED 대상이 아니라 빈 페이지로 돌려준다.
            return PageResponse.of(new PageImpl<PostSummary>(List.of(), pageable, 0));
        }
        return toSummaryPage(postRepository.findByTeamId(team.getId(), pageable));
    }

    @Transactional
    public PostDetail create(User user, PostCreateRequest request) {
        Team team = teamRepository.findWithOwnerByOwnerId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_REQUIRED));
        requireAccountWhenDepositSet(request.depositAmount(), request.bankName(),
                request.accountNumber(), request.accountHolder());
        requireCoordinatePair(request.latitude(), request.longitude());
        MatchPost post = postRepository.save(MatchPost.builder()
                .team(team)
                .title(request.title())
                .content(request.content())
                .matchAt(request.matchAt())
                .location(request.location())
                .region(request.region())
                .fieldType(request.fieldType())
                .preferredSkillLevel(request.preferredSkillLevel())
                .rentalFee(request.rentalFee())
                .depositAmount(request.depositAmount())
                .bankName(request.bankName())
                .accountNumber(request.accountNumber())
                .accountHolder(request.accountHolder())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build());
        return toDetail(post, user);
    }

    /** 인증 불필요 — viewer 가 null 이면 isAuthor 는 false. 조회할 때마다 조회수가 오른다. */
    @Transactional
    public PostDetail get(Long postId, User viewer) {
        MatchPost post = findPost(postId);
        post.increaseViewCount();
        return toDetail(post, viewer);
    }

    @Transactional
    public PostDetail update(Long postId, User user, PostUpdateRequest request) {
        MatchPost post = findPost(postId);
        requireAuthor(post, user);
        requireNotCleared(request);
        applyCoordinatePatch(post, request.latitude(), request.longitude());
        applyClearableFields(post, request);
        post.update(Patchable.valueOf(request.title()), Patchable.valueOf(request.content()),
                Patchable.valueOf(request.matchAt()), Patchable.valueOf(request.location()),
                Patchable.valueOf(request.region()), Patchable.valueOf(request.fieldType()),
                Patchable.valueOf(request.status()));
        // 병합된 결과를 기준으로 본다. 계좌는 작성자도 다시 읽을 수 없어서,
        // PATCH 본문만 보고 판단하면 금액만 고치는 정상 요청이 막혀버린다.
        requireAccountWhenDepositSet(post.getDepositAmount(), post.getBankName(),
                post.getAccountNumber(), post.getAccountHolder());
        return toDetail(post, user);
    }

    @Transactional
    public void delete(Long postId, User user) {
        MatchPost post = findPost(postId);
        requireAuthor(post, user);
        postRepository.delete(post);
    }

    /**
     * 지울 수 없는 필드에 명시적 {@code null} 이 오면 400 이다 (계약서 §5, v1.5.1).
     *
     * 여기서 막지 않으면 "안 보냄"과 같아져 조용히 무시된다. FE 가 실수로 null 을 보내면
     * 아무 일도 안 일어나는데 200 이 오므로, 반영된 줄 알고 넘어가게 된다.
     *
     * status 는 계약서의 필수 필드 목록에 없지만 컬럼이 not null 이라 지울 수 없다.
     */
    private void requireNotCleared(PostUpdateRequest request) {
        Patchable.rejectClear(request.title(), "title");
        Patchable.rejectClear(request.content(), "content");
        Patchable.rejectClear(request.matchAt(), "matchAt");
        Patchable.rejectClear(request.location(), "location");
        Patchable.rejectClear(request.region(), "region");
        Patchable.rejectClear(request.fieldType(), "fieldType");
        Patchable.rejectClear(request.status(), "status");
    }

    /** 지울 수 있는 필드들 (계약서 §5, v1.5.1). 여기서는 명시적 null 이 "지우기"다. */
    private void applyClearableFields(MatchPost post, PostUpdateRequest request) {
        if (Patchable.isPresent(request.preferredSkillLevel())) {
            post.updatePreferredSkillLevel(Patchable.valueOf(request.preferredSkillLevel()));
        }
        if (Patchable.isPresent(request.rentalFee())) {
            post.updateRentalFee(Patchable.valueOf(request.rentalFee()));
        }
        applyDepositPatch(post, request);
    }

    /**
     * 입금액·계좌 처리 (계약서 §5, v1.5.1).
     *
     * 입금액을 지우면 계좌 3필드도 <b>함께</b> 지운다. 금액 없는 계좌를 남기면 "무료 경기인데
     * 입금 안내가 붙은" 글이 되는데, 이건 좌표 버그와 같은 종류다 — 글에 쓰인 내용과 실제가
     * 어긋나고 그게 사용자에게 그대로 보인다.
     *
     * 반대로 계좌만 개별로 지우는 것은 400 이다. 허용하면 "금액은 5만원인데 계좌는 없음"이
     * 만들어지는데, 그건 아래 requireAccountWhenDepositSet 이 금지하는 상태와 같다.
     */
    private void applyDepositPatch(MatchPost post, PostUpdateRequest request) {
        if (Patchable.isClear(request.depositAmount())) {
            post.clearDeposit();
            return;
        }
        rejectAccountClear(request.bankName(), "bankName");
        rejectAccountClear(request.accountNumber(), "accountNumber");
        rejectAccountClear(request.accountHolder(), "accountHolder");
        post.updateDeposit(Patchable.valueOf(request.depositAmount()),
                Patchable.valueOf(request.bankName()),
                Patchable.valueOf(request.accountNumber()),
                Patchable.valueOf(request.accountHolder()));
    }

    private void rejectAccountClear(Patchable<String> field, String name) {
        if (Patchable.isClear(field)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                    new ErrorResponse.FieldError(name,
                            "계좌는 따로 지울 수 없습니다. 입금액(depositAmount)을 null 로 보내면 함께 지워집니다.")));
        }
    }

    /**
     * PATCH 의 좌표 처리 (계약서 §5-1). 세 갈래다.
     * <ul>
     *   <li>둘 다 안 보냄 → 기존 좌표 유지 (아무것도 안 한다)</li>
     *   <li>둘 다 null → 좌표를 지운다. 장소를 직접 입력으로 바꾸는 흐름이다</li>
     *   <li>둘 다 값 → 범위를 보고 교체</li>
     * </ul>
     * 그 밖의 조합(한쪽만 보냄, 한쪽만 null)은 전부 400 이다. 반쪽만 반영하면 이전 값과
     * 짝지어져 <b>조용히 틀린 지점</b>이 만들어진다.
     */
    private void applyCoordinatePatch(MatchPost post, Patchable<Double> latitude,
                                      Patchable<Double> longitude) {
        boolean latitudeSent = Patchable.isPresent(latitude);
        boolean longitudeSent = Patchable.isPresent(longitude);
        if (!latitudeSent && !longitudeSent) {
            return;
        }
        if (latitudeSent != longitudeSent) {
            throw coordinatePairViolation(latitudeSent ? "longitude" : "latitude");
        }
        Double latitudeValue = Patchable.valueOf(latitude);
        Double longitudeValue = Patchable.valueOf(longitude);
        if ((latitudeValue == null) != (longitudeValue == null)) {
            throw coordinatePairViolation(latitudeValue == null ? "latitude" : "longitude");
        }
        if (latitudeValue != null) {
            requireCoordinateRange(latitudeValue, longitudeValue);
        }
        post.updateCoordinates(latitudeValue, longitudeValue);
    }

    /** PATCH 는 어노테이션 검증을 못 타므로 범위를 여기서 본다 (계약서 §5-1). */
    private void requireCoordinateRange(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                    new ErrorResponse.FieldError("latitude", "위도는 -90~90 이어야 합니다.")));
        }
        if (longitude < -180 || longitude > 180) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                    new ErrorResponse.FieldError("longitude", "경도는 -180~180 이어야 합니다.")));
        }
    }

    private BusinessException coordinatePairViolation(String field) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                new ErrorResponse.FieldError(field, "위도와 경도는 함께 보내야 합니다.")));
    }

    /**
     * POST 의 좌표는 쌍으로만 받는다 (계약서 §5-1). 하나만 오면 400 이다.
     *
     * 계좌 검증(requireAccountWhenDepositSet)과 달리 <b>병합된 결과가 아니라 요청 본문</b>을
     * 본다. 계좌는 클라이언트가 값을 되읽을 수 없어서 금액만 보내는 PATCH 를 허용해야 하지만,
     * 좌표는 응답에 그대로 실려 나가므로 고칠 때 둘 다 보낼 수 있다. 그리고 위도만 바꾸면
     * 이전 경도와 짝지어져 <b>엉뚱한 지점</b>을 가리키게 되는데, 그건 조용히 틀리는 종류의
     * 사고라 요청 단계에서 막는 편이 낫다. 계약서가 PATCH 를 따로 규정하지 않아 이렇게 정했다.
     */
    private void requireCoordinatePair(Double latitude, Double longitude) {
        if ((latitude == null) == (longitude == null)) {
            return;
        }
        String missing = latitude == null ? "latitude" : "longitude";
        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                List.of(new ErrorResponse.FieldError(missing,
                        "위도와 경도는 함께 보내야 합니다.")));
    }

    /** depositAmount 를 받으려면 보낼 곳이 있어야 한다 (계약서 §5). */
    private void requireAccountWhenDepositSet(Integer depositAmount, String bankName,
                                              String accountNumber, String accountHolder) {
        if (depositAmount == null) {
            return;
        }
        List<ErrorResponse.FieldError> missing = new ArrayList<>();
        if (bankName == null || bankName.isBlank()) {
            missing.add(new ErrorResponse.FieldError("bankName", "입금액을 입력하면 은행명은 필수입니다."));
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            missing.add(new ErrorResponse.FieldError("accountNumber", "입금액을 입력하면 계좌번호는 필수입니다."));
        }
        if (accountHolder == null || accountHolder.isBlank()) {
            missing.add(new ErrorResponse.FieldError("accountHolder", "입금액을 입력하면 예금주는 필수입니다."));
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, missing);
        }
    }

    private MatchPost findPost(Long postId) {
        return postRepository.findWithTeamAndOwnerById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private void requireAuthor(MatchPost post, User user) {
        if (!post.isWrittenBy(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private PostDetail toDetail(MatchPost post, User viewer) {
        long requestCount = requestCounts(List.of(post)).getOrDefault(post.getId(), 0L);
        // 상세의 team 은 TeamResponse 라 평점까지 들어간다 (계약서 §2, v1.2.0).
        ReviewStats teamReviewStats = reviewRepository.statsOf(post.getTeam().getId());
        if (viewer == null) {
            return PostDetail.of(post, requestCount, null, null, null, null, teamReviewStats);
        }

        Long viewerId = viewer.getId();
        Long viewerTeamId = teamRepository.findByOwnerId(viewerId).map(Team::getId).orElse(null);

        RequestStatus myRequestStatus = viewerTeamId == null ? null
                : requestRepository
                        .findFirstByPostIdAndApplicantTeamIdOrderByIdDesc(post.getId(), viewerTeamId)
                        .map(MatchRequest::getStatus)
                        .orElse(null);

        MatchRequest accepted = requestRepository.findAcceptedByPostId(post.getId()).orElse(null);
        return PostDetail.of(post, requestCount, viewerId, myRequestStatus,
                contactFor(post, accepted, viewerId, viewerTeamId),
                paymentFor(post, accepted, viewerId, viewerTeamId), teamReviewStats);
    }

    /** 매칭이 성사된 두 팀만 서로의 연락처를 본다. */
    private ContactInfo contactFor(MatchPost post, MatchRequest accepted, Long viewerId,
                                   Long viewerTeamId) {
        if (accepted == null) {
            return null;
        }
        if (post.isWrittenBy(viewerId)) {
            return ContactInfo.from(accepted.getApplicantTeam().getOwner());
        }
        if (accepted.getApplicantTeam().getId().equals(viewerTeamId)) {
            return ContactInfo.from(post.getTeam().getOwner());
        }
        return null;
    }

    /**
     * 계좌를 볼 수 있는 사람은 둘뿐이다 (계약서 §5).
     * 수락된 신청 팀은 어디로 입금할지 알아야 하고, 작성자 본인은 자기가 등록한 계좌를
     * 확인·수정해야 한다. 비로그인·제3자·수락 전 신청 팀은 전부 null.
     */
    private PaymentInfo paymentFor(MatchPost post, MatchRequest accepted, Long viewerId,
                                   Long viewerTeamId) {
        if (!post.hasDepositAccount()) {
            return null;
        }
        boolean acceptedApplicant = accepted != null && viewerTeamId != null
                && accepted.getApplicantTeam().getId().equals(viewerTeamId);
        if (!post.isWrittenBy(viewerId) && !acceptedApplicant) {
            return null;
        }
        return new PaymentInfo(post.getDepositAmount(), post.getBankName(),
                post.getAccountNumber(), post.getAccountHolder(),
                accepted != null && accepted.isDepositPaid());
    }

    private PageResponse<PostSummary> toSummaryPage(Page<MatchPost> posts) {
        Map<Long, Long> counts = requestCounts(posts.getContent());
        return PageResponse.of(posts,
                post -> PostSummary.of(post, counts.getOrDefault(post.getId(), 0L)));
    }

    /** 페이지에 실린 글들의 신청 수를 쿼리 한 번으로 모아온다. */
    private Map<Long, Long> requestCounts(List<MatchPost> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = posts.stream().map(MatchPost::getId).toList();
        return requestRepository.countActiveByPostIds(ids).stream()
                .collect(Collectors.toMap(PostRequestCount::postId, PostRequestCount::count));
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
