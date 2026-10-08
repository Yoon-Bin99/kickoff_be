package com.kickoff.be.team.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 스쿼드의 한 자리 (계약서 §4-4, v1.28.0). 선발 한 칸이거나 교체 한 명이다.
 *
 * <b>선발과 교체를 한 표에 담는다.</b> 구조가 같고({@code memberId} 또는 {@code name} 또는
 * 빈 자리) 중복 검사도 둘을 통틀어 하기 때문이다 — 표를 나누면 "같은 사람이 선발+교체에
 * 두 번" 검사가 두 표를 가로질러야 한다. {@code bench} 가 어느 쪽인지 가른다.
 *
 * <b>{@code member} 와 {@code name} 둘 다 들고 있는 이유</b>는 스냅샷이다. 팀원이 명단에서
 * 지워져도 스쿼드는 과거 기록으로 남아야 한다(§4-4). 저장할 때 그 시점 이름을 함께 적어
 * 두고, 팀원이 사라지면 DB 가 {@code member_id} 를 null 로 만들어(ON DELETE SET NULL)
 * 이 이름이 응답에 나간다. 명단에 그대로 있으면 <b>현재</b> 이름을 쓴다 — 닉네임이 바뀌면
 * 스쿼드에도 따라가야 하므로 스냅샷을 믿지 않는다.
 *
 * {@code BaseTimeEntity} 를 상속하지 않는다. PUT 이 통째로 갈아치우는 값이라 항목마다
 * 생성·수정 시각을 들고 있을 이유가 없다 — 시각은 스쿼드가 갖는다.
 */
@Entity
@Getter
@Table(name = "squad_slots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SquadSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "squad_id", nullable = false)
    private Squad squad;

    /** true 면 교체, false 면 선발. */
    @Column(nullable = false)
    private boolean bench;

    /**
     * 선발이면 자리 번호(0 = GK, 1..N-1 은 줄 순서), 교체면 나열 순서(0부터).
     *
     * 선발·교체가 한 축을 쓰므로 {@code (squad_id, bench, slot_order)} 유니크 하나로
     * "같은 자리 번호가 두 번"을 DB 가 막는다.
     */
    @Column(name = "slot_order", nullable = false)
    private int slotOrder;

    /**
     * 이 팀 팀원 (§4-1). 명단 밖 사람(게스트)이면 null.
     *
     * <b>팀원이 지워지면 DB 가 이 값을 null 로 만든다</b> (ON DELETE SET NULL). 그래서
     * 명단 삭제가 스쿼드를 깨거나 함께 지우지 않는다 — 외래키를 cascade 로 걸면 과거
     * 스쿼드가 통째로 사라진다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private TeamMember member;

    /** 게스트 이름, 그리고 팀원이 지워졌을 때 쓸 스냅샷. 빈 자리면 null. */
    @Column(length = 20)
    private String name;

    private SquadSlot(Squad squad, boolean bench, int slotOrder, TeamMember member, String name) {
        this.squad = squad;
        this.bench = bench;
        this.slotOrder = slotOrder;
        this.member = member;
        this.name = name;
    }

    /**
     * @param member 이 팀 팀원이거나 null
     * @param name   게스트 이름. member 가 있으면 <b>그 시점 이름을 적어 둔다</b>(스냅샷).
     */
    public static SquadSlot starter(Squad squad, int slotOrder, TeamMember member, String name) {
        return new SquadSlot(squad, false, slotOrder, member, name);
    }

    public static SquadSlot benched(Squad squad, int order, TeamMember member, String name) {
        return new SquadSlot(squad, true, order, member, name);
    }

    public Long getMemberId() {
        return member == null ? null : member.getId();
    }

    /**
     * 응답에 나갈 이름 (계약서 §4-4).
     *
     * 팀원이 남아 있으면 <b>현재 명단 이름</b>이다 — 닉네임 변경이 명단에 반영되는데
     * (§4-3) 스쿼드만 옛 이름을 보여 주면 같은 사람이 둘로 보인다. 팀원이 지워졌으면
     * 저장 시점 스냅샷을 쓴다. 빈 자리는 null 이다.
     */
    public String displayName() {
        return member == null ? name : member.getName();
    }
}
