package com.kickoff.be.post;

import com.kickoff.be.auth.LoginUser;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.post.dto.PostCreateRequest;
import com.kickoff.be.post.dto.PostDetail;
import com.kickoff.be.post.dto.PostSummary;
import com.kickoff.be.post.dto.PostUpdateRequest;
import com.kickoff.be.team.SkillLevel;
import com.kickoff.be.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** 인증 불필요. 필터는 전부 optional, status 기본값 OPEN, 정렬은 matchAt ASC 고정. */
    @GetMapping
    public ResponseEntity<PageResponse<PostSummary>> search(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) FieldType fieldType,
            @RequestParam(required = false) SkillLevel skillLevel,
            @RequestParam(required = false, defaultValue = "OPEN") PostStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                postService.search(region, fieldType, skillLevel, status, keyword, page, size));
    }

    /** "/me" 는 "/{postId}" 보다 먼저 선언해야 경로가 겹치지 않는다. */
    @GetMapping("/me")
    public ResponseEntity<PageResponse<PostSummary>> getMine(
            @LoginUser User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postService.getMine(user, page, size));
    }

    @PostMapping
    public ResponseEntity<PostDetail> create(@LoginUser User user,
                                             @Valid @RequestBody PostCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.create(user, request));
    }

    /** 인증 불필요. 토큰이 있으면 isAuthor 를 채운다. */
    @GetMapping("/{postId}")
    public ResponseEntity<PostDetail> get(@PathVariable Long postId, @LoginUser User viewer) {
        return ResponseEntity.ok(postService.get(postId, viewer));
    }

    @PatchMapping("/{postId}")
    public ResponseEntity<PostDetail> update(@PathVariable Long postId, @LoginUser User user,
                                             @Valid @RequestBody PostUpdateRequest request) {
        return ResponseEntity.ok(postService.update(postId, user, request));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@PathVariable Long postId, @LoginUser User user) {
        postService.delete(postId, user);
        return ResponseEntity.noContent().build();
    }
}
