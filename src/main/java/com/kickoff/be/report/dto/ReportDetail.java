package com.kickoff.be.report.dto;

import com.kickoff.be.common.ModerationResponse;
import com.kickoff.be.report.entity.RightsHolder;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 신고 상세 (계약서 §7-2, v1.35.0). 운영자만 본다.
 *
 * <b>대상은 신고 시점의 사본이다.</b> 신고 뒤 글이 고쳐지거나 지워져도 무엇을 두고
 * 신고했는지가 남아야 한다 — 지금 값을 다시 읽으면 운영자가 보는 것과 신고자가 본 것이
 * 달라진다.
 *
 * @param reporter   신고자. 탈퇴했으면 null (기록의 보관 — 식별값을 비운다)
 * @param reported   신고된 쪽. 탈퇴했으면 null
 * @param moderation 대상에 지금 걸려 있는 조치. 없으면 null
 * @param resolution 조치 내용·시각·메모. 아직 처리 전이면 null
 */
public record ReportDetail(
        ReportSummary summary,
        RightsHolder rightsHolder,
        String detail,
        Party reporter,
        Party reported,
        Target target,
        ModerationResponse moderation,
        Resolution resolution
) {

    public record Party(Long userId, String nickname, Long teamId, String teamName) {
    }

    /**
     * 신고 시점의 대상 사본.
     *
     * @param messages 채팅 메시지 신고일 때의 앞뒤 대화. 그 밖에는 빈 목록
     */
    public record Target(String title, String body, Integer rating, Long reportedMessageId,
                         List<Message> messages) {
    }

    public record Message(Long messageId, boolean reported, Long senderUserId, Long senderTeamId,
                          String type, String content, OffsetDateTime sentAt) {
    }

    public record Resolution(String action, String note, OffsetDateTime resolvedAt) {
    }
}
