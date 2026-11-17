package com.kickoff.be.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.weather.client.KmaClient;
import com.kickoff.be.weather.client.KmaProvider;
import com.kickoff.be.weather.client.WeatherProperties;
import com.kickoff.be.weather.client.dto.HourSlot;
import com.kickoff.be.weather.client.dto.MidLandForecast;
import com.kickoff.be.weather.client.dto.MidTemperature;
import com.kickoff.be.weather.client.dto.ShortTermForecast;
import com.kickoff.be.weather.service.WeatherAreas;
import com.kickoff.be.weather.service.WeatherCallBudget;
import com.kickoff.be.weather.service.WeatherFetcher;
import com.kickoff.be.weather.service.WeatherStore;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 무엇을 언제 받아 오는가 (계약서 §8-3 「받아 오기」).
 *
 * <b>호출 수가 곧 비용이다.</b> 개발 계정은 서비스별 하루 10,000건이고, 같은 발표를
 * 거듭 받거나 경기가 없는 지역까지 받으면 금세 닿는다 — 그 두 가지를 여기서 못박는다.
 */
class WeatherFetchTest extends IntegrationTestSupport {

    @Autowired
    private WeatherStore weatherStore;
    @Autowired
    private WeatherAreas weatherAreas;
    @Autowired
    private WeatherCallBudget budget;

    private RecordingKmaClient client;
    private Team team;

    @BeforeEach
    void setUpTeam() {
        weatherStore.clear();
        client = new RecordingKmaClient();
        User owner = createUser("owner@example.com", "김감독", "010-1111-1111");
        team = createTeam(owner, "FC 새벽", "서울 강서구");
    }

    @AfterEach
    void clearStore() {
        weatherStore.clear();
    }

    @Test
    @DisplayName("경기가 잡힌 지역만 받는다 — 표 전체를 받지 않는다")
    void fetchesOnlyRegionsWithMatches() {
        createPostIn(team, "강서 경기", OffsetDateTime.now().plusDays(2), "서울 강서구");
        createPostIn(team, "강남 경기", OffsetDateTime.now().plusDays(3), "서울 강남구");

        fetcher(true).refreshAll(OffsetDateTime.now());

        assertThat(client.grids).containsExactlyInAnyOrder("58,126", "61,126");
    }

    /** 같은 격자에 글이 여럿이어도 한 번이다 (계약서 §8-3). */
    @Test
    @DisplayName("같은 격자는 한 번만 부른다")
    void sameGridIsFetchedOnce() {
        createPostIn(team, "첫 경기", OffsetDateTime.now().plusDays(2), "서울 강서구");
        createPostIn(team, "둘째 경기", OffsetDateTime.now().plusDays(3), "서울 강서구");

        fetcher(true).refreshAll(OffsetDateTime.now());

        assertThat(client.grids).containsExactly("58,126");
    }

    /** 30분마다 돌지만 새 발표가 나오기 전에는 호출이 없어야 한다. */
    @Test
    @DisplayName("이미 받은 발표는 다시 받지 않는다")
    void doesNotRefetchTheSameAnnouncement() {
        createPostIn(team, "강서 경기", OffsetDateTime.now().plusDays(2), "서울 강서구");
        WeatherFetcher fetcher = fetcher(true);
        fetcher.refreshAll(OffsetDateTime.now());
        client.grids.clear();

        fetcher.refreshAll(OffsetDateTime.now());

        assertThat(client.grids).isEmpty();
    }

    @Test
    @DisplayName("11일보다 먼 경기의 지역은 받지 않는다")
    void ignoresMatchesBeyondTheHorizon() {
        createPostIn(team, "먼 경기", OffsetDateTime.now().plusDays(20), "서울 강서구");

        fetcher(true).refreshAll(OffsetDateTime.now());

        assertThat(client.grids).isEmpty();
    }

    /** 키가 없으면 받아 오기 자체를 돌리지 않는다 (계약서 §8-3). 기동은 정상이다. */
    @Test
    @DisplayName("인증키가 없으면 아예 부르지 않는다")
    void doesNothingWithoutAKey() {
        createPostIn(team, "강서 경기", OffsetDateTime.now().plusDays(2), "서울 강서구");

        fetcher(false).refreshAll(OffsetDateTime.now());

        assertThat(client.grids).isEmpty();
    }

    /** 새 글의 지역은 주기를 기다리지 않고 바로 받는다 (계약서 §8-3). */
    @Test
    @DisplayName("한 지역만 바로 받을 수 있고, 보관분이 있으면 지나간다")
    void refreshesOneRegionOnDemand() {
        WeatherFetcher fetcher = fetcher(true);

        fetcher.refreshRegion("서울 강서구", OffsetDateTime.now());
        assertThat(client.grids).containsExactly("58,126");

        client.grids.clear();
        fetcher.refreshRegion("서울 강서구", OffsetDateTime.now());
        assertThat(client.grids).as("이미 보관분이 있으면 부르지 않는다").isEmpty();
    }

    private WeatherFetcher fetcher(boolean configured) {
        WeatherProperties properties = new WeatherProperties(
                configured ? "test-key" : "", KmaProvider.DATA_GO_KR, null, false);
        return new WeatherFetcher(postRepository, weatherAreas, weatherStore, client,
                budget, properties);
    }

    /** 무엇을 불렀는지만 남기는 가짜 기상청. 실제 Expo·카카오 스텁과 같은 생각이다. */
    private static class RecordingKmaClient implements KmaClient {

        private final List<String> grids = new ArrayList<>();

        @Override
        public Optional<ShortTermForecast> shortTerm(int nx, int ny, OffsetDateTime baseAt) {
            grids.add(nx + "," + ny);
            return Optional.of(new ShortTermForecast(baseAt, Map.of(
                    LocalDateTime.now().plusDays(1), new HourSlot(1, 0, 3, 20))));
        }

        @Override
        public Optional<MidLandForecast> midLand(String regId, OffsetDateTime tmFc) {
            return Optional.empty();
        }

        @Override
        public Optional<MidTemperature> midTemperature(String regId, OffsetDateTime tmFc) {
            return Optional.empty();
        }
    }
}
