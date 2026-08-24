package com.kickoff.be.post.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ContactInfo;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.common.PageResponse;
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
        post.update(request.title(), request.content(), request.matchAt(), request.location(),
                request.region(), request.fieldType(), request.preferredSkillLevel(),
                request.rentalFee(), request.depositAmount(), request.bankName(),
                request.accountNumber(), request.accountHolder(), request.status());
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
