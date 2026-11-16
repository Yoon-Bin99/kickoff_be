package com.kickoff.be.post.service;

import com.kickoff.be.chatlink.service.ChatOutboxRecorder;
import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ContactInfo;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.common.Patchable;
import com.kickoff.be.common.LikeEscape;
import com.kickoff.be.common.PaymentInfo;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.entity.RequestStatus;
import com.kickoff.be.matchrequest.repository.MatchRequestRepository;
import com.kickoff.be.matchrequest.repository.PostRequestCount;
import com.kickoff.be.post.dto.PostCreateRequest;
import com.kickoff.be.post.dto.PostDetail;
import com.kickoff.be.post.dto.PostSummary;
import com.kickoff.be.post.dto.PostUpdateRequest;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.post.repository.MatchPostRepository;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.team.entity.SkillLevel;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.dto.RecordSummary;
import com.kickoff.be.team.repository.TeamRecordRepository;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.team.service.TeamAuthz;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final MatchPostRepository postRepository;
    private final TeamRepository teamRepository;
    private final TeamRecordRepository teamRecordRepository;
    private final TeamAuthz teamAuthz;
    private final MatchRequestRepository requestRepository;
    private final ReviewRepository reviewRepository;
    private final ChatOutboxRecorder chatOutboxRecorder;
    private final MatchContactProperties matchContactProperties;

    /**
     * 정렬은 matchAt 오름차순 고정 — 가까운 경기가 먼저 (계약서 §5).
     * 지난 경기는 status 필터와 무관하게 빠진다.
     */
    @Transactional(readOnly = true)
    public PageResponse<PostSummary> search(String region, SkillLevel skillLevel,
                                            PostStatus status, String keyword,
                                            String dates, String times, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                clampSize(size),
                Sort.by(Sort.Direction.ASC, "matchAt"));
        // 와일드카드를 리터럴로 만든다 (계약서 §5). 팀 검색이 v1.14.0 에서 먼저 갖춘
        // 처리인데 여기만 빠져 있었다 — 그동안 keyword=% 하나로 모든 글이, _ 하나로
        // 한 글자 이상인 글이 전부 나왔다. 에러가 아니라 결과가 넘칠 뿐이라 안 드러났다.
        // 쿼리 쪽 escape '!' 절과 짝이라, 한쪽만 있으면 더 나빠진다.
        Page<MatchPost> posts = postRepository.search(OffsetDateTime.now(),
                LikeEscape.escape(blankToNull(region)), skillLevel, status,
                LikeEscape.escape(blankToNull(keyword)),
                DateFilter.parse(dates), TimeSlotFilter.parseToStoredHours(times), pageable);
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
        requirePaymentShareConfirmed(request.paymentShareConfirmed(), request.bankName(),
                request.accountNumber(), request.accountHolder());
        requireCoordinatePair(request.latitude(), request.longitude());
        MatchPost post = postRepository.save(MatchPost.builder()
                .team(team)
                .title(request.title())
                .content(request.content())
                .matchAt(request.matchAt())
                .location(request.location())
                .locationAddress(request.locationAddress())
                .region(request.region())
                .preferredSkillLevel(request.preferredSkillLevel())
                .rentalFee(request.rentalFee())
                .depositAmount(request.depositAmount())
                .bankName(request.bankName())
                .accountNumber(request.accountNumber())
                .accountHolder(request.accountHolder())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build());
        if (hasAnyAccountValue(request.bankName(), request.accountNumber(),
                request.accountHolder())) {
            post.confirmPaymentShare(OffsetDateTime.now());
        }
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
        // 계좌 공개 확인은 <b>요청 본문</b>으로 본다 (계약서 §5, v1.34.0) — 계좌를 건드리지
        // 않는 PATCH 에는 필요 없다. 병합 결과로 보면 계좌가 이미 있는 글의 제목만 고치는
        // 요청까지 확인을 요구하게 된다. 아래 requireAccountWhenDepositSet 이 병합 결과를
        // 보는 것과 기준이 다른데, 묻는 것이 다르기 때문이다: 저쪽은 "지금 글의 상태가
        // 온전한가", 이쪽은 "지금 이 값을 적겠다고 확인했는가"다.
        requirePaymentShareConfirmed(request.paymentShareConfirmed(),
                Patchable.valueOf(request.bankName()),
                Patchable.valueOf(request.accountNumber()),
                Patchable.valueOf(request.accountHolder()));
        applyCoordinatePatch(post, request.latitude(), request.longitude());
        applyClearableFields(post, request);
        OffsetDateTime matchAtBefore = post.getMatchAt();
        post.update(Patchable.valueOf(request.title()), Patchable.valueOf(request.content()),
                Patchable.valueOf(request.matchAt()), Patchable.valueOf(request.location()),
                Patchable.valueOf(request.region()), Patchable.valueOf(request.status()));
        notifyChatIfMatchAtChanged(post, matchAtBefore);
        // 병합된 결과를 기준으로 본다. 계좌는 작성자도 다시 읽을 수 없어서,
        // PATCH 본문만 보고 판단하면 금액만 고치는 정상 요청이 막혀버린다.
        requireAccountWhenDepositSet(post.getDepositAmount(), post.getBankName(),
                post.getAccountNumber(), post.getAccountHolder());
        if (hasAnyAccountValue(Patchable.valueOf(request.bankName()),
                Patchable.valueOf(request.accountNumber()),
                Patchable.valueOf(request.accountHolder()))) {
            post.confirmPaymentShare(OffsetDateTime.now());
        }
        return toDetail(post, user);
    }

    /**
     * 계좌를 적었으면 공개 확인이 있어야 한다 (계약서 §5, v1.34.0).
     *
     * <b>어노테이션으로는 표현할 수 없는 규칙이다</b> — "다른 필드에 값이 있을 때만 필수"라서
     * 요청 하나를 통째로 봐야 한다.
     *
     * 세 칸 중 <b>하나라도</b> 값이 있으면 묻는다. 은행명만 고치는 요청에도 묻는 이유:
     * 그 결과로 상대에게 보이는 계좌 한 벌이 달라진다.
     */
    private void requirePaymentShareConfirmed(Boolean confirmed, String bankName,
                                              String accountNumber, String accountHolder) {
        if (!hasAnyAccountValue(bankName, accountNumber, accountHolder)) {
            return;
        }
        if (!Boolean.TRUE.equals(confirmed)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                    new ErrorResponse.FieldError("paymentShareConfirmed",
                            "입금 계좌가 매칭이 수락된 상대 팀 감독에게 보이는 것을 확인해 주세요.")));
        }
    }

    private boolean hasAnyAccountValue(String bankName, String accountNumber,
                                       String accountHolder) {
        return bankName != null || accountNumber != null || accountHolder != null;
    }

    /**
     * 경기 일시가 바뀌었고 그 글에 수락된 신청이 있으면 채팅 서버에 알린다
     * (계약서 §10 ROOM_INVALIDATED).
     *
     * <b>"실제로 바뀐" 경우만이다.</b> matchAt 을 같은 값으로 보내는 PATCH 가 흔한데
     * (FE 가 폼 전체를 보낸다), 그때마다 이벤트를 쌓으면 바뀐 것 없는 ROOM_STATE 가
     * 오간다. 반대로 바뀌었는데 안 알리면 채팅방이 옛 경기 시각을 보여 주고, 전송 가능
     * 여부(matchAt 이 지났는지)까지 어긋난다.
     *
     * 비교는 {@code equals} 다 — 순간이 같고 오프셋만 다른 값도 "바뀜"으로 친다. 한 번
     * 더 보내는 쪽은 멱등이라 손해가 없지만, 안 보내는 쪽은 틀린 화면이 남는다.
     *
     * 수락된 신청이 없으면 방이 없으므로 조회도 하지 않는다 — 바뀌지 않은 PATCH 에
     * 쿼리가 하나 붙지 않게 순서를 이렇게 뒀다. 수락된 신청은 글당 최대 하나다(§6).
     */
    private void notifyChatIfMatchAtChanged(MatchPost post, OffsetDateTime before) {
        if (Objects.equals(before, post.getMatchAt())) {
            return;
        }
        requestRepository.findAcceptedByPostId(post.getId())
                .ifPresent(accepted -> chatOutboxRecorder.roomInvalidated(List.of(accepted.getId())));
    }

    /**
     * 모집글 삭제 (계약서 §5, v1.31.3).
     *
     * <b>신청이 하나라도 있으면 지울 수 없다.</b> 상태를 가리지 않는다 — 거절·철회된
     * 신청도 상대 팀의 이력이고, 리뷰·전적이 그 신청을 참조한다. 글을 지우면 그 기록들이
     * 남의 화면에서 함께 사라진다.
     *
     * 그 전까지는 <b>막지도 지우지도 않았다.</b> 외래 키에 걸려 500 이 났고, 작성자는
     * "삭제가 안 된다"만 보고 이유를 알 수 없었으며 신청이 붙은 글은 영영 못 지웠다.
     * 이제 409 로 뜻을 밝히고 대안(마감)을 안내한다.
     *
     * 검사 순서는 계약이 정했다 — 없는 글 404 → 작성자 아님 403 → 신청 있음 409.
     * 권한을 상태보다 먼저 보는 건 제3자에게 "그 글에 신청이 있는가"를 알려주지 않기
     * 위해서다(§6-1·§7 과 같은 규칙).
     */
    @Transactional
    public void delete(Long postId, User user) {
        MatchPost post = findPost(postId);
        requireAuthor(post, user);
        if (requestRepository.existsByPostId(postId)) {
            throw new BusinessException(ErrorCode.POST_HAS_REQUESTS);
        }
        try {
            postRepository.delete(post);
            // 삭제를 여기서 밀어낸다. 트랜잭션 끝에 나가면 외래 키 위반이 이 메서드 밖에서
            // 터져 500 이 되고, 아래 catch 는 영영 돌지 않는다.
            postRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 위 검사와 삭제 사이에 신청이 들어온 경우다. 좁지만 실재하는 창이고(신청은
            // 다른 트랜잭션에서 들어온다), 그때도 사용자가 보는 답은 같아야 한다 — 같은
            // 요청이 어떤 날은 409, 어떤 날은 500 이면 FE 가 안내를 고를 수 없다.
            throw new BusinessException(ErrorCode.POST_HAS_REQUESTS);
        }
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
        // 주소는 지울 수 있다 (계약서 §5, v1.29.0) — 장소 검색으로 고른 글을 직접 입력으로
        // 되돌릴 길이 있어야 한다. 그래서 location 과 달리 여기(지우기 가능 쪽)에 있다.
        if (Patchable.isPresent(request.locationAddress())) {
            post.updateLocationAddress(Patchable.valueOf(request.locationAddress()));
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
        // 상세에 실린 팀도 전적 요약을 그대로 내보낸다. 0 으로 두면 기록이 있는 팀이
        // 목록에서는 7승인데 글 상세에서는 0승으로 보인다 (계약서 §4-1).
        RecordSummary teamRecordSummary = teamRecordRepository.summaryOf(post.getTeam().getId());
        if (viewer == null) {
            return PostDetail.of(post, requestCount, null, null, null, null, teamReviewStats,
                    null, teamRecordSummary);
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
                paymentFor(post, accepted, viewerId, viewerTeamId), teamReviewStats,
                teamAuthz.roleOf(post.getTeam(), viewerId), teamRecordSummary);
    }

    /**
     * 매칭이 성사된 두 팀만 서로의 연락처를 본다.
     *
     * <b>경기 뒤 {@code contact-visible-days}(기본 14)일까지만이다</b>(계약서 §5, v1.34.1).
     * 연락처는 "매칭 성사 시 상대 팀 감독에게 제공"에 동의해서 넘어간 값이고, 그 동의가
     * 말한 보유·이용 기간이 이 값이다 — 무기한 보이면 동의한 범위를 넘는다.
     */
    private ContactInfo contactFor(MatchPost post, MatchRequest accepted, Long viewerId,
                                   Long viewerTeamId) {
        if (accepted == null) {
            return null;
        }
        if (!matchContactProperties.isVisibleAt(post.getMatchAt(), OffsetDateTime.now())) {
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
            // 저장된 계좌를 못 읽은 경우도 여기로 떨어진다 — hasDepositAccount() 가 세 칸을
            // 모두 보고, 복호화 실패는 null 로 온다. 아래에서 그 둘을 가려 로그를 남긴다.
            logIfAccountUnreadable(post);
            return null;
        }
        boolean acceptedApplicant = accepted != null && viewerTeamId != null
                && accepted.getApplicantTeam().getId().equals(viewerTeamId);
        if (!post.isWrittenBy(viewerId) && !acceptedApplicant) {
            return null;
        }
        // 표시 기간은 <b>상대 팀에게만</b> 적용된다 (계약서 §5, v1.34.1). 작성자 본인이
        // 자기 글에서 보는 계좌는 기간과 무관하다 — 자기가 등록한 값을 확인·수정하는
        // 길이고, 그걸 막으면 경기 뒤에는 계좌를 고칠 수 없게 된다.
        if (!post.isWrittenBy(viewerId)
                && !matchContactProperties.isVisibleAt(post.getMatchAt(), OffsetDateTime.now())) {
            return null;
        }
        return new PaymentInfo(post.getDepositAmount(), post.getBankName(),
                post.getAccountNumber(), post.getAccountHolder(),
                accepted != null && accepted.isDepositPaid());
    }

    /**
     * 저장된 계좌를 읽지 못한 글을 알린다 (계약서 §5).
     *
     * <b>500 으로 터뜨리지 않는다.</b> 계좌를 못 읽는 것은 그 글의 입금 안내 하나가 빠지는
     * 일인데, 예외로 올리면 <b>글 상세 자체가 열리지 않는다</b> — 제목·장소·시각처럼 계좌와
     * 무관한 정보까지 함께 막힌다. 그래서 payment 만 내려주지 않고 글은 그대로 보여 준다.
     *
     * 일어나는 경우는 사실상 하나다: 운영 키가 바뀌었거나 잘못된 키로 떴다. 그래서
     * <b>사람이 반드시 알아야 한다</b> — 조용히 넘기면 입금 안내가 사라진 것을 사용자
     * 문의로 알게 된다.
     *
     * <b>식별값은 postId 뿐이다.</b> 암호문·키·그 글의 다른 값은 남기지 않는다 — 계좌가
     * 로그로 새면 암호화한 이유 자체가 없어진다.
     *
     * {@code depositAmount} 가 있는데 칸이 비어 있을 때만 의심한다. 금액이 없으면 애초에
     * 계좌를 받지 않는 글이라({@code requireAccountWhenDepositSet}) 정상 상태다.
     */
    private void logIfAccountUnreadable(MatchPost post) {
        if (post.getDepositAmount() == null) {
            return;
        }
        if (post.getBankName() == null || post.getAccountNumber() == null
                || post.getAccountHolder() == null) {
            log.error("모집글의 입금 계좌를 읽지 못해 payment 를 내리지 않았다 — postId={}. "
                    + "ACCOUNT_ENC_KEY 가 저장 당시와 같은지 확인할 것", post.getId());
        }
    }

    private PageResponse<PostSummary> toSummaryPage(Page<MatchPost> posts) {
        Map<Long, Long> counts = requestCounts(posts.getContent());
        // 카드마다 평점 조회를 날리면 페이지 크기만큼 쿼리가 는다 (계약서 §2, v1.10.0)
        Map<Long, ReviewStats> teamStats = reviewRepository.statsMapOf(
                posts.getContent().stream().map(post -> post.getTeam().getId()).toList());
        return PageResponse.of(posts,
                post -> PostSummary.of(post, counts.getOrDefault(post.getId(), 0L),
                        teamStats.getOrDefault(post.getTeam().getId(), ReviewStats.EMPTY)));
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
