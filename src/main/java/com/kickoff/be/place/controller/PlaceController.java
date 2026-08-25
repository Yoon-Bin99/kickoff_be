package com.kickoff.be.place.controller;

import com.kickoff.be.place.dto.PlaceSearchResponse;
import com.kickoff.be.place.service.PlaceSearchService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소 검색 프록시 (계약서 §5-1). 인증 필요 — 로그인한 사용자만 우리 검색 쿼터를 쓴다.
 */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    @GetMapping("/search")
    public ResponseEntity<PlaceSearchResponse> search(
            @RequestParam @NotBlank(message = "검색어는 필수입니다.")
            @Size(min = 1, max = 100, message = "검색어는 1~100자여야 합니다.") String query,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(placeSearchService.search(query, size));
    }
}
