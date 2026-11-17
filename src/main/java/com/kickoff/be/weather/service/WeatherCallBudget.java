package com.kickoff.be.weather.service;

import com.kickoff.be.weather.client.WeatherProperties;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 하루 호출 수를 센다 (계약서 §8-3).
 *
 * <b>왜 세는가.</b> 공공데이터포털 개발 계정은 서비스별 하루 10,000건이고, 넘기면 그날
 * 남은 호출이 전부 실패한다 — 보관분까지 낡아 버리면 날씨가 통째로 사라진다. 90%에서
 * 스스로 멈추면 남은 10%가 완충이 되고, 그날은 보관분으로 버틴다.
 *
 * 절반에서 경고를 남기는 것은 <b>한도에 닿기 전에 알아채라고</b> 두는 것이다. 지역이
 * 늘어 호출이 늘었다는 신호이기도 하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherCallBudget {

    private static final double WARN_AT = 0.5;
    private static final double STOP_AT = 0.9;

    private final WeatherProperties properties;

    private LocalDate day;
    private int used;
    private boolean warned;

    /** 한 번 더 불러도 되는지. 된다면 센 것으로 친다. */
    public synchronized boolean take() {
        LocalDate today = LocalDate.now(WeatherAnnouncements.KST);
        if (!today.equals(day)) {
            day = today;
            used = 0;
            warned = false;
        }
        int limit = properties.dailyLimit();
        if (used >= limit * STOP_AT) {
            return false;
        }
        used++;
        if (!warned && used > limit * WARN_AT) {
            warned = true;
            log.warn("날씨 호출이 하루 한도의 절반을 넘었다 — {}/{}건. 지역이 늘었는지 "
                    + "kickoff.weather.daily-limit 이 맞는지 볼 것", used, limit);
        }
        return true;
    }

    /** 오늘 몇 번 불렀는지 — 로그와 테스트용. */
    public synchronized int used() {
        return LocalDate.now(WeatherAnnouncements.KST).equals(day) ? used : 0;
    }

    /** 기준 시각을 넘겨 쓰는 테스트용 — 운영 코드는 {@link #take()} 를 쓴다. */
    synchronized void resetFor(OffsetDateTime now) {
        day = now.atZoneSameInstant(WeatherAnnouncements.KST).toLocalDate();
        used = 0;
        warned = false;
    }
}
