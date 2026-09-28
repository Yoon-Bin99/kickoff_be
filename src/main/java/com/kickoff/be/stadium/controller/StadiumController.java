package com.kickoff.be.stadium.controller;

import com.kickoff.be.common.PageResponse;
import com.kickoff.be.stadium.dto.StadiumSummary;
import com.kickoff.be.stadium.service.StadiumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stadiums")
@RequiredArgsConstructor
public class StadiumController {

    private final StadiumService stadiumService;

    /** 구장 목록 (계약서 §8-1, v1.27.0). 인증 불필요 — 가입 전에도 구장을 볼 수 있다. */
    @GetMapping
    public ResponseEntity<PageResponse<StadiumSummary>> search(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(stadiumService.search(region, keyword, page, size));
    }
}
