package com.kickoff.be.report.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.report.dto.ReportCreateRequest;
import com.kickoff.be.report.dto.ReportCreatedResponse;
import com.kickoff.be.report.service.ReportService;
import com.kickoff.be.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고 (계약서 §7-2, v1.35.0).
 *
 * 넣는 것 하나뿐이다 — <b>신고 내역을 조회하는 사용자 API 는 없다</b>. 처리 결과는
 * 고객센터 메시지와 푸시로 간다.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportCreatedResponse> create(@LoginUser User user,
                                                        @Valid @RequestBody
                                                        ReportCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.create(user, request));
    }
}
