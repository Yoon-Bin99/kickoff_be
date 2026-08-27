package com.kickoff.be.chat.entity;

import com.kickoff.be.common.BaseTimeEntity;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.team.entity.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매칭 채팅 메시지 (계약서 §6-1, v1.12.0).
 *
 * <b>방 리소스가 따로 없다.</b> 수락된 신청 하나가 곧 방이고, 방은 수락 시점부터 암묵적으로
 * 존재한다. 그래서 이 엔티티가 가리키는 것도 방이 아니라 매칭이다.
 *
 * 보낸 이를 사용자가 아니라 <b>팀</b>으로 적는다. 계약서가 말풍선을 팀 기준으로 가르고,
 * 주장이 바뀌더라도 "어느 팀이 한 말인가"는 그대로 남아야 하기 때문이다.
 *
 * 수정·삭제가 없다 (계약서 §9). 그래서 상태 변경 메서드도 없다.
 */
@Entity
@Getter
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private MatchRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_team_id", nullable = false)
    private Team senderTeam;

    @Column(nullable = false, length = 500)
    private String content;

    @Builder
    private ChatMessage(MatchRequest request, Team senderTeam, String content) {
        this.request = request;
        this.senderTeam = senderTeam;
        this.content = content;
    }

    public Long getRequestId() {
        return request.getId();
    }

    public Long getSenderTeamId() {
        return senderTeam.getId();
    }
}
