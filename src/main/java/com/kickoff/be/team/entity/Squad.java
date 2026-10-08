package com.kickoff.be.team.entity;

import com.kickoff.be.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 스쿼드 — 경기 전에 짜 두는 선발·교체 보드 (계약서 §4-4, v1.28.0).
 *
 * 한 팀에 여러 개 저장한다("10/12 vs 마포" 식). 최대 30개는 서비스가 막는다 — 개수 제한은
 * 같은 팀의 다른 행을 세어야 알 수 있어서 엔티티가 혼자 판단할 수 없다.
 *
 * <b>이미지는 서버가 만들지 않는다.</b> 공유는 FE 가 경기장 그림을 캡처해 OS 공유창으로
 * 보내는 방식이라(§4-4), 서버는 "누가 어느 자리에 있다"만 안다. 자리 좌표도 모른다.
 *
 * <b>PUT 은 전체 교체다.</b> 보드 하나를 통째로 저장하는 UI 라 부분 수정이 의미가 없다 —
 * 그래서 {@link #replaceItems} 가 항목을 다 지우고 다시 만든다. {@code orphanRemoval} 이
 * 그 삭제를 맡는다.
 */
@Entity
@Getter
@Table(name = "squads")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Squad extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 30)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Formation formation;

    /**
     * 선발과 교체를 함께 담는다 ({@link SquadSlot#isBench()} 가 가른다).
     *
     * {@code @OrderBy} 로 정렬을 DB 에 맡긴다. 안 걸면 순서가 조회마다 달라질 수 있는데,
     * 자리 번호가 곧 경기장 위치라 <b>순서가 틀리면 포메이션이 틀린다.</b>
     */
    @OneToMany(mappedBy = "squad", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("bench asc, slotOrder asc")
    private List<SquadSlot> items = new ArrayList<>();

    private Squad(Team team, String title, Formation formation) {
        this.team = team;
        this.title = title;
        this.formation = formation;
    }

    public static Squad of(Team team, String title, Formation formation) {
        return new Squad(team, title, formation);
    }

    /** 선발 자리 — 자리 번호 순. */
    public List<SquadSlot> starters() {
        return items.stream()
                .filter(item -> !item.isBench())
                .sorted(Comparator.comparingInt(SquadSlot::getSlotOrder))
                .toList();
    }

    /** 교체 — 저장한 순서 그대로. */
    public List<SquadSlot> bench() {
        return items.stream()
                .filter(SquadSlot::isBench)
                .sorted(Comparator.comparingInt(SquadSlot::getSlotOrder))
                .toList();
    }

    /**
     * 제목·포메이션을 바꾼다 (PUT, 계약서 §4-4).
     *
     * 포메이션이 바뀌면 자리 개수도 바뀌므로 항목을 남겨 둘 수 없다. 그래서 부분 교체를
     * 두지 않았다 — 섞이면 "자리 개수 = 총원"이 깨진 채로 저장될 길이 생긴다.
     */
    public void retitle(String title, Formation formation) {
        this.title = title;
        this.formation = formation;
    }

    /**
     * 항목을 비운다. {@code orphanRemoval} 이 삭제를 맡는다.
     *
     * <b>{@link #addItems} 와 사이에 flush 가 필요하다.</b> 하이버네이트는 한 flush 안에서
     * <b>삽입을 삭제보다 먼저</b> 보내는데, 교체 저장은 지운 자리와 같은 번호를 다시
     * 넣으므로 {@code uk_squad_slots_position} 유니크를 건드려 500 이 난다. 실제로 그렇게
     * 터졌고, PUT 세 경우가 한꺼번에 실패해서 원인이 금방 보였다 — 제약이 없었다면
     * 중복된 자리가 조용히 저장됐을 것이다.
     *
     * 리스트 객체를 갈아 끼우지 않고 비우는 이유는, 새 리스트로 바꾸면 하이버네이트가
     * 컬렉션 변경을 추적하지 못해 옛 항목이 그대로 남기 때문이다.
     */
    public void clearItems() {
        this.items.clear();
    }

    /**
     * {@code newItems} 는 이 스쿼드를 가리키도록 만들어져 있어야 한다
     * ({@code SquadSlot.starter/benched} 의 첫 인자).
     */
    public void addItems(List<SquadSlot> newItems) {
        this.items.addAll(newItems);
    }
}
