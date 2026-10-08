package com.kickoff.be.team.dto;

import jakarta.validation.constraints.Size;

/**
 * 스쿼드의 한 항목 — 선발 자리 또는 교체 (계약서 §4-4, v1.28.0).
 *
 * 세 가지 상태가 모두 정상이다.
 * <ul>
 *   <li>{@code memberId} 만 — 명단에 있는 팀원</li>
 *   <li>{@code name} 만 — 게스트 등 명단 밖 사람</li>
 *   <li>둘 다 null — <b>빈 자리</b>. 짜다 만 스쿼드도 저장된다</li>
 * </ul>
 * 둘 다 오면 {@code memberId} 가 이긴다 (계약서 §4-4). name 을 무시하는 게 아니라
 * 스냅샷으로 덮어쓴다 — 저장 시점의 팀원 이름이 들어간다.
 *
 * <b>{@code slot} 은 받지만 쓰지 않는다.</b> 선발 자리 번호는 배열 순서로 정한다
 * (0번째 = GK). 번호를 본문에서 받아 그대로 믿으면 중복·누락·범위 밖 번호를 전부
 * 검증해야 하고, 그 검증이 "slots 는 정확히 N개"와 어긋날 길이 생긴다. FE 가 보내 주는
 * 값이라 받아만 두고, 서버의 진실은 순서다 — 계약서도 "1..N-1은 포메이션 줄 순서"로
 * 순서를 기준으로 쓴다.
 */
public record SquadItemRequest(

        Integer slot,

        Long memberId,

        @Size(min = 1, max = 20, message = "이름은 1~20자여야 합니다.")
        String name
) {

    /** 빈 자리인지 — 둘 다 없으면 빈 자리다. */
    public boolean isEmptySlot() {
        return memberId == null && (name == null || name.isBlank());
    }
}
