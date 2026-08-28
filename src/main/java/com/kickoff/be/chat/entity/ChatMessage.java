package com.kickoff.be.chat.entity;

import com.kickoff.be.common.BaseTimeEntity;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.team.entity.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매칭 채팅 메시지 (계약서 §6-1, v1.12.0 / type 은 v1.13.0).
 *
 * <b>방 리소스가 따로 없다.</b> 수락된 신청 하나가 곧 방이고, 방은 수락 시점부터 암묵적으로
 * 존재한다. 그래서 이 엔티티가 가리키는 것도 방이 아니라 매칭이다.
 *
 * 보낸 이를 사용자가 아니라 <b>팀</b>으로 적는다. 계약서가 말풍선을 팀 기준으로 가르고,
 * 주장이 바뀌더라도 "어느 팀이 한 말인가"는 그대로 남아야 하기 때문이다.
 *
 * v1.13.0 부터 SYSTEM 줄이 섞인다. SYSTEM 은 서버가 만든 안내라 {@code senderTeam} 이
 * 없다 — 그래서 이 연관은 더 이상 optional=false 가 아니다.
 *
 * 수정·삭제가 없다 (계약서 §9). 그래서 상태 변경 메서드도 없다.
 */
@Entity
@Getter
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    /** 나가기 안내 문구 (계약서 §6-1). 계약서에 박힌 문자열이라 바꾸면 화면이 달라진다. */
    public static final String LEAVE_NOTICE = "상대 팀이 채팅방을 나갔습니다";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private MatchRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ChatMessageType type;

    /** SYSTEM 이면 null 이다 (계약서 §6-1). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_team_id")
    private Team senderTeam;

    @Column(nullable = false, length = 500)
    private String content;

    private ChatMessage(MatchRequest request, ChatMessageType type, Team senderTeam,
                        String content) {
        this.request = request;
        this.type = type;
        this.senderTeam = senderTeam;
        this.content = content;
    }

    /** 사람이 보낸 말풍선. 보낸 팀이 반드시 있다. */
    public static ChatMessage text(MatchRequest request, Team senderTeam, String content) {
        return new ChatMessage(request, ChatMessageType.TEXT, senderTeam, content);
    }

    /**
     * 나가기 안내 (계약서 §6-1, v1.13.0). 남은 쪽이 "답 없는 방"에서 기다리지 않게 한다.
     *
     * 나간 팀을 적지 않는 건 의도다. 이 줄을 보는 사람은 언제나 남은 쪽 하나뿐이고,
     * 팀을 적으면 FE 가 말풍선 주인으로 오해한다.
     */
    public static ChatMessage leaveNotice(MatchRequest request) {
        return new ChatMessage(request, ChatMessageType.SYSTEM, null, LEAVE_NOTICE);
    }

    public Long getRequestId() {
        return request.getId();
    }

    /** SYSTEM 이면 null — 계약서가 약속한 값이다. */
    public Long getSenderTeamId() {
        return senderTeam == null ? null : senderTeam.getId();
    }
}
