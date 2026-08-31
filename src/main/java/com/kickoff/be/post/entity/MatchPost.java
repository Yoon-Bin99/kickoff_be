package com.kickoff.be.post.entity;

import com.kickoff.be.common.BaseTimeEntity;
import com.kickoff.be.team.entity.SkillLevel;
import com.kickoff.be.team.entity.Team;
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
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "match_posts", indexes = {
        @Index(name = "idx_match_posts_match_at", columnList = "matchAt"),
        @Index(name = "idx_match_posts_status", columnList = "status")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 글을 쓴 팀. 작성자 판별은 이 팀의 소유자로 한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private OffsetDateTime matchAt;

    @Column(nullable = false, length = 100)
    private String location;

    @Column(nullable = false, length = 50)
    private String region;

    /** null 이면 상대 실력 무관. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SkillLevel preferredSkillLevel;

    /** 글 작성 팀이 이미 낸 총 구장 대여료. 정보 표시용. */
    private Integer rentalFee;

    /** 매칭 확정 시 상대 팀이 작성 팀에게 보낼 금액. null 이면 "협의". */
    private Integer depositAmount;

    /*
     * 입금받을 계좌. 평문 필드로 나가는 응답은 없다. 수락된 신청 팀과 작성자 본인에게만
     * PaymentInfo 로 내려간다. 새 응답 DTO 를 만들 때 이 세 필드를 무심코 넣지 말 것.
     */
    @Column(length = 20)
    private String bankName;

    @Column(length = 30)
    private String accountNumber;

    @Column(length = 20)
    private String accountHolder;

    /**
     * 지도에 찍을 좌표 (계약서 §5-1). 둘 다 nullable 이고 <b>언제나 쌍으로만</b> 존재한다 —
     * 한쪽만 있으면 엉뚱한 지점을 가리키게 되므로 서비스에서 쌍 검증을 한다.
     * 장소를 직접 입력한 글은 좌표가 없고, FE 는 그때 지도 영역 자체를 숨긴다.
     */
    private Double latitude;

    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(nullable = false)
    private int viewCount;

    @Builder
    private MatchPost(Team team, String title, String content, OffsetDateTime matchAt, String location,
                      String region, SkillLevel preferredSkillLevel,
                      Integer rentalFee, Integer depositAmount, String bankName,
                      String accountNumber, String accountHolder,
                      Double latitude, Double longitude) {
        this.team = team;
        this.title = title;
        this.content = content;
        this.matchAt = matchAt;
        this.location = location;
        this.region = region;
        this.preferredSkillLevel = preferredSkillLevel;
        this.rentalFee = rentalFee;
        this.depositAmount = depositAmount;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = PostStatus.OPEN;
        this.viewCount = 0;
    }

    /**
     * PATCH — null 인 항목은 그대로 둔다.
     * preferredSkillLevel/rentalFee/depositAmount 는 원래 null 이 "무관/미정"이라
     * 이 방식으로는 다시 null 로 되돌릴 수 없다.
     */
    /**
     * 지울 수 없는 필드만 여기서 바꾼다 — 여기서는 {@code null} 이 <b>"안 건드림"</b>이다.
     *
     * 지울 수 있는 필드(실력수준·대여료·입금액·계좌)는 같은 null 이 <b>"지우기"</b>라서 규칙이
     * 정반대다. 한 메서드에 섞어 두면 다음 사람이 반드시 헷갈리므로 아래 전용 메서드로 뗐다.
     */
    public void update(String title, String content, OffsetDateTime matchAt, String location,
                       String region, PostStatus status) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (matchAt != null) {
            this.matchAt = matchAt;
        }
        if (location != null) {
            this.location = location;
        }
        if (region != null) {
            this.region = region;
        }
        if (status != null) {
            this.status = status;
        }
    }

    /** null 이면 "실력 무관"으로 되돌린다 (계약서 §5, v1.5.1). */
    public void updatePreferredSkillLevel(SkillLevel preferredSkillLevel) {
        this.preferredSkillLevel = preferredSkillLevel;
    }

    /** null 이면 "대여료 미정"으로 되돌린다 (계약서 §5, v1.5.1). */
    public void updateRentalFee(Integer rentalFee) {
        this.rentalFee = rentalFee;
    }

    /**
     * 입금액과 계좌를 함께 바꾼다. 여기서는 {@code null} 이 "안 건드림"이다 — 지우기는
     * {@link #clearDeposit()} 이 맡는다. 둘을 한 메서드에 두면 "계좌만 지우기"가 표현
     * 가능해져 버리는데, 그건 계약이 금지하는 상태다.
     */
    public void updateDeposit(Integer depositAmount, String bankName,
                              String accountNumber, String accountHolder) {
        if (depositAmount != null) {
            this.depositAmount = depositAmount;
        }
        if (bankName != null) {
            this.bankName = bankName;
        }
        if (accountNumber != null) {
            this.accountNumber = accountNumber;
        }
        if (accountHolder != null) {
            this.accountHolder = accountHolder;
        }
    }

    /**
     * 입금액과 계좌 3필드를 <b>한꺼번에</b> 지운다 (계약서 §5, v1.5.1).
     *
     * 금액만 지우고 계좌를 남기면 "무료 경기인데 입금 안내가 붙은" 글이 된다. 좌표 버그와
     * 같은 종류 — 글에 쓰인 내용과 실제가 어긋나고, 그게 사용자에게 그대로 보인다.
     */
    public void clearDeposit() {
        this.depositAmount = null;
        this.bankName = null;
        this.accountNumber = null;
        this.accountHolder = null;
    }

    /**
     * 좌표는 update 에 섞지 않고 따로 받는다. 다른 필드는 "null 이면 안 건드림"이지만 좌표는
     * <b>null 이 지우기</b>라서, 같은 메서드에 두면 규칙이 둘로 갈려 헷갈린다.
     * 요청에 좌표가 실제로 담겨 왔을 때만 호출된다 (계약서 §5-1).
     */
    public void updateCoordinates(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /** 입금 안내를 만들 수 있는 상태인지 — 계좌가 다 채워져 있어야 한다. */
    public boolean hasDepositAccount() {
        return bankName != null && accountNumber != null && accountHolder != null;
    }

    public void markMatched() {
        this.status = PostStatus.MATCHED;
    }

    public void close() {
        this.status = PostStatus.CLOSED;
    }

    /**
     * 매칭이 취소되어 다시 모집 상태로 돌아간다 (계약서 §6-2, v1.20.0).
     *
     * 지난 경기인지는 여기서 보지 않는다 — 취소 자체가 경기 전에만 되므로 호출 시점에
     * 이미 미래다. 그리고 목록·신청 가능 여부는 status 가 아니라
     * {@link #acceptsRequests()} 가 matchAt 과 함께 판정한다.
     */
    public void reopen() {
        this.status = PostStatus.OPEN;
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

    public boolean isOpen() {
        return this.status == PostStatus.OPEN;
    }

    /** 경기 시각이 이미 지났는지. 지난 경기는 status 와 무관하게 매칭 대상이 아니다. */
    public boolean hasPassed() {
        return matchAt.isBefore(OffsetDateTime.now());
    }

    /** 지금 신청을 받을 수 있는 글인지 (계약서 §5 지난 경기 규칙). */
    public boolean acceptsRequests() {
        return isOpen() && !hasPassed();
    }

    /** 작성 팀의 소유자인지. */
    public boolean isWrittenBy(Long userId) {
        return team.isOwnedBy(userId);
    }
}
