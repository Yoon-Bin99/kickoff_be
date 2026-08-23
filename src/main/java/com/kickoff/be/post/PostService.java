package com.kickoff.be.post;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.matchrequest.MatchRequestRepository;
import com.kickoff.be.matchrequest.PostRequestCount;
import com.kickoff.be.post.dto.PostCreateRequest;
import com.kickoff.be.post.dto.PostDetail;
import com.kickoff.be.post.dto.PostSummary;
import com.kickoff.be.post.dto.PostUpdateRequest;
import com.kickoff.be.team.SkillLevel;
import com.kickoff.be.team.Team;
import com.kickoff.be.team.TeamRepository;
import com.kickoff.be.user.User;
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

    /** 정렬은 matchAt 오름차순 고정 — 가까운 경기가 먼저 (계약서 §5). */
    @Transactional(readOnly = true)
    public PageResponse<PostSummary> search(String region, FieldType fieldType, SkillLevel skillLevel,
                                            PostStatus status, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                clampSize(size),
                Sort.by(Sort.Direction.ASC, "matchAt"));
        Page<MatchPost> posts = postRepository.search(
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
        MatchPost post = postRepository.save(MatchPost.builder()
                .team(team)
                .title(request.title())
                .content(request.content())
                .matchAt(request.matchAt())
                .location(request.location())
                .region(request.region())
                .fieldType(request.fieldType())
                .preferredSkillLevel(request.preferredSkillLevel())
                .costPerTeam(request.costPerTeam())
                .build());
        return toDetail(post, user.getId());
    }

    /** 인증 불필요 — viewer 가 null 이면 isAuthor 는 false. 조회할 때마다 조회수가 오른다. */
    @Transactional
    public PostDetail get(Long postId, User viewer) {
        MatchPost post = findPost(postId);
        post.increaseViewCount();
        return toDetail(post, viewer == null ? null : viewer.getId());
    }

    @Transactional
    public PostDetail update(Long postId, User user, PostUpdateRequest request) {
        MatchPost post = findPost(postId);
        requireAuthor(post, user);
        post.update(request.title(), request.content(), request.matchAt(), request.location(),
                request.region(), request.fieldType(), request.preferredSkillLevel(),
                request.costPerTeam(), request.status());
        return toDetail(post, user.getId());
    }

    @Transactional
    public void delete(Long postId, User user) {
        MatchPost post = findPost(postId);
        requireAuthor(post, user);
        postRepository.delete(post);
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

    private PostDetail toDetail(MatchPost post, Long viewerId) {
        long requestCount = requestCounts(List.of(post)).getOrDefault(post.getId(), 0L);
        // myRequestStatus / contact 는 매칭 신청 기능이 붙는 3단계에서 채운다.
        return PostDetail.of(post, requestCount, viewerId, null, null);
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
