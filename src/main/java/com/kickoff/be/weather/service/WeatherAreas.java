package com.kickoff.be.weather.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 글의 {@code region} 문자열을 예보 지점으로 바꾼다 (계약서 §8-3).
 *
 * <b>표를 코드에 박지 않는다.</b> 기상청 격자·구역 코드는 우리가 정한 값이 아니라
 * 공식 자료에서 옮겨 온 값이고, 행정구역이 바뀌면 다시 옮겨야 한다. 리소스 파일에
 * 두면 무엇을 쓰고 있는지 한눈에 보이고 코드 수정 없이 갈아 끼울 수 있다.
 *
 * {@code region} 은 자유 문자열이다("서울 강서구", "강원특별자치도 춘천시",
 * "경기 수원시 장안구"). 공백으로 나눠 첫 토막을 시/도로, 나머지를 시군구로 읽는다.
 */
@Slf4j
@Component
public class WeatherAreas {

    private static final String DEFAULT_RESOURCE = "weather-area.csv";

    /**
     * 시/도 이름 맞추기 — 정식 이름·옛 이름·짧은 이름이 모두 들어온다 (계약서 §8-3).
     *
     * 접미사를 떼는 규칙 하나로 줄이지 않는다. "전북특별자치도"에서 "특별자치도"를 떼면
     * "전북"이 되지만 "강원특별자치도"는 "강원"이고 "세종특별자치시"는 "세종"이라,
     * 규칙으로 쓰면 읽는 사람이 어느 경우가 되는지 머릿속으로 돌려 봐야 한다.
     *
     * 값이 <b>목록</b>인 것은 통합 명칭 때문이다(아래 참고). 대개는 한 개짜리다.
     */
    private static final Map<String, List<String>> SIDO = new LinkedHashMap<>();

    /**
     * 2026년에 광주와 전남이 한 시/도로 합쳐졌다(기상청 지역 자료의 "전남광주통합특별시").
     *
     * <b>표는 짧은 이름 17개를 그대로 둔다</b> — 글의 {@code region} 은 FE 의 시/도
     * 목록에서 고른 값이라 여전히 "광주 …"/"전남 …"으로 온다. 다만 통합 명칭으로 적혀
     * 온 글도 알아볼 수 있어야 해서 별칭으로 받는다. 어느 쪽인지는 <b>시군구 이름으로
     * 가르고</b>(광주는 자치구, 전남은 시·군), 가를 수 없으면 전남 대표 지점이다.
     */
    private static final List<String> GWANGJU_JEONNAM = List.of("광주", "전남");

    static {
        put("서울", "서울특별시", "서울시");
        put("부산", "부산광역시", "부산시");
        put("대구", "대구광역시", "대구시");
        put("인천", "인천광역시", "인천시");
        put("광주", "광주광역시", "광주시");
        put("대전", "대전광역시", "대전시");
        put("울산", "울산광역시", "울산시");
        put("세종", "세종특별자치시", "세종시");
        put("경기", "경기도");
        put("강원", "강원특별자치도", "강원도");
        put("충북", "충청북도");
        put("충남", "충청남도");
        put("전북", "전북특별자치도", "전라북도");
        put("전남", "전라남도");
        put("경북", "경상북도");
        put("경남", "경상남도");
        put("제주", "제주특별자치도", "제주도");
        SIDO.put("전남광주통합특별시", GWANGJU_JEONNAM);
        SIDO.put("전남광주통합시", GWANGJU_JEONNAM);
    }

    private static void put(String shortName, String... aliases) {
        SIDO.put(shortName, List.of(shortName));
        for (String alias : aliases) {
            SIDO.put(alias, List.of(shortName));
        }
    }

    /** 키는 "시/도\t시군구" — 대표 지점은 시군구 자리가 빈 문자열이다. */
    private final Map<String, WeatherArea> byName;
    private final List<WeatherArea> all;

    private final String resource;

    public WeatherAreas() {
        this(DEFAULT_RESOURCE);
    }

