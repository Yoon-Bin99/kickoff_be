package com.kickoff.be.weather.service;

import com.kickoff.be.weather.dto.Weather;
import com.kickoff.be.weather.dto.WeatherCondition;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 개발용 가짜 예보 (계약서 §8-3).
 *
 * <b>왜 필요한가.</b> 인증키가 없거나 기상청이 실제로 그날 맑으면 FE 가 비·눈 화면을
 * 확인할 길이 없다. 경기일과 지역으로 정해지는 고정 값을 내려서 이모지·한 줄 문구·
 * 최저/최고 표시를 다 밟아 볼 수 있게 한다.
 *
 * <b>dev 프로필에서만 쓰인다</b>({@code WeatherService} 가 프로필까지 함께 본다).
 * 운영에서 나가면 지어낸 예보를 기상청 예보처럼 보여 주는 일이 되고, 그건 예보 행위다
 * (기상법 제17조).
 *
 * <b>같은 글은 늘 같은 값이다.</b> 무작위로 만들면 새로 고칠 때마다 날씨가 바뀌어
 * 화면 확인이 안 된다.
 */
final class FakeWeather {

    private static final WeatherCondition[] WHEEL = {
            WeatherCondition.CLEAR,
            WeatherCondition.PARTLY_CLOUDY,
            WeatherCondition.CLOUDY,
            WeatherCondition.RAIN,
            WeatherCondition.SHOWER,
            WeatherCondition.SNOW,
            WeatherCondition.RAIN_SNOW,
    };

    /** 오늘~3일은 단기, 4~10일은 중기, 그 밖은 없음 (계약서 §8-3). */
    private static final int HOURLY_DAYS = 3;
    private static final int MID_TERM_DAYS = 10;

    private FakeWeather() {
    }

    static Weather of(OffsetDateTime matchAt, WeatherArea area, OffsetDateTime now) {
        LocalDate today = now.atZoneSameInstant(WeatherAnnouncements.KST).toLocalDate();
        LocalDate matchDate = matchAt.atZoneSameInstant(WeatherAnnouncements.KST).toLocalDate();
        long ahead = ChronoUnit.DAYS.between(today, matchDate);
        if (ahead < 0 || ahead > MID_TERM_DAYS) {
            return null;
        }
        int seed = Math.abs((area.displayName() + matchDate).hashCode());
        WeatherCondition condition = WHEEL[seed % WHEEL.length];
        int pop = seed % 11 * 10;
        // 발표 시각도 그럴듯해야 한다 — FE 가 "11월 16일 17시 발표"를 그린다.
        OffsetDateTime announcedAt = ahead <= HOURLY_DAYS
                ? WeatherAnnouncements.latestShortTerm(now)
                : WeatherAnnouncements.latestMidTerm(now);
        if (ahead <= HOURLY_DAYS) {
            int temperature = seed % 30 - 5;
            return Weather.hourly(condition, temperature, pop, area.displayName(), announcedAt);
        }
        int min = seed % 15 - 8;
        return Weather.daily(condition, min, min + 7, pop, area.displayName(), announcedAt);
    }
}
