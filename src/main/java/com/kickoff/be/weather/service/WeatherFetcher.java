package com.kickoff.be.weather.service;

import com.kickoff.be.post.repository.MatchPostRepository;
import com.kickoff.be.weather.client.KmaClient;
import com.kickoff.be.weather.client.WeatherProperties;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기상청 예보를 받아 보관함에 넣는다 (계약서 §8-3).
 *
 * <b>받는 곳을 글이 정한다.</b> 전국을 매번 받지 않고, 앞으로 11일 안에 경기가 잡힌
 * 지역만 받는다 — 아무도 보지 않는 예보에 하루 한도를 쓰지 않는다.
 *
 * <b>이미 받은 발표는 다시 받지 않는다.</b> 30분마다 돌지만 새 발표가 나온 뒤 첫 주기에만
 * 실제 호출이 일어난다. 단기는 하루 8번, 중기는 2번 발표라 그만큼만 부르면 된다.
 *
 * <b>한 곳이 실패해도 멈추지 않는다.</b> 실패는 그 지역만 건너뛰고 다음 주기에 다시 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherFetcher {

    /** 받아 오는 범위 — 중기예보가 10일까지라 하루 여유를 둔다 (계약서 §8-3). */
    private static final int HORIZON_DAYS = 11;

    private final MatchPostRepository postRepository;
    private final WeatherAreas areas;
    private final WeatherStore store;
    private final KmaClient client;
    private final WeatherCallBudget budget;
    private final WeatherProperties properties;

    /**
     * 주기 작업의 본문. 새 발표가 나왔는데 아직 못 받은 곳만 받는다.
     *
     * 읽기 전용 트랜잭션인 이유는 지역 목록을 조회하기 때문이다 — 쓰는 것은 메모리
     * 보관함뿐이라 DB 에 남기는 것이 없다.
     */
    @Transactional(readOnly = true)
    public void refreshAll(OffsetDateTime now) {
        if (!properties.isConfigured()) {
            // 키가 없으면 받아 오기 자체를 돌리지 않는다 (계약서 §8-3). 기동은 정상이다.
            return;
        }
        List<String> regions = postRepository.findRegionsWithUpcomingMatch(
                now, now.plusDays(HORIZON_DAYS));
        if (regions.isEmpty()) {
            return;
        }
        refresh(toAreas(regions), now);
    }

    /**
     * 한 지역만 바로 받는다 — 글이 새로 생겼거나 {@code matchAt}·{@code region} 이 바뀌어
     * 보관분이 없을 때 (계약서 §8-3).
     *
     * <b>이미 보관분이 있으면 아무것도 하지 않는다.</b> 글을 고칠 때마다 부르면 같은
     * 발표를 거듭 받게 된다.
     */
    public void refreshRegion(String region, OffsetDateTime now) {
        if (!properties.isConfigured()) {
            return;
        }
        Optional<WeatherArea> area = areas.find(region);
        if (area.isEmpty() || store.hasAnyShortTerm(area.get().gridKey())) {
            return;
        }
        refresh(Set.of(area.get()), now);
    }

    private Set<WeatherArea> toAreas(List<String> regions) {
        Set<WeatherArea> found = new LinkedHashSet<>();
        for (String region : regions) {
            areas.find(region).ifPresent(found::add);
        }
        return found;
    }

    private void refresh(Set<WeatherArea> targets, OffsetDateTime now) {
        OffsetDateTime shortTermBase = WeatherAnnouncements.latestShortTerm(now);
        OffsetDateTime midTermBase = WeatherAnnouncements.latestMidTerm(now);
        Set<String> doneGrids = new LinkedHashSet<>();
        Set<String> doneRegions = new LinkedHashSet<>();
        int calls = 0;

        for (WeatherArea area : targets) {
            if (doneGrids.add(area.gridKey())
                    && !store.hasShortTerm(area.gridKey(), shortTermBase)) {
                calls += fetchShortTerm(area, shortTermBase);
            }
            if (!area.hasMidTerm() || !doneRegions.add(area.midLandRegId() + area.midTaRegId())) {
                continue;
            }
            if (!store.hasMidTerm(area.midLandRegId(), area.midTaRegId(), midTermBase)) {
                calls += fetchMidTerm(area, midTermBase);
            }
        }
        if (calls > 0) {
            log.info("날씨 받아 오기 — 지역 {}곳, 호출 {}건 (오늘 누적 {}건)",
                    targets.size(), calls, budget.used());
        }
    }

    private int fetchShortTerm(WeatherArea area, OffsetDateTime base) {
        if (!budget.take()) {
            return 0;
        }
        try {
            client.shortTerm(area.nx(), area.ny(), base)
                    .ifPresent(forecast -> store.putShortTerm(area.gridKey(), forecast));
        } catch (RuntimeException e) {
            // 경계가 이미 삼키지만, 한 지역의 사고가 주기 전체를 멈추지 않게 한 겹 더 둔다.
            log.debug("단기예보 받기 실패 — {} {}", area.displayName(), e.getMessage());
        }
        return 1;
    }

    /**
     * 중기는 날씨와 기온을 따로 부른다 — 서비스가 둘로 나뉘어 있다.
     *
     * 기온이 실패해도 날씨는 넣는다. 중기예보 API 는 개편이 예고돼 있어 한쪽 형식만
     * 바뀔 수 있고(계약서 §8-3), 그때 둘 다 버리면 멀쩡한 자료까지 사라진다.
     */
    private int fetchMidTerm(WeatherArea area, OffsetDateTime base) {
        int calls = 0;
        if (budget.take()) {
            calls++;
            try {
                client.midLand(area.midLandRegId(), base)
                        .ifPresent(forecast -> store.putMidLand(area.midLandRegId(), forecast));
            } catch (RuntimeException e) {
                log.debug("중기육상예보 받기 실패 — {} {}", area.midLandRegId(), e.getMessage());
            }
        }
        if (budget.take()) {
            calls++;
            try {
                client.midTemperature(area.midTaRegId(), base)
                        .ifPresent(forecast ->
                                store.putMidTemperature(area.midTaRegId(), forecast));
            } catch (RuntimeException e) {
                log.debug("중기기온 받기 실패 — {} {}", area.midTaRegId(), e.getMessage());
            }
        }
        return calls;
    }
}
