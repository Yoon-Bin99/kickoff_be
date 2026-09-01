package com.kickoff.be.support.entity;

import com.kickoff.be.common.BaseTimeEntity;
import com.kickoff.be.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문의방 (계약서 §7-1, v1.22.0). 사용자당 하나이고 닫히지 않는다.
 *
 * <b>메시지만으로는 표현할 수 없는 상태가 하나 있어서 테이블을 둔다: 운영자 모드.</b>
 * 에스컬레이트는 눌렀는데 운영자가 아직 답을 안 한 상태가 정상 경로인데, 그때도 AI 는
 * 침묵해야 한다. "OPERATOR 메시지가 있으면 운영자 모드"로 유추하면 바로 그 구간에서
 * AI 가 계속 답한다 — 사용자는 운영자를 부른 줄 알고 기다리는데 AI 가 대신 답하는 셈이다.
 *
 * 방 자체는 메시지가 없어도 만들어질 수 있다(첫 조회). 운영자 문의함 목록에는 메시지가
 * 있는 방만 나온다 — 그건 목록 쿼리가 판단한다.
 */
@Entity
@Getter
@Table(name = "support_rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * 운영자 연결 상태. 한 번 켜지면 v1 에서는 되돌리지 않는다 (계약서 §7-1).
     * 켜진 뒤로 AI 는 답하지 않는다.
     */
    @Column(nullable = false)
    private boolean operatorMode;

    @Builder
    private SupportRoom(User user) {
        this.user = user;
        this.operatorMode = false;
    }

    /** 되돌리기는 없다 — 계약이 v1 에서 제외했다. 두 번 불러도 같은 상태다. */
    public void escalate() {
        this.operatorMode = true;
    }
}
