package com.kickoff.be.stadium.dto;

import com.kickoff.be.stadium.entity.Stadium;
import com.kickoff.be.stadium.entity.StadiumSource;

/**
 * 구장 목록 항목 (계약서 §8-1, v1.27.0).
 *
 * 필드명은 계약서 그대로다. 특히 식별자가 {@code id} 가 아니라 <b>{@code stadiumId}</b> 다.
 *
 * {@code acceptStatus}·{@code usePeriod} 는 SEOUL_PUBLIC 에만 있고 MANUAL 은 null 로 나간다.
 * 엔티티가 이미 그 상태라 여기서 걸러 내지 않는다 — 두 곳에서 같은 규칙을 지키면
 * 한쪽만 바뀌는 날이 온다.
 */
public record StadiumSummary(
        Long stadiumId,
        String name,
        String region,
        String address,
        String reservationUrl,
        StadiumSource source,
        String acceptStatus,
        String usePeriod
) {

    public static StadiumSummary of(Stadium stadium) {
        return new StadiumSummary(
                stadium.getId(),
                stadium.getName(),
                stadium.getRegion(),
                stadium.getAddress(),
                stadium.getReservationUrl(),
                stadium.getSource(),
                stadium.getAcceptStatus(),
                stadium.getUsePeriod()
        );
    }
}
