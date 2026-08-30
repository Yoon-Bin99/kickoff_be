package com.kickoff.be.post.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.post.entity.TimeSlot;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** times 쿼리 해석 (계약서 §5, v1.18.0). */
public final class TimeSlotFilter {

    private TimeSlotFilter() {
    }

    /**
     * {@code "DAWN,MORNING"} → 그 시간대들에 해당하는 <b>저장된 시 집합</b>.
     *
     * 여러 시간대는 OR 이므로 집합을 합치면 그대로 조건이 된다. 값이 없으면 빈 집합이고,
     * 쿼리는 그때 이 조건을 아예 적용하지 않는다.
     */
    public static Set<Integer> parseToStoredHours(String times) {
        if (times == null || times.isBlank()) {
            return Set.of();
        }
        Set<Integer> hours = new LinkedHashSet<>();
        for (String token : times.split(",", -1)) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                // "DAWN,,MORNING" 이나 끝의 쉼표. 조용히 건너뛰면 오타가 필터를 넓히는
                // 방향으로 작동해 사용자는 왜 결과가 많은지 모른다.
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "시간대 목록에 빈 값이 있습니다.");
            }
            hours.addAll(TimeSlot.from(trimmed).storedHours());
        }
        return hours;
    }

    /**
     * 쿼리에 넘길 안전한 형태. Hibernate 는 빈 IN 목록을 다루지 못하므로 자리를 채운다 —
     * 어차피 조건이 꺼져 있을 때만 쓰이는 값이다.
     */
    public static List<Integer> toQueryParam(Set<Integer> hours) {
        return hours.isEmpty() ? List.of(-1) : List.copyOf(hours);
    }
}
