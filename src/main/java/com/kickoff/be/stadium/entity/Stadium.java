package com.kickoff.be.stadium.entity;

import com.kickoff.be.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구장 (계약서 §8-1, v1.27.0). 조회 전용이다 — 예약은 전부 외부 사이트 몫이다.
 *
 * <b>사용자를 외부로 내보내는 것이 이 기능의 목적</b>이라 {@code reservationUrl} 이 없는
 * 행은 의미가 없다. 그래서 not null 이다.
 *
 * 접수 상태와 이용 기간은 <b>공공 API 가 준 원문을 그대로</b> 담는다("접수중"·"예약마감"
 * 같은 한국어 문자열). 열거형으로 바꾸지 않는 이유는, 공공 API 가 언제 새 상태 문구를
 * 내보낼지 우리가 정할 수 없어서다 — 모르는 값이 오면 동기화가 통째로 실패하는 것보다
 * 화면에 그대로 보여주는 편이 낫다. 수동 시드에는 이 값이 없다(null).
 */
@Entity
@Getter
@Table(name = "stadiums")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stadium extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 공공 API 의 서비스 id (SVCID). 동기화가 <b>같은 구장을 다시 넣지 않고 갱신</b>하는
     * 기준이다. 수동 시드는 null 이라, 유니크 제약이 null 을 서로 다른 값으로 보는 성질에
     * 기대 여러 행이 공존한다.
     */
    @Column(name = "external_id", length = 100, unique = true)
    private String externalId;

    @Column(nullable = false, length = 200)
    private String name;

    /** "서울 성북구" 형식. 모집글(§5)의 지역 문자열과 같은 모양이라 화면이 같은 필터를 쓴다. */
    @Column(nullable = false, length = 100)
    private String region;

    @Column(length = 300)
    private String address;

    @Column(name = "reservation_url", nullable = false, length = 500)
    private String reservationUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StadiumSource source;

    /** 공공 API 원문(예: 접수중·예약마감). MANUAL 은 null. */
    @Column(name = "accept_status", length = 50)
    private String acceptStatus;

    /** 공공 API 원문(예: "2026-09-01 ~ 2026-12-31"). MANUAL 은 null. */
    @Column(name = "use_period", length = 100)
    private String usePeriod;

    @Builder
    private Stadium(String externalId, String name, String region, String address,
                    String reservationUrl, StadiumSource source, String acceptStatus,
                    String usePeriod) {
        this.externalId = externalId;
        this.name = name;
        this.region = region;
        this.address = address;
        this.reservationUrl = reservationUrl;
        this.source = source;
        this.acceptStatus = acceptStatus;
        this.usePeriod = usePeriod;
    }

    /**
     * 동기화가 가져온 값으로 갱신한다.
     *
     * <b>지우고 다시 넣지 않는 이유</b>: 삭제·삽입으로 하면 id 가 매번 바뀌어, 나중에 이
     * 테이블을 참조하는 것이 생겼을 때(즐겨찾기 같은) 조용히 끊긴다. 지금은 참조가 없지만
     * 그때 가서 바꾸려면 동기화를 다시 설계해야 한다.
     */
    public void syncFrom(String name, String region, String address, String reservationUrl,
                         String acceptStatus, String usePeriod) {
        this.name = name;
        this.region = region;
        this.address = address;
        this.reservationUrl = reservationUrl;
        this.acceptStatus = acceptStatus;
        this.usePeriod = usePeriod;
    }
}
