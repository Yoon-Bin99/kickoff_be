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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 팀이 한 방을 나간 상태 (계약서 §6-1, v1.13.0).
 *
 * 팀 단위다 — 채팅 참여자가 주장뿐이라도 주장은 바뀔 수 있고, "누가 나갔는가"는 사람이
 * 아니라 팀의 사실이어야 다음 주장이 지난 대화를 새로 보게 되는 일이 없다.
 *
 * <b>두 값이 따로 필요하다.</b> 계약서의 나가기는 성질이 다른 두 효과를 한꺼번에 낸다.
 * <ul>
 *   <li>{@code watermark} — 이 id 이하는 <b>영구히</b> 안 보인다. 복귀해도 돌아오지 않는다
 *       ("지난 대화를 다시 볼 수 없게 됩니다"가 FE 확인 문구에 들어가는 이유다)</li>
 *   <li>{@code hidden} — 방 목록에서 빠지고 푸시가 멈춘 상태. 전송에 성공하면 <b>풀린다</b></li>
 * </ul>
 * 하나로 합치면 복귀가 표현되지 않는다. hidden 만 두면 복귀할 때 지난 대화가 되살아나고,
 * watermark 만 두면 복귀 여부를 알 수 없어 목록에 영영 안 나오거나 항상 나온다.
 */
@Entity
@Getter
@Table(name = "chat_leaves", uniqueConstraints = @UniqueConstraint(
        name = "uk_chat_leaves_request_team", columnNames = {"request_id", "team_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatLeave extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private MatchRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /** 이 id 이하는 안 보인다. 메시지가 없을 때 나가면 0 이다 (모든 id 가 0 보다 크다). */
    @Column(name = "watermark_message_id", nullable = false)
    private Long watermark;

    /** 지금 나가 있는가. 전송하면 false 가 되고 watermark 는 그대로 남는다. */
    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    private ChatLeave(MatchRequest request, Team team) {
        this.request = request;
        this.team = team;
        this.watermark = 0L;
        this.hidden = false;
    }

    public static ChatLeave of(MatchRequest request, Team team) {
        return new ChatLeave(request, team);
    }

    /**
     * 나간다. watermark 는 <b>나가기 안내 줄까지 포함</b>해서 잘라야 한다 — 안내는 남은
     * 쪽에게 하는 말이라, 나간 쪽이 재입장했을 때 자기가 나갔다는 안내를 다시 보면 안 된다.
     */
    public void leaveAt(Long lastMessageId) {
        this.watermark = lastMessageId == null ? 0L : lastMessageId;
        this.hidden = true;
    }

    /** 전송 성공 시 복귀 (계약서 §6-1). watermark 는 건드리지 않는다 — 지난 대화는 영구다. */
    public void rejoin() {
        this.hidden = false;
    }

    public Long getRequestId() {
        return request.getId();
    }

    public Long getTeamId() {
        return team.getId();
    }
}
