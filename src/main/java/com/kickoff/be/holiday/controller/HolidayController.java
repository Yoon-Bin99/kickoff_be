package com.kickoff.be.holiday.controller;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.holiday.dto.HolidayResponse;
import com.kickoff.be.holiday.service.HolidayService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공휴일 (계약서 §8-2). 인증 불필요 — 달력은 로그인 전에도 보인다.
 *
 * 서버가 카카오를 대행하는 이유는 장소 검색(§5-1)과 같다 — <b>어드민 키를 앱 번들에 넣지
 * 않기 위해서</b>다. 더해서 캐시를 한 곳에 두면 사용자 수만큼 외부 호출이 늘지 않는다.
 */
@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
public class HolidayController {

    /**
     * 받아 줄 연도 범위.
     *
     * 범위를 두는 이유는 캐시다. 연도가 캐시 키라서 아무 값이나 받으면
     * {@code ?year=1} 을 수만 번 부르는 것으로 맵을 불릴 수 있다 — 호출마다 외부 API 를
     * 찌르기도 한다. 달력이 다루는 범위보다 넉넉하면 충분하다.
     */
    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2200;

    private final HolidayService holidayService;

    @GetMapping
    public ResponseEntity<HolidayResponse> of(@RequestParam int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                    new ErrorResponse.FieldError("year",
                            "연도는 %d~%d 사이여야 합니다.".formatted(MIN_YEAR, MAX_YEAR))));
        }
        return ResponseEntity.ok(holidayService.of(year));
    }
}
