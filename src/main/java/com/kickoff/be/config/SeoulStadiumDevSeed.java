package com.kickoff.be.config;

import com.kickoff.be.stadium.entity.Stadium;
import com.kickoff.be.stadium.entity.StadiumSource;
import com.kickoff.be.stadium.repository.StadiumRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서울 공공 구장 모양의 <b>개발용</b> 시드 (계약서 §8-1).
 *
 * <b>왜 필요한가.</b> 서울 열린데이터광장 인증키가 없어 SEOUL_PUBLIC 행이 하나도 없다.
 * 그래서 FE·디자인이 요일별로 갈린 같은 구장(평일 / 토·일·공휴일), 접수 상태 칩, 이용
 * 기간 줄, 출처 표기("서울시 공공서비스예약 제공")를 실데이터로 볼 수 없다. V20 의 수동
 * 시드는 전부 MANUAL 이라 그 네 가지가 모두 비어 있다.
 *
 * <b>Flyway 가 아니라 여기인 이유</b>는 운영 스키마에 가짜 데이터를 넣지 않기 위해서다.
 * V20 의 수동 시드는 전부 실제로 열리는 예약 페이지인데, 이 행들의 예약 주소는
 * <b>존재하지 않는 rsv_svc_id 라 열면 서울시 예약 사이트에서 오류가 난다.</b> 운영에
 * 들어가면 "앱이 고장 났다"로 보인다.
 *
 * <b>{@code DataInitializer} 와 달리 dev 프로파일까지 요구한다.</b> 그쪽은
 * {@code kickoff.seed-data} 플래그만 보기 때문에 운영에서 {@code SEED_DATA=true} 를 주면
 * 함께 돈다(첫 배포 직후 화면 확인용으로 그렇게 쓰도록 만들어져 있다). 팀·모집글은 그래도
 * 되지만 <b>죽은 링크는 안 된다</b>. 그래서 프로파일을 한 겹 더 걸어, 운영에서는 플래그를
 * 켜도 이 행들이 들어갈 수 없게 한다.
 *
 * <h2>동기화와의 관계 (§8-1 보존 규칙)</h2>
 * 인증키가 없는 동안은 충돌하지 않는다 — 동기화가 아예 돌지 않는다. <b>키를 넣으면 두 가지가
 * 달라진다.</b>
 * <ul>
 *   <li>기동 시 1회 동기화가 더 이상 돌지 않는다. {@code StadiumSyncScheduler.onStartup} 은
 *       SEOUL_PUBLIC 이 <b>비어 있을 때만</b> 도는데, 이 시드가 그 칸을 채운다.
 *       (ApplicationRunner 가 ApplicationReadyEvent 보다 먼저라 순서도 그렇게 된다.)</li>
 *   <li>새벽 5시 동기화가 이 행들을 <b>지운다.</b> 공공 API 응답에 없는 SEOUL_PUBLIC 행은
 *       내려간 예약 서비스로 보고 삭제하는 규칙이기 때문이다(죽은 링크를 남기지 않으려는
 *       의도이고, 가짜 행에도 똑같이 적용된다).</li>
 * </ul>
 * 즉 <b>키를 넣는 날 이 시드는 역할이 끝난다.</b> 그때는 이 클래스를 지우면 된다 — 실데이터가
 * 들어오므로 더 필요하지 않다. 보존 규칙을 고쳐 가짜 행을 살리는 쪽은 택하지 않았다.
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "kickoff.seed-data", havingValue = "true")
@RequiredArgsConstructor
public class SeoulStadiumDevSeed implements ApplicationRunner {

    /**
     * 공공 API 의 SVCID 형식(S + 18자리)을 흉내 낸 가짜 id.
     *
     * <b>실제로 존재하지 않는 id 다.</b> 형식만 같게 둔 이유는 FE 가 주소를 만드는 방식과
     * 길이·모양을 함께 확인할 수 있어야 해서다. 링크를 누르면 서울시 예약 사이트에서
     * 오류가 나는 것이 정상이다.
     */
    private static final String RESERVATION_URL =
            "https://yeyak.seoul.go.kr/web/reservation/selectReservView.do?rsv_svc_id=";

    /** 공공 API 가 주는 모양 그대로 (날짜만, 계약서 §8-1). */
    private static final String USE_PERIOD = "2026-01-01 ~ 2026-12-31";

    private final StadiumRepository stadiumRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (stadiumRepository.countBySource(StadiumSource.SEOUL_PUBLIC) > 0) {
            // 이미 있으면 건너뛴다. H2 인메모리는 재기동마다 비지만, 개발 DB 를 파일이나
            // PostgreSQL 로 바꿔 쓰는 날 중복으로 쌓이지 않게 한다 (externalId 유니크가
            // 막아 주긴 하지만 그때는 기동이 실패한다).
            log.info("서울 공공 구장 개발 시드 생략 — 이미 SEOUL_PUBLIC 행이 있다");
            return;
        }

        stadiumRepository.saveAll(List.of(
                // 같은 구장이 요일별로 갈린 두 행. 공공 API 의 단위가 구장이 아니라
                // 예약 서비스라서 이렇게 온다 (계약서 §8-1, v1.27.1) — 화면이 이 둘을
                // 어떻게 묶어 보여 줄지가 이 시드의 주 용도다.
                seoul("S260101000000000011", "월곡 인조잔디구장(평일)-2026년",
                        "서울 성북구", "접수중"),
                seoul("S260101000000000012", "월곡 인조잔디구장(토/일/공휴일)-2026년",
                        "서울 성북구", "예약마감"),
                // 괄호 없는 이름 — 요일로 갈리지 않는 서비스도 있다. 묶기 로직이 괄호를
                // 전제하면 이 행에서 깨진다.
                seoul("S260101000000000013", "마포구립축구장-2026년", "서울 마포구", "접수중"),
                // 다른 자치구. 지역 필터가 "서울"(시 단위 프리픽스)과 "서울 성북구"
                // (자치구) 양쪽으로 눌리는지 보려면 자치구가 둘 이상이어야 한다.
                //
                // 접수 상태를 세 번째 값으로 둔 것은 <b>의도</b>다. 실제 응답에서 확인한
                // 값은 "접수중"·"예약마감" 둘뿐이고 "안내중"은 그럴듯하게 고른 것이다 —
                // 공공 API 가 모르는 문구를 보낼 때 칩이 어떻게 그려지는지 디자인이 미리
                // 보게 하려는 것이다. 서버는 원문을 그대로 통과시킨다(§8-1).
                seoul("S260101000000000014", "강서구민운동장 축구장-2026년",
                        "서울 강서구", "안내중")));

        log.info("서울 공공 구장 개발 시드 완료 — 4건 (가짜 rsv_svc_id, 링크는 열리지 않는다)");
    }

    /** address 는 null 이다 — 공공 API 응답에 주소 필드가 없다 (계약서 §8-1, v1.27.1). */
    private Stadium seoul(String externalId, String name, String region, String acceptStatus) {
        return Stadium.builder()
                .externalId(externalId)
                .name(name)
                .region(region)
                .reservationUrl(RESERVATION_URL + externalId)
                .source(StadiumSource.SEOUL_PUBLIC)
                .acceptStatus(acceptStatus)
                .usePeriod(USE_PERIOD)
                .build();
    }
}
