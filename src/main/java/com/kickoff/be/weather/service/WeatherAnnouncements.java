package com.kickoff.be.weather.service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 발표 시각 계산 (계약서 §8-3).
 *
 * 받아 오기를 30분마다 돌리되 <b>매번 다 받지는 않는다</b> — "새 발표가 나왔을 시각인데
 * 아직 그 발표를 못 받은 곳"만 받는다. 그러려면 "지금 기준으로 가장 최근 발표가 언제
 * 것인가"를 알아야 하고, 그걸 정하는 자리가 여기다.
 *
 * <b>발표 뒤 15분을 기다린다.</b> 발표 시각 정각에 부르면 아직 올라오지 않아 빈 응답을
 * 받고, 그 자리는 다음 주기까지 비어 있게 된다 — 기다리는 쪽이 호출도 덜 쓴다.
 */
public final class WeatherAnnouncements {

    /** 기상청의 시각은 전부 한국 시각이다. 서버 시간대가 무엇이든 여기서 못박는다. */
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 단기예보 발표 (계약서 §8-3). */
    private static final int[] SHORT_TERM_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};

    /** 중기예보 발표 (계약서 §8-3). */
    private static final int[] MID_TERM_HOURS = {6, 18};

    private static final Duration SETTLE = Duration.ofMinutes(15);

    private WeatherAnnouncements() {
    }

    public static OffsetDateTime latestShortTerm(OffsetDateTime now) {
        return latest(now, SHORT_TERM_HOURS);
    }

    public static OffsetDateTime latestMidTerm(OffsetDateTime now) {
        return latest(now, MID_TERM_HOURS);
    }

    /**
     * 지금 받아 볼 수 있는 가장 최근 발표. 오늘 것이 아직 하나도 없으면 어제 마지막
     * 발표로 넘어간다 — 02시 발표 전의 새벽이 그렇다.
     */
    private static OffsetDateTime latest(OffsetDateTime now, int[] hours) {
        ZonedDateTime kst = now.atZoneSameInstant(KST).minus(SETTLE);
        for (int index = hours.length - 1; index >= 0; index--) {
            ZonedDateTime candidate = kst.with(LocalTime.of(hours[index], 0));
            if (!candidate.isAfter(kst)) {
                return candidate.toOffsetDateTime();
            }
        }
        ZonedDateTime yesterday = kst.minusDays(1).with(LocalTime.of(hours[hours.length - 1], 0));
        return yesterday.toOffsetDateTime();
    }
}
