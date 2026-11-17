package com.kickoff.be.weather;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.weather.client.dto.HourSlot;
import com.kickoff.be.weather.client.dto.ShortTermForecast;
import com.kickoff.be.weather.service.WeatherAreas;
import com.kickoff.be.weather.service.WeatherStore;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * 글·신청 응답에 날씨가 실린다 (계약서 §8-3).
 *
 * 기상청을 부르지 않고 <b>보관함에 직접 넣어</b> 확인한다 — 이 테스트가 보려는 것은
 * "받아 둔 예보가 응답에 실리는가"이고, 받아 오는 쪽은 따로 본다.
 *
 * 보관함은 컨텍스트 하나를 여러 테스트가 공유하므로 끝나면 비운다.
 */
class PostWeatherTest extends IntegrationTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private WeatherStore weatherStore;
    @Autowired
    private WeatherAreas weatherAreas;

    private User owner;
    private Team team;

    @BeforeEach
    void setUpTeam() {
        weatherStore.clear();
        owner = createUser("owner@example.com", "김감독", "010-1111-1111");
        team = createTeam(owner, "FC 새벽", "서울 강서구");
    }

    @AfterEach
    void clearStore() {
        weatherStore.clear();
    }

    @Test
    @DisplayName("글 상세에 경기 시각의 날씨가 실린다")
    void postDetailCarriesWeather() throws Exception {
        OffsetDateTime matchAt = OffsetDateTime.now().plusDays(1).withHour(7).withMinute(40);
        MatchPost post = createPostIn(team, "토요일 아침", matchAt, "서울 강서구");
        hold("서울 강서구", matchAt, new HourSlot(1, 0, 3, 20));

        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weather.condition").value("CLEAR"))
                .andExpect(jsonPath("$.weather.kind").value("HOURLY"))
                .andExpect(jsonPath("$.weather.temperature").value(3))
                .andExpect(jsonPath("$.weather.precipitationProbability").value(20))
                .andExpect(jsonPath("$.weather.area").value("서울 강서구"))
                .andExpect(jsonPath("$.weather.announcedAt").exists())
                .andExpect(jsonPath("$.weather.temperatureMin").doesNotExist());
    }

    @Test
    @DisplayName("목록 카드에도 실린다")
    void postListCarriesWeather() throws Exception {
        OffsetDateTime matchAt = OffsetDateTime.now().plusDays(1).withHour(7).withMinute(0);
        createPostIn(team, "토요일 아침", matchAt, "서울 강서구");
        hold("서울 강서구", matchAt, new HourSlot(4, 1, 1, 80));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].weather.condition").value("RAIN"))
                .andExpect(jsonPath("$.content[0].weather.temperature").value(1));
    }

    @Test
    @DisplayName("신청 응답에도 같은 규칙으로 실린다")
    void requestCarriesWeather() throws Exception {
        OffsetDateTime matchAt = OffsetDateTime.now().plusDays(1).withHour(7).withMinute(0);
        MatchPost post = createPostIn(team, "토요일 아침", matchAt, "서울 강서구");
        User applicant = createUser("applicant@example.com", "박감독", "010-2222-2222");
        Team applicantTeam = createTeam(applicant, "FC 노을", "서울 송파구");
        acceptedRequest(post, applicantTeam);
        hold("서울 강서구", matchAt, new HourSlot(3, 0, -2, 60));

        mockMvc.perform(get("/api/requests/sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weather.condition").value("PARTLY_CLOUDY"))
                .andExpect(jsonPath("$[0].weather.temperature").value(-2));
    }

    /** 날씨가 없다고 글이 안 보이면 안 된다 (계약서 §8-3). */
    @Test
    @DisplayName("보관된 예보가 없으면 weather 는 null 이고 나머지는 정상이다")
    void noForecastMeansNullWeather() throws Exception {
        MatchPost post = createPostIn(team, "토요일 아침",
                OffsetDateTime.now().plusDays(1), "서울 강서구");

        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("토요일 아침"))
                .andExpect(jsonPath("$.weather").doesNotExist());
    }

    @Test
    @DisplayName("지난 경기는 예보가 보관돼 있어도 weather 가 null 이다")
    void pastMatchHasNoWeather() throws Exception {
        OffsetDateTime matchAt = OffsetDateTime.now().minusDays(1).withHour(7).withMinute(0);
        MatchPost post = createPostIn(team, "지난 경기", matchAt, "서울 강서구");
        hold("서울 강서구", matchAt, new HourSlot(1, 0, 3, 20));

        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weather").doesNotExist());
    }

    @Test
    @DisplayName("시/도를 알아볼 수 없는 지역이면 weather 가 null 이다")
    void unknownRegionHasNoWeather() throws Exception {
        OffsetDateTime matchAt = OffsetDateTime.now().plusDays(1).withHour(7).withMinute(0);
        MatchPost post = createPostIn(team, "어딘가", matchAt, "어딘가 저기");

        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weather").doesNotExist());
    }

    /** 그 지역의 격자에 "지금 발표"로 한 칸을 넣어 둔다. */
    private void hold(String region, OffsetDateTime matchAt, HourSlot slot) {
        LocalDateTime at = matchAt.atZoneSameInstant(KST).toLocalDateTime()
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        weatherStore.putShortTerm(weatherAreas.find(region).orElseThrow().gridKey(),
                new ShortTermForecast(OffsetDateTime.now(), Map.of(at, slot)));
    }
}
