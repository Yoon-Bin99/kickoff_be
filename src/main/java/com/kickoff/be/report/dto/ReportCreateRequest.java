package com.kickoff.be.report.dto;

import com.kickoff.be.report.entity.ReportReason;
import com.kickoff.be.report.entity.ReportTargetType;
import com.kickoff.be.report.entity.RightsHolder;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고 요청 (계약서 §7-2 — {@code POST /api/reports}, v1.35.0).
 *
 * 어노테이션으로 거를 수 있는 것만 여기서 본다. <b>사유와 대상의 짝</b>, 채팅 신고의
 * {@code requestId}·전달 동의, 자기 것 신고 같은 규칙은 요청 하나를 통째로 봐야 해서
 * 서비스가 판정한다.
 */
public record ReportCreateRequest(

        @NotNull(message = "신고 대상 종류가 필요합니다.")
        ReportTargetType targetType,

        @NotNull(message = "신고 대상이 필요합니다.")
        Long targetId,

        /** {@code CHAT_MESSAGE} 일 때만 필수 — 메시지는 방 안에서만 식별된다. */
        Long requestId,

        @NotNull(message = "신고 사유가 필요합니다.")
        ReportReason reason,

        /** {@code DEFAMATION} 일 때만 필수 (계약서 §7-2). */
        RightsHolder rightsHolder,

        @Size(max = 500, message = "상세 내용은 500자를 넘을 수 없습니다.")
        String detail,

        /**
         * "신고한 메시지와 그 앞뒤 대화가 운영자에게 전달된다"는 동의 (계약서 §7-2).
         *
         * {@code CHAT_MESSAGE} 일 때 {@code true} 가 아니면 400 이다. 1:1 대화를 운영자가
         * 보게 되는 일이라, 신고 버튼을 눌렀다는 사실만으로 갈음하지 않는다.
         */
        Boolean chatDisclosureAgreed
) {
}
