package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.SquadSlot;

/**
 * 응답의 스쿼드 항목 (계약서 §4-4, v1.28.0).
 *
 * {@code slot} 은 선발에만 있고 교체는 null 이다 — 계약서가 "bench 는 slot 없음"으로
 * 정했다. null 필드도 키는 남는다 (§2, {@code default-property-inclusion: always}).
 *
 * {@code name} 은 팀원이 명단에 있으면 <b>현재 이름</b>, 지워졌으면 저장 시점 스냅샷이고
 * 그때 {@code memberId} 는 null 로 내려간다 ({@link SquadSlot#displayName()} 참고).
 */
public record SquadItem(
        Integer slot,
        Long memberId,
        String name
) {

    public static SquadItem starter(SquadSlot slot) {
        return new SquadItem(slot.getSlotOrder(), slot.getMemberId(), slot.displayName());
    }

    public static SquadItem benched(SquadSlot slot) {
        return new SquadItem(null, slot.getMemberId(), slot.displayName());
    }
}
