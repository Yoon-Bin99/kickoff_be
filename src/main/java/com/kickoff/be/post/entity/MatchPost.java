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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FieldType fieldType;

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
                      String region, FieldType fieldType, SkillLevel preferredSkillLevel,
                      Integer rentalFee, Integer depositAmount, String bankName,
                      String accountNumber, String accountHolder,
                      Double latitude, Double longitude) {
        this.team = team;
        this.title = title;
        this.content = content;
        this.matchAt = matchAt;
        this.location = location;
        this.region = region;
        this.fieldType = fieldType;
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
    public void update(String title, String content, OffsetDateTime matchAt, String location,
                       String region, FieldType fieldType, SkillLevel preferredSkillLevel,
                       Integer rentalFee, Integer depositAmount, String bankName,
                       String accountNumber, String accountHolder, PostStatus status,
                       Double latitude, Double longitude) {
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
        if (fieldType != null) {
            this.fieldType = fieldType;
        }
        if (preferredSkillLevel != null) {
            this.preferredSkillLevel = preferredSkillLevel;
        }
        if (rentalFee != null) {
            this.rentalFee = rentalFee;
        }
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
        if (status != null) {
            this.status = status;
        }
        // 좌표는 쌍으로만 바뀐다. 하나만 들어오는 요청은 서비스에서 이미 걸러졌다.
        if (latitude != null && longitude != null) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
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
