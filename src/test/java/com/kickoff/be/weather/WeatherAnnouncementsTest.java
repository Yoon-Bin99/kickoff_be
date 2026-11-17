package com.kickoff.be.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.weather.service.WeatherAnnouncements;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * "지금 받아 볼 수 있는 가장 최근 발표가 언제 것인가" (계약서 §8-3).
 *
 * 이 값이 틀리면 둘 중 하나다 — 아직 올라오지 않은 발표를 불러 빈 응답을 받거나,
 * 새 발표가 나왔는데도 받지 않아 보관분이 낡는다.
 */
class WeatherAnnouncementsTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Test
    @DisplayName("단기 — 발표 15분 뒤부터 그 발표를 받는다")
    void shortTermWaitsFifteenMinutes() {
        assertThat(WeatherAnnouncements.latestShortTerm(at(17, 14)).getHour()).isEqualTo(14);
        assertThat(WeatherAnnouncements.latestShortTerm(at(17, 15)).getHour()).isEqualTo(17);
        assertThat(WeatherAnnouncements.latestShortTerm(at(19, 59)).getHour()).isEqualTo(17);
    }

    /** 02시 발표 전의 새벽이 하루를 넘기는 자리다. */
    @Test
    @DisplayName("단기 — 오늘 발표가 아직 없으면 어제 23시다")
    void shortTermCrossesMidnight() {
        OffsetDateTime base = WeatherAnnouncements.latestShortTerm(at(1, 0));

        assertThat(base.getHour()).isEqualTo(23);
        assertThat(base.getDayOfMonth()).isEqualTo(16);
    }

    @Test
    @DisplayName("중기 — 06시와 18시 두 번이다")
    void midTermTwiceADay() {
        assertThat(WeatherAnnouncements.latestMidTerm(at(6, 20)).getHour()).isEqualTo(6);
        assertThat(WeatherAnnouncements.latestMidTerm(at(17, 0)).getHour()).isEqualTo(6);
        assertThat(WeatherAnnouncements.latestMidTerm(at(18, 30)).getHour()).isEqualTo(18);
    }

    @Test
    @DisplayName("중기 — 06시 발표 전이면 어제 18시다")
    void midTermCrossesMidnight() {
        OffsetDateTime base = WeatherAnnouncements.latestMidTerm(at(5, 0));

        assertThat(base.getHour()).isEqualTo(18);
        assertThat(base.getDayOfMonth()).isEqualTo(16);
    }

    /** 서버 시간대가 무엇이든 기상청 시각은 한국 시각이다. */
    @Test
    @DisplayName("다른 시간대로 물어도 한국 시각 기준으로 답한다")
    void worksFromAnotherZone() {
        OffsetDateTime utc = OffsetDateTime.of(2026, 11, 17, 8, 30, 0, 0, ZoneOffset.UTC);

        assertThat(WeatherAnnouncements.latestShortTerm(utc).getHour())
                .as("UTC 08:30 은 KST 17:30 이다").isEqualTo(17);
    }

    private static OffsetDateTime at(int hour, int minute) {
        return OffsetDateTime.of(2026, 11, 17, hour, minute, 0, 0, KST);
    }
}