    /**
     * 표 파일을 바꿔 끼울 수 있게 열어 둔다 — <b>테스트가 다른 표를 쓰기 위해서다</b>.
     * 운영에서 쓰는 파일은 기본값 하나뿐이다.
     */
    WeatherAreas(String resource) {
        this.resource = resource;
        this.all = read();
        Map<String, WeatherArea> index = new LinkedHashMap<>();
        for (WeatherArea area : all) {
            index.put(key(area.sido(), area.isProvinceFallback() ? "" : area.sigungu()), area);
        }
        this.byName = Map.copyOf(index);
        log.info("날씨 지역표 {}줄 (중기 코드가 있는 줄 {}개)", all.size(),
                all.stream().filter(WeatherArea::hasMidTerm).count());
    }

    /** 받아 와야 할 곳을 묶을 때 쓴다 — 표 전체가 아니라 글이 있는 지역만 받는다. */
    public List<WeatherArea> all() {
        return all;
    }

    /**
     * 글의 {@code region} 에 해당하는 지점. 시/도조차 알아볼 수 없으면 비어 있다.
     *
     * 찾는 순서: 두 토막짜리 시군구("수원시 장안구") → 한 토막("강서구") → 한 토막으로
     * 시작하는 줄("수원시" → "수원시 권선구", 같은 시의 구들은 격자가 사실상 같다) →
     * 시/도 대표 지점.
     *
     * 시/도 후보가 둘인 통합 명칭은 <b>시군구가 맞는 쪽</b>을 고르고, 어느 쪽에도 없으면
     * 마지막 후보의 대표 지점으로 떨어진다.
     */
    public Optional<WeatherArea> find(String region) {
        if (region == null || region.isBlank()) {
            return Optional.empty();
        }
        String[] tokens = region.trim().split("\\s+");
        List<String> candidates = SIDO.get(tokens[0]);
        if (candidates == null) {
            return Optional.empty();
        }
        for (String sido : candidates) {
            Optional<WeatherArea> found = findIn(sido, tokens);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.ofNullable(
                byName.get(key(candidates.get(candidates.size() - 1), "")));
    }

    /** 한 시/도 안에서 시군구를 찾는다. 못 찾으면 비어 있다 — 대표 지점은 호출부가 본다. */
    private Optional<WeatherArea> findIn(String sido, String[] tokens) {
        if (tokens.length >= 3) {
            WeatherArea twoWord = byName.get(key(sido, tokens[1] + " " + tokens[2]));
            if (twoWord != null) {
                return Optional.of(twoWord);
            }
        }
        if (tokens.length >= 2) {
            WeatherArea exact = byName.get(key(sido, tokens[1]));
            if (exact != null) {
                return Optional.of(exact);
            }
            // 구 없이 "성남시"라고만 적힌 글이다. 격자는 그 시의 한 줄을 쓰되 이름은
            // 글에 적힌 수준으로 돌려준다 (v1.38.3) — 화면에 "성남시 분당구 기준"으로
            // 뜨면 글과 다른 구의 예보처럼 읽힌다.
            return all.stream()
                    .filter(area -> area.sido().equals(sido))
                    .filter(area -> !area.isProvinceFallback())
                    .filter(area -> area.sigungu().startsWith(tokens[1] + " "))
                    .findFirst()
                    .map(area -> area.renamedTo(tokens[1]));
        }
        return Optional.empty();
    }

    private static String key(String sido, String sigungu) {
        return sido + "\t" + sigungu;
    }

    /**
     * 표가 없거나 비어 있으면 <b>기동에서 멈춘다</b>. 조용히 빈 표로 뜨면 모든 글의
     * 날씨가 null 인데 아무 데도 이유가 남지 않는다.
     */
    private List<WeatherArea> read() {
        List<WeatherArea> rows = new ArrayList<>();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(resource + " 를 찾을 수 없습니다.");
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("sido,")) {
                    continue;
                }
                String[] cells = trimmed.split(",", -1);
                if (cells.length < 6) {
                    throw new IllegalStateException(resource + " 의 칸이 모자랍니다: " + trimmed);
                }
                rows.add(new WeatherArea(cells[0].strip(), cells[1].strip(),
                        Integer.parseInt(cells[2].strip()), Integer.parseInt(cells[3].strip()),
                        blankToNull(cells[4]), blankToNull(cells[5])));
            }
        } catch (IOException e) {
            throw new IllegalStateException(resource + " 를 읽지 못했습니다.", e);
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException(resource + " 에 줄이 없습니다.");
        }
        return List.copyOf(rows);
    }

    private static String blankToNull(String cell) {
        String value = cell.strip();
        return value.isEmpty() ? null : value;
    }
}
