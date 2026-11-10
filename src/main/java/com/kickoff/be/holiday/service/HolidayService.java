package com.kickoff.be.holiday.service;

import com.kickoff.be.holiday.client.HolidayLookupException;
import com.kickoff.be.holiday.client.KakaoHolidayClient;
import com.kickoff.be.holiday.dto.Holiday;
import com.kickoff.be.holiday.dto.HolidayResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 공휴일 조회 (계약서 §8-2).
 *
 * <b>이 서비스는 절대 실패하지 않는다.</b> 어드민 키가 없든 카카오가 죽었든 타임아웃이든,
 * {@code source: NONE} + 빈 목록으로 200 을 낸다. 달력은 앱의 기본 화면이라, 공휴일을 몰라서
 * 화면이 안 뜨는 쪽이 공휴일 색이 빠지는 쪽보다 훨씬 나쁘다.
 *
 * <h2>캐시</h2>
 * 연 단위 인메모리 캐시다. 외부 호출이 요청 스레드에서 일어나므로(§8-2) 캐시가 없으면
 * 달력을 열 때마다 최대 10초를 기다릴 수 있다(한 해가 창 열세 개로 쪼개진다).
 *
 * TTL 을 세 가지로 둔다.
 * <ul>
 *   <li><b>지난 연도 + 성공</b> → 영구. 이미 끝난 해의 공휴일은 바뀌지 않는다</li>
 *   <li><b>올해·앞으로 + 성공</b> → 하루. 임시공휴일이 중간에 지정되는 일이 실제로 있다</li>
 *   <li><b>NONE</b> → 10분. 영구로 두면 키를 넣거나 공표된 뒤에도 하루 동안 모른 채로
 *       남고, 아예 캐시하지 않으면 카카오가 죽은 동안 <b>모든 요청이 타임아웃만큼</b> 기다린다.
 *       짧게 담아 둘의 중간을 잡는다</li>
 * </ul>
 *
 * 인메모리라 재기동하면 비워진다. 다중 인스턴스에서는 인스턴스별로 따로 담긴다 — 같은
 * 값을 두 번 받아 오는 것뿐이라 틀리지는 않는다.
 */
@Slf4j
@Service
public class HolidayService {

    private static final Duration FUTURE_TTL = Duration.ofDays(1);
    private static final Duration NONE_TTL = Duration.ofMinutes(10);

    private final KakaoHolidayClient client;
    private final Clock clock;
    private final Map<Integer, Cached> cache = new ConcurrentHashMap<>();

    /** 캐시 한 칸. {@code holidays} 가 null 이면 NONE 을 담은 것이다. */
    private record Cached(List<Holiday> holidays, Instant expiresAt) {

        boolean isAlive(Instant now) {
            return expiresAt == null || now.isBefore(expiresAt);
        }
    }

    /**
     * 생성자가 둘이라 <b>어느 쪽을 쓸지 표시해야 한다.</b> 안 붙이면 스프링이 기본 생성자를
     * 찾다가 기동이 실패한다 — 컨텍스트가 뜨지 않아 그 패키지 테스트가 통째로 깨지는
     * 방식으로 드러난다(실제로 그렇게 걸렸다).
     */
    @Autowired
    public HolidayService(KakaoHolidayClient client) {
        this(client, Clock.systemDefaultZone());
    }

    /** 테스트가 TTL 경과를 만들 수 있게 시계를 주입받는다. */
    HolidayService(KakaoHolidayClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public HolidayResponse of(int year) {
        Instant now = clock.instant();
        Cached cached = cache.get(year);
        if (cached != null && cached.isAlive(now)) {
            return respond(year, cached.holidays());
        }
        if (!client.isConfigured()) {
            // 키는 사용자가 콘솔에서 받아 넣는 것이라, 없는 상태가 정상 동작의 하나다.
            // 매 요청 warn 을 내면 진짜 경고가 묻힌다.
            log.debug("공휴일 조회 건너뜀 — KAKAO_ADMIN_KEY 없음 (source NONE)");
            return store(year, null, now);
        }
        try {
            List<Holiday> holidays = client.fetch(year);
            if (holidays.isEmpty()) {
                // 카카오에 그 해 데이터가 없다. "0건 성공"이 아니라 "모른다"로 내린다.
                log.info("{}년 공휴일이 카카오에 없다 — source NONE", year);
                return store(year, null, now);
            }
            return store(year, holidays, now);
        } catch (HolidayLookupException e) {
            log.warn("공휴일 조회 실패 — source NONE 으로 내린다. {}", e.getMessage());
            return store(year, null, now);
        }
    }

    private HolidayResponse store(int year, List<Holiday> holidays, Instant now) {
        cache.put(year, new Cached(holidays, expiry(year, holidays, now)));
        return respond(year, holidays);
    }

    /** null 이면 만료 없음(영구). */
    private Instant expiry(int year, List<Holiday> holidays, Instant now) {
        if (holidays == null) {
            return now.plus(NONE_TTL);
        }
        return year < LocalDate.now(ZoneId.of("Asia/Seoul")).getYear()
                ? null
                : now.plus(FUTURE_TTL);
    }

    private HolidayResponse respond(int year, List<Holiday> holidays) {
        return holidays == null
                ? HolidayResponse.none(year)
                : HolidayResponse.kakao(year, holidays);
    }

    /** 테스트가 캐시를 비우는 용도. 운영 코드에서는 부르지 않는다. */
    public void clearCache() {
        cache.clear();
    }
}
