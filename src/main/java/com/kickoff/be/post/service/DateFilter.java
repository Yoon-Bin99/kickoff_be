package com.kickoff.be.post.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * dates 쿼리 해석 (계약서 §5, v1.18.0).
 *
 * 날짜를 <b>KST 하루의 시각 범위</b>로 바꿔 넘긴다. 2026-09-01 은
 * {@code [2026-08-31T15:00Z, 2026-09-01T15:00Z)} 이다.
 *
 * SQL 에서 날짜 함수를 전혀 쓰지 않는 게 요점이다. matchAt 을 KST 로 바꿔 날짜를 뽑으려면
 * 타임존 변환이 필요한데, 그 함수들이 H2 와 PostgreSQL 에서 갈린다 — 이번 작업에서
 * supervisor 가 지목한 위험이기도 하다. 범위 비교로 바꾸면 남는 건 부등호뿐이라 두 DB 가
 * 다르게 동작할 여지가 없다.
 */
public final class DateFilter {

    /** 계약서 §5 — 최대 14개. */
    private static final int MAX_DATES = 14;

    /** KST 는 고정 +09:00 이다 (서머타임 없음). */
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    /** 하루의 시작과 끝 (끝은 다음 날 00:00, 제외 경계). */
    public record Range(OffsetDateTime start, OffsetDateTime end) {
    }

    private DateFilter() {
    }

    public static List<Range> parse(String dates) {
        if (dates == null || dates.isBlank()) {
            return List.of();
        }
        Set<LocalDate> parsed = new LinkedHashSet<>();
        for (String token : dates.split(",", -1)) {
            parsed.add(parseOne(token.trim()));
        }
        // 중복을 걷어낸 뒤에 센다. "같은 날짜를 여러 번 보냈다"는 사용자 실수지 한도
        // 초과가 아니다.
        if (parsed.size() > MAX_DATES) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "날짜는 최대 %d개까지 선택할 수 있습니다.".formatted(MAX_DATES));
        }
        List<Range> ranges = new ArrayList<>();
        for (LocalDate date : parsed) {
            ranges.add(new Range(date.atStartOfDay(KST).toOffsetDateTime(),
                    date.plusDays(1).atStartOfDay(KST).toOffsetDateTime()));
        }
        return ranges;
    }

    private static LocalDate parseOne(String token) {
        try {
            return LocalDate.parse(token);
        } catch (DateTimeParseException e) {
            // 하나라도 틀리면 전체를 거절한다 (계약서 §5). 틀린 것만 버리면 사용자가
            // 고른 날짜와 다른 결과가 조용히 나온다.
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "날짜 형식이 올바르지 않습니다 (YYYY-MM-DD): " + token);
        }
    }
}
