package com.kickoff.be.post.entity;

import com.kickoff.be.common.BaseTimeEntity;
import com.kickoff.be.common.Moderation;
import com.kickoff.be.team.entity.SkillLevel;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.common.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
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

    /**
     * 경기장 주소 (계약서 §5, v1.29.0). <b>nullable</b> — 장소명을 직접 입력한 글과 이 필드가
     * 생기기 전의 글에는 없다.
     *
     * 장소 검색(§5-1)에서 고르면 FE 가 {@code roadAddress ?? address} 로 채워 보낸다. 서버는
     * <b>길이만 본다</b> — 사용자가 고친 값을 그대로 저장한다. 실제 주소와 맞는지 검증하지
     * 않는 이유는 그럴 방법이 없어서다(같은 장소가 지도마다 다른 이름·주소로 나온다).
     *
     * <b>v1.37.0 부터 검색 결과로 채우지 않는다</b> — 카카오 응답을 저장할 수 없어서다.
     * 사용자가 손으로 적은 글자만 들어온다.
     */
    @Column(name = "location_address", length = 100)
    private String locationAddress;

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
     *
     * 세 칸 모두 암호화해 저장한다 — DB 만으로는 읽을 수 없다. 의무 대상은 계좌번호지만,
     * 은행명·예금주도 함께 있으면 그대로 입금할 수 있는 한 벌이 되므로 묶는다.
     *
     * 엔티티 쪽 값은 언제나 평문이다. 그래서 계약서 §5 의 길이 제한(20/30/20자)과 검증은
     * 그대로 평문 기준으로 돈다 — 칸이 varchar(255) 인 것은 암호문이 길어서다(V26).
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 255)
    private String bankName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 255)
    private String accountNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 255)
    private String accountHolder;

    /**
     * 사용자가 검색 결과에서 고른 장소의 카카오 장소 ID (계약서 §5-1, v1.37.0).
     *
     * <b>좌표 대신 이것을 저장한다.</b> 카카오 로컬 검색의 응답(이름·주소·좌표)은 저장할
     * 수 없고, 장소 ID 와 사용자가 직접 친 글자만 저장할 수 있다(카카오 운영자 답변).
     * 지도는 보는 사람의 기기가 이 둘로 다시 조회해 그린다.
     *
     * {@code placeQuery} 와 <b>언제나 쌍으로만</b> 존재한다 — ID 만 있으면 기기가 그
     * 장소를 찾을 검색어가 없고, 검색어만 있으면 어느 줄이 그 장소인지 가릴 수 없다.
     */
    @Column(name = "kakao_place_id", length = 20)
    private String kakaoPlaceId;

    /** 그 장소를 찾을 때 사용자가 친 검색어 (계약서 §5-1). 사용자의 글자다. */
    @Column(name = "place_query", length = 100)
    private String placeQuery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(nullable = false)
    private int viewCount;

    /**
     * 계좌가 상대 팀 감독에게 보인다는 것을 작성자가 확인한 시각 (계약서 §5, v1.34.0).
     *
     * 응답에 싣지 않는다 — 화면이 쓸 값이 아니고, 제3자 제공을 알렸다는 서버의 기록이다.
     * 계좌를 적거나 고칠 때마다 새로 찍힌다(그때마다 확인을 다시 받으므로).
     */
    @Column(name = "payment_share_confirmed_at")
    private OffsetDateTime paymentShareConfirmedAt;

    /**
     * 운영 조치 (계약서 §7-2, v1.35.0). 걸린 적이 없으면 두 칸 모두 null 이다.
     *
     * 가려진 글은 목록에서 빠지고, 상세는 <b>작성자에게만</b> 열린다. 작성자의 수정·삭제도
     * 막는다 — 조치 중인 글을 고쳐서 빠져나갈 수 있으면 조치가 아니다.
     */
    @Embedded
    private Moderation moderation = Moderation.none();

    @Builder
    private MatchPost(Team team, String title, String content, OffsetDateTime matchAt, String location,
                      String locationAddress, String region, SkillLevel preferredSkillLevel,
                      Integer rentalFee, Integer depositAmount, String bankName,
                      String accountNumber, String accountHolder,
                      String kakaoPlaceId, String placeQuery) {
        this.team = team;
        this.title = title;
        this.content = content;
        this.matchAt = matchAt;
        this.location = location;
        this.locationAddress = locationAddress;
        this.region = region;
        this.preferredSkillLevel = preferredSkillLevel;
        this.rentalFee = rentalFee;
        this.depositAmount = depositAmount;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.kakaoPlaceId = kakaoPlaceId;
        this.placeQuery = placeQuery;
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
     * null 이면 주소를 지운다 (계약서 §5, v1.29.0).
     *
     * <b>{@code location} 과 규칙이 반대다.</b> 장소명은 필수라 null 이 "안 건드림"이지만,
     * 주소는 nullable 이라 null 이 "지우기"다 — 장소 검색으로 고른 글을 직접 입력으로
     * 되돌릴 길이 있어야 한다. 계약서 §5 의 PATCH 지우기 규칙("필드 없음 = 기존 유지,
     * 명시적 null = 지움")에서 주소는 지울 수 없는 목록에 없다.
     */
    public void updateLocationAddress(String locationAddress) {
        this.locationAddress = locationAddress;
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

    /** 계좌를 적거나 고칠 때 확인 시각을 남긴다 (계약서 §5, v1.34.0). */
    public void confirmPaymentShare(OffsetDateTime confirmedAt) {
        this.paymentShareConfirmedAt = confirmedAt;
    }

    /**
     * 입금액과 계좌 3필드를 <b>한꺼번에</b> 지운다 (계약서 §5, v1.5.1).
     *
     * 금액만 지우고 계좌를 남기면 "무료 경기인데 입금 안내가 붙은" 글이 된다. 좌표 버그와
     * 같은 종류 — 글에 쓰인 내용과 실제가 어긋나고, 그게 사용자에게 그대로 보인다.
     */
    /** 임시 가림 30일 (계약서 §7-2). */
    public void hide(OffsetDateTime now) {
        this.moderation = Moderation.hiddenFrom(now);
    }

    /** 운영자 삭제 (계약서 §7-2). 행은 남는다 — 그 자리에 조치 사실을 표시해야 한다. */
    public void removeByOperator() {
        this.moderation = Moderation.removed();
    }

    /** 조치를 푼다 (DISMISS·복원). */
    public void clearModeration() {
        this.moderation = Moderation.none();
    }

    /** 지금 조치가 살아 있는지. 기한이 지난 임시 가림은 풀린 것으로 본다. */
    public boolean isModeratedAt(OffsetDateTime now) {
        return moderation != null && moderation.isActiveAt(now);
    }

    public void clearDeposit() {
        this.depositAmount = null;
        this.bankName = null;
        this.accountNumber = null;
        this.accountHolder = null;
        // 계좌가 없어지면 확인도 가리킬 대상이 없다.
        this.paymentShareConfirmedAt = null;
    }

    /**
     * 장소 연결은 update 에 섞지 않고 따로 받는다. 다른 필드는 "null 이면 안 건드림"이지만
     * 이 둘은 <b>null 이 지우기</b>(연결 끊기)라서, 같은 메서드에 두면 규칙이 둘로 갈린다.
     * 요청에 둘 중 하나라도 담겨 왔을 때만 호출된다 (계약서 §5-1).
     */
    public void updatePlaceLink(String kakaoPlaceId, String placeQuery) {
        this.kakaoPlaceId = kakaoPlaceId;
        this.placeQuery = placeQuery;
    }

    /** 지도를 그릴 수 있는 글인지 — 둘이 함께 있어야 기기가 그 장소를 찾을 수 있다. */
    public boolean hasPlaceLink() {
        return kakaoPlaceId != null && placeQuery != null;
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
        // 운영 조치로 가려진 글은 새 신청을 받지 않는다 (계약서 §7-2 — 409 POST_NOT_OPEN).
        // 이미 수락된 매칭은 그대로다: 잡힌 경기를 조치가 깨면 아무 잘못 없는 상대 팀이
        // 경기를 잃는다.
        return isOpen() && !hasPassed() && !isModeratedAt(OffsetDateTime.now());
    }


    /** 작성 팀의 소유자인지. */
    public boolean isWrittenBy(Long userId) {
        return team.isOwnedBy(userId);
    }
}
