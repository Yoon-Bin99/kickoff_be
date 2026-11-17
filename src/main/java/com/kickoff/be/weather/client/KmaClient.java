package com.kickoff.be.weather.client;

import com.kickoff.be.weather.client.dto.MidLandForecast;
import com.kickoff.be.weather.client.dto.MidTemperature;
import com.kickoff.be.weather.client.dto.ShortTermForecast;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 기상청 예보 조회 경계 (계약서 §8-3). 장소 검색의 PlaceSearchClient, 푸시의 PushClient 와
 * 같은 이유로 인터페이스를 둔다 — 테스트가 실제 기상청을 때리지 않아야 한다.
 *
 * <b>예외를 밖으로 내지 않는다.</b> 받아 오기는 주기 작업이고, 한 곳이 실패했다고 다른
 * 지역까지 멈추면 안 된다 (계약서 §8-3). 실패는 빈 값이다.
 */
public interface KmaClient {

    /** 단기예보 한 격자. baseAt 은 발표 시각(KST). */
    Optional<ShortTermForecast> shortTerm(int nx, int ny, OffsetDateTime baseAt);

    /** 중기육상예보 한 구역. tmFc 는 발표 시각(06시 또는 18시). */
    Optional<MidLandForecast> midLand(String regId, OffsetDateTime tmFc);

    /** 중기기온 한 지점. */
    Optional<MidTemperature> midTemperature(String regId, OffsetDateTime tmFc);
}
