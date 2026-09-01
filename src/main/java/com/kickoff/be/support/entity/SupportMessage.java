package com.kickoff.be.support.entity;

import com.kickoff.be.common.BaseTimeEntity;
import com.kickoff.be.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객센터 문의 메시지 (계약서 §7-1, v1.22.0).
 *
 * 매칭 채팅(§6-1)과 구조는 닮았지만 <b>별개 도메인</b>이다 — 방의 주인이 팀이 아니라
 * 사용자이고, 만료도 나가기도 없다. 한 테이블로 합치면 "요청에 매인 메시지"와 "사용자에
 * 매인 메시지"가 섞여 두 규칙이 한 곳에서 갈린다.
 *
 * {@code user} 는 <b>방의 주인</b>이지 보낸 사람이 아니다. 운영자가 답장해도 그 방의
 * 주인은 그대로 사용자다 — 누가 보냈는지는 {@link #sender} 가 말한다.
 */
@Entity
@Getter
@Table(name = "support_messages", indexes = {
        @Index(name = "idx_support_messages_user_id", columnList = "user_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 방의 주인. 보낸 사람이 아니다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportSender sender;

    @Column(nullable = false, length = 500)
    private String content;

    @Builder
    private SupportMessage(User user, SupportSender sender, String content) {
        this.user = user;
        this.sender = sender;
        this.content = content;
    }

    public boolean isFrom(SupportSender other) {
        return this.sender == other;
    }
}
