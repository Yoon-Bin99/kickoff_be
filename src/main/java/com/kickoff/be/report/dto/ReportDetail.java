package com.kickoff.be.report.dto;

import com.kickoff.be.common.ModerationResponse;
import com.kickoff.be.report.entity.ReportReason;
import com.kickoff.be.report.entity.ReportStatus;
import com.kickoff.be.report.entity.ReportTargetType;
import com.kickoff.be.report.entity.RightsHolder;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 신고 상세 (계약서 §7-2, v1.35.0 / 모양은 v1.37.2). 운영자만 본다.
 *
 * <b>{@code ReportSummary} 의 칸을 같은 층에 펼친다.</b> 묶음({@code summary})에 넣으면
 * FE 가 계약 문구("ReportSummary + …")대로 읽은 자리가 전부 빈다 — 실제로 그렇게
 * 어긋났고, v1.37.2 가 필드 이름까지 못 박았다.
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
        Long id,
        ReportTargetType targetType,
        Long targetId,
        ReportReason reason,
        ReportStatus status,
        long reporterCount,
        boolean autoHidden,
        OffsetDateTime hiddenUntil,
        OffsetDateTime createdAt,
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
     * 신고 시점의 대상 사본. <b>대상 종류마다 채워지는 칸이 다르다</b> (계약서 §7-2 표) —
     * 글은 title·content, 리뷰는 rating·content, 팀은 name·introduction, 채팅은
     * reportedMessageId·messages. 나머지는 null 이고 messages 는 빈 배열이다.
     */
    public record Target(String title, String content, Integer rating, String name,
                         String introduction, Long reportedMessageId, List<Message> messages) {

        public static Target ofPost(String title, String content) {
            return new Target(title, content, null, null, null, null, List.of());
        }

        public static Target ofReview(Integer rating, String content) {
            return new Target(null, content, rating, null, null, null, List.of());
        }

        public static Target ofTeam(String name, String introduction) {
            return new Target(null, null, null, name, introduction, null, List.of());
        }

        public static Target ofChat(Long reportedMessageId, List<Message> messages) {
            return new Target(null, null, null, null, null, reportedMessageId, messages);
        }
    }

    /**
     * §6-1 {@code ChatMessage} 에서 <b>다섯 칸만</b> (계약서 §7-2, v1.37.3).
     *
     * <b>보낸 사람의 userId 도 requestId 도 내보내지 않는다.</b> userId 는 사본에 들고
     * 있지만(탈퇴 때 비우려면 필요하다) 운영자 화면이 쓰는 것은 "어느 팀이 보냈는가"뿐이고,
     * requestId 는 이미 신고 건이 들고 있다 — 같은 값을 두 자리에 두면 어긋날 자리가 된다.
     */
    public record Message(Long id, String type, Long senderTeamId, String content,
                          OffsetDateTime createdAt) {
    }

    /** @param suspendDays 그 조치에 이용 제한이 붙었으면 날수, 아니면 null (v1.37.2) */
    public record Resolution(String action, Integer suspendDays, String note,
                             OffsetDateTime resolvedAt) {
    }
}
