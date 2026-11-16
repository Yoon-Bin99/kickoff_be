package com.kickoff.be.report.dto;

import com.kickoff.be.report.entity.ResolutionAction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 운영자 조치 (계약서 §7-2, v1.35.0).
 *
 * @param suspendDays 신고된 쪽 계정을 제한할 날수. {@code DISMISS} 와 함께 쓸 수 없다
 * @param note        내부 메모 — <b>통지에 싣지 않는다</b>
 */
public record ResolveRequest(

        @NotNull(message = "조치를 선택해 주세요.")
        ResolutionAction action,

        @Min(value = 1, message = "제한 기간은 1일 이상이어야 합니다.")
        @Max(value = 3650, message = "제한 기간은 3650일을 넘을 수 없습니다.")
        Integer suspendDays,

        @Size(max = 500, message = "메모는 500자를 넘을 수 없습니다.")
        String note
) {
}
