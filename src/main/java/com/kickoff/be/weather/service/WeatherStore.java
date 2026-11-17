package com.kickoff.be.weather.service;

import com.kickoff.be.weather.client.dto.MidLandForecast;
import com.kickoff.be.weather.client.dto.MidTemperature;
import com.kickoff.be.weather.client.dto.ShortTermForecast;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 받아 둔 예보 보관함 (계약서 §8-3).
 *
 * <b>DB 에 넣지 않는다.</b> 예보는 몇 시간이면 갈아치워지는 값이고 재기동하면 다시 받으면
 * 된다. 표를 만들면 지우는 일(보관 기간)이 따라붙는데, 그만한 값이 아니다.
 *
 * <b>낡은 값은 읽는 쪽에서 가린다.</b> 들어올 때 거르지 않는 이유는, 보관할 때는
 * 최신이었던 값이 시간이 지나 낡아지기 때문이다 — 판정은 읽는 시각에 해야 한다.
 * 그 안에서는 호출이 실패해도 직전 값을 계속 내린다(계약서 §8-3 규칙 4).
 */
@Component
public class WeatherStore {

    /** 단기는 3시간마다 새로 나온다. 12시간이면 네 번을 내리 놓친 것이다. */
    private static final Duration SHORT_TERM_LIFE = Duration.ofHours(12);

    /** 중기는 하루 두 번이다. 36시간이면 세 번을 내리 놓친 것이다. */
    private static final Duration MID_TERM_LIFE = Duration.ofHours(36);

    private final Map<String, ShortTermForecast> shortTerm = new ConcurrentHashMap<>();
    private final Map<String, MidLandForecast> midLand = new ConcurrentHashMap<>();
    private final Map<String, MidTemperature> midTemperature = new ConcurrentHashMap<>();

    public void putShortTerm(String gridKey, ShortTermForecast forecast) {
        shortTerm.put(gridKey, forecast);
    }

    public void putMidLand(String regId, MidLandForecast forecast) {
        midLand.put(regId, forecast);
    }

    public void putMidTemperature(String regId, MidTemperature forecast) {
        midTemperature.put(regId, forecast);
    }

    public Optional<ShortTermForecast> shortTerm(String gridKey, OffsetDateTime now) {
        return fresh(shortTerm.get(gridKey),
                forecast -> forecast.announcedAt(), SHORT_TERM_LIFE, now);
    }

    public Optional<MidLandForecast> midLand(String regId, OffsetDateTime now) {
        return fresh(midLand.get(regId), forecast -> forecast.announcedAt(), MID_TERM_LIFE, now);
    }

    public Optional<MidTemperature> midTemperature(String regId, OffsetDateTime now) {
        return fresh(midTemperature.get(regId),
                forecast -> forecast.announcedAt(), MID_TERM_LIFE, now);
    }

    /** 그 발표를 이미 받아 뒀는가 — 같은 발표를 두 번 부르지 않으려고 본다. */
    public boolean hasShortTerm(String gridKey, OffsetDateTime announcedAt) {
        ShortTermForecast held = shortTerm.get(gridKey);
        return held != null && !held.announcedAt().isBefore(announcedAt);
    }

    public boolean hasMidTerm(String landRegId, String taRegId, OffsetDateTime announcedAt) {
        MidLandForecast land = midLand.get(landRegId);
        MidTemperature temperature = midTemperature.get(taRegId);
        return land != null && temperature != null
                && !land.announcedAt().isBefore(announcedAt)
                && !temperature.announcedAt().isBefore(announcedAt);
    }

    /** 보관분이 있는지만 (발표 시각과 무관) — 새 글의 지역을 바로 받아야 하는지 본다. */
    public boolean hasAnyShortTerm(String gridKey) {
        return shortTerm.containsKey(gridKey);
    }

    /** 테스트와 재기동 흉내용. 운영 코드에서 부를 일은 없다. */
    public void clear() {
        shortTerm.clear();
        midLand.clear();
        midTemperature.clear();
    }

    private <T> Optional<T> fresh(T held, java.util.function.Function<T, OffsetDateTime> announcedAt,
                                  Duration life, OffsetDateTime now) {
        if (held == null) {
            return Optional.empty();
        }
        return announcedAt.apply(held).plus(life).isAfter(now)
                ? Optional.of(held) : Optional.empty();
    }
}
