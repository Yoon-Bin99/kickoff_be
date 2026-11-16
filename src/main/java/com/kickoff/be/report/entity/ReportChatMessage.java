package com.kickoff.be.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지 신고에 함께 저장하는 대화 사본 (계약서 §7-2·§10, v1.35.0).
 *
 * <b>사본이 증거다.</b> 운영자가 볼 때마다 채팅 서버에 다시 묻지 않는다 — 그 사이에
 * 대화가 지워질 수도(보관 기간·탈퇴), 채팅 서버가 꺼져 있을 수도 있다. 신고 시점에
 * 신고자에게 보이던 그대로를 여기 박아 둔다.
 *
 * 신고자나 신고된 쪽이 탈퇴하면 <b>이 사본은 지운다</b>(§7-2 — 대화는 탈퇴와 함께
 * 지운다는 §3-4 의 약속). 신고 건 자체는 보관 기간까지 남는다.
 */
@Entity
@Getter
@Table(name = "report_chat_messages",
        indexes = @Index(name = "idx_report_chat_messages_report", columnList = "report_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    /** 채팅 서버의 메시지 id. 신고된 메시지를 가리키는 값과 맞춰 본다. */
    @Column(name = "message_id", nullable = false)
    private Long messageId;

    /** 이 사본 중 신고된 그 메시지인지 — 운영자 화면이 가운데를 짚을 수 있게. */
    @Column(nullable = false)
    private boolean reported;

    /** 보낸 사람. 탈퇴·시스템 메시지면 null. */
    @Column(name = "sender_user_id")
    private Long senderUserId;

    /** 보낸 팀. 사본에는 id 만 남기고 운영자 화면이 지금 이름을 붙인다. */
    @Column(name = "sender_team_id")
    private Long senderTeamId;

    /** {@code TEXT} 또는 {@code SYSTEM} (§6-1). 시스템 안내 줄도 문맥의 일부다. */
    @Column(name = "message_type", length = 10)
    private String messageType;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    @Builder
    private ReportChatMessage(Long reportId, Long messageId, boolean reported, Long senderUserId,
                              Long senderTeamId, String messageType, String content,
                              OffsetDateTime sentAt) {
        this.reportId = reportId;
        this.messageId = messageId;
        this.reported = reported;
        this.senderUserId = senderUserId;
        this.senderTeamId = senderTeamId;
        this.messageType = messageType;
        this.content = content;
        this.sentAt = sentAt;
    }

    /** 신고자·신고된 쪽이 탈퇴하면 보낸 사람 식별값을 비운다 (계약서 §7-2). */
    public void forgetSender() {
        this.senderUserId = null;
    }
}
