package com.kickoff.be.legal.controller;

import com.kickoff.be.legal.service.LegalDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 약관·개인정보처리방침 페이지 (계약서 §3, v1.21.0).
 *
 * <b>API 표면이 아니라 사람이 브라우저로 읽는 문서다.</b> FE 가입 화면이 링크를 걸고,
 * 앱 심사에서도 이 주소를 요구한다. 그래서 {@code /api} 아래가 아니고 응답도 JSON 이 아니다 —
 * share-card.png 와 같은 성격이다.
 *
 * 인증이 없어야 한다. 가입하기 <b>전에</b> 읽는 문서라 토큰이 있을 수 없다.
 */
@RestController
@RequiredArgsConstructor
public class LegalDocumentController {

    private final LegalDocumentService legalDocumentService;

    @GetMapping(value = "/terms", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> terms() {
        return ResponseEntity.ok(legalDocumentService.terms());
    }

    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> privacy() {
        return ResponseEntity.ok(legalDocumentService.privacy());
    }
}
