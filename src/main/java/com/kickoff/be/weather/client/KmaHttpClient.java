package com.kickoff.be.weather.client;

import com.kickoff.be.weather.client.dto.DayTemperature;
import com.kickoff.be.weather.client.dto.HourSlot;
import com.kickoff.be.weather.client.dto.MidDay;
import com.kickoff.be.weather.client.dto.MidLandForecast;
import com.kickoff.be.weather.client.dto.MidTemperature;
import com.kickoff.be.weather.client.dto.ShortTermForecast;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 기상청 예보를 받아 온다 (계약서 §8-3).
 *
 * <b>주소를 직접 만들어 넘긴다.</b> 인증키(Decoding 키)에는 +, /, = 가 들어 있는데,
 * 문자열 URI 를 RestClient 에 넘기면 템플릿으로 보고 한 번 더 손대서 + 가 공백으로
 * 읽히는 키가 된다. 여기서 한 번만 인코딩하고 URI 로 넘겨 그 뒤로는 아무도 손대지
 * 않게 한다. (장소 검색에서 같은 자리를 한 번 밟았다 — KakaoPlaceSearchClient 주석.)
 *
 * <b>로그에 키를 적지 않는다.</b> 실패를 남길 때도 주소가 아니라 경로와 사유만 적는다.
 */
@Slf4j
@Component
public class KmaHttpClient implements KmaClient {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    /** 한 격자의 단기예보는 800줄 안팎이다 (항목 12종 x 시각 70칸 남짓). */
    private static final int SHORT_TERM_ROWS = 1000;
    private static final int MID_TERM_ROWS = 10;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WeatherProperties properties;
    private final RestClient restClient;

    /** 인증 실패는 기동 뒤 한 번만 분명히 남긴다 (계약서 §8-3). 주기마다 찍으면 묻힌다. */
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    public KmaHttpClient(WeatherProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        log.info("기상청 인증키 {} — 입구 {}",
                properties.isConfigured() ? "설정됨" : "없음 (weather 는 전부 null 이다)",
                properties.provider());
    }

    @Override
    public Optional<ShortTermForecast> shortTerm(int nx, int ny, OffsetDateTime baseAt) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("base_date", DATE.format(baseAt));
        query.put("base_time", TIME.format(baseAt));
        query.put("nx", nx);
        query.put("ny", ny);
        List<Map<String, Object>> items =
                call("VilageFcstInfoService_2.0/getVilageFcst", SHORT_TERM_ROWS, query);
        return parseShortTerm(items, baseAt);
    }

    /**
     * 응답 줄을 보관 형태로 옮긴다. 호출과 떼어 둔 것은 <b>해석을 테스트가 못박기</b>
     * 위해서다 — 항목 이름 하나가 바뀌면 조용히 빈 예보가 되는 자리다.
     */
    static Optional<ShortTermForecast> parseShortTerm(List<Map<String, Object>> items,
                                                      OffsetDateTime baseAt) {
        if (items == null || items.isEmpty()) {
            return Optional.empty();
        }
        Map<LocalDateTime, HourSlot> slots = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            LocalDateTime at = slotTime(text(item, "fcstDate"), text(item, "fcstTime"));
            Integer value = number(text(item, "fcstValue"));
            if (at == null || value == null) {
                continue;
            }
            HourSlot slot = slots.getOrDefault(at, HourSlot.empty());
            String category = text(item, "category");
            slots.put(at, switch (category == null ? "" : category) {
                case "SKY" -> slot.withSky(value);
                case "PTY" -> slot.withPty(value);
                case "TMP" -> slot.withTemperature(value);
                case "POP" -> slot.withPop(value);
                default -> slot;
            });
        }
        return slots.isEmpty() ? Optional.empty()
                : Optional.of(new ShortTermForecast(baseAt, slots));
    }

    @Override
    public Optional<MidLandForecast> midLand(String regId, OffsetDateTime tmFc) {
        return parseMidLand(midCall("MidFcstInfoService/getMidLandFcst", regId, tmFc), tmFc);
    }

    /** 응답 한 줄을 날짜별로 푼다. 해석을 테스트가 못박을 수 있게 호출과 떼어 둔다. */
    static Optional<MidLandForecast> parseMidLand(List<Map<String, Object>> items,
                                                  OffsetDateTime tmFc) {
        if (items == null || items.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> item = items.get(0);
        Map<LocalDate, MidDay> days = new LinkedHashMap<>();
        LocalDate announced = tmFc.toLocalDate();
        // 3~7일은 오전·오후가 따로 오고, 끝의 8~10일은 하루 하나다.
        for (int day = 3; day <= 7; day++) {
            String am = text(item, "wf" + day + "Am");
            String pm = text(item, "wf" + day + "Pm");
            if (am == null && pm == null) {
                continue;
            }
            days.put(announced.plusDays(day), new MidDay(am, pm,
                    number(text(item, "rnSt" + day + "Am")),
                    number(text(item, "rnSt" + day + "Pm"))));
        }
        for (int day = 8; day <= 10; day++) {
            String wf = text(item, "wf" + day);
            if (wf == null) {
                continue;
            }
            days.put(announced.plusDays(day),
                    MidDay.wholeDay(wf, number(text(item, "rnSt" + day))));
        }
        return days.isEmpty() ? Optional.empty() : Optional.of(new MidLandForecast(tmFc, days));
    }

    @Override
    public Optional<MidTemperature> midTemperature(String regId, OffsetDateTime tmFc) {
        return parseMidTemperature(midCall("MidFcstInfoService/getMidTa", regId, tmFc), tmFc);
    }

    static Optional<MidTemperature> parseMidTemperature(List<Map<String, Object>> items,
                                                        OffsetDateTime tmFc) {
        if (items == null || items.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> item = items.get(0);
        Map<LocalDate, DayTemperature> days = new LinkedHashMap<>();
        LocalDate announced = tmFc.toLocalDate();
        for (int day = 3; day <= 10; day++) {
            Integer min = number(text(item, "taMin" + day));
            Integer max = number(text(item, "taMax" + day));
            if (min == null && max == null) {
                continue;
            }
            days.put(announced.plusDays(day), new DayTemperature(min, max));
        }
        return days.isEmpty() ? Optional.empty() : Optional.of(new MidTemperature(tmFc, days));
    }

    private List<Map<String, Object>> midCall(String path, String regId, OffsetDateTime tmFc) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("regId", regId);
        query.put("tmFc", DATE.format(tmFc) + TIME.format(tmFc));
        return midTermRows(path, query);
    }

    private List<Map<String, Object>> midTermRows(String path, Map<String, Object> query) {
        return call(path, MID_TERM_ROWS, query);
    }

    /**
     * 한 번 부르고 items.item 을 꺼낸다. 어떤 이유로든 실패하면 null 이다 — 받아 오기는
     * 주기 작업이라 한 곳의 실패가 다음 주기에 저절로 복구된다 (계약서 §8-3).
     *
     * 빈 목록(List.of())과 null 은 다르다. 빈 목록은 "불렀는데 줄이 없다"이고 null 은
     * "부르지 못했다"다 — 보관분을 지울지 그대로 둘지가 갈린다.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> call(String path, int rows, Map<String, Object> query) {
        if (!properties.isConfigured()) {
            return null;
        }
        Map<String, Object> body;
        try {
            body = restClient.get().uri(uri(path, rows, query))
                    .header("accept", "application/json")
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException e) {
            // 인증 오류는 XML 로 오기도 해서 JSON 으로 읽다가 여기로 떨어진다.
            logFirstFailure(path, e.getMessage());
            return null;
        }
        if (body == null || !(body.get("response") instanceof Map<?, ?> response)) {
            logFirstFailure(path, "응답 모양이 다르다");
            return null;
        }
        String resultCode = response.get("header") instanceof Map<?, ?> header
                ? String.valueOf(header.get("resultCode")) : null;
        if (!"00".equals(resultCode)) {
            // 03 자료 없음, 22 한도 초과, 30·31 키 문제. 어느 쪽이든 이 호출만 건너뛴다.
            logFirstFailure(path, "resultCode=" + resultCode);
            return null;
        }
        if (!(response.get("body") instanceof Map<?, ?> payload)
                || !(payload.get("items") instanceof Map<?, ?> items)
                || !(items.get("item") instanceof List<?> list)) {
            return List.of();
        }
        return (List<Map<String, Object>>) list;
    }

    /**
     * 주소를 만든다. 인증키는 <b>여기서 한 번만</b> 인코딩한다 — 설정에는 Decoding 키가
     * 들어 있다는 전제다 (계약서 §8-3).
     */
    private URI uri(String path, int rows, Map<String, Object> query) {
        StringBuilder url = new StringBuilder(properties.provider().baseUrl()).append(path)
                .append('?').append(properties.provider().keyParam()).append('=')
                .append(URLEncoder.encode(properties.serviceKey(), StandardCharsets.UTF_8))
                .append("&pageNo=1&numOfRows=").append(rows).append("&dataType=JSON");
        query.forEach((name, value) -> url.append('&').append(name).append('=')
                .append(URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8)));
        return URI.create(url.toString());
    }

    /**
     * 처음 실패했을 때만 경고로 남긴다 (계약서 §8-3). 활용신청이 안 됐거나 키가 틀린
     * 상태는 주기마다 똑같이 실패하므로, 매번 찍으면 다른 로그를 덮는다.
     *
     * <b>키 값과 주소는 적지 않는다.</b> 주소에는 키가 들어 있다.
     */
    private void logFirstFailure(String path, String reason) {
        if (failureLogged.compareAndSet(false, true)) {
            log.warn("기상청 호출이 처음으로 실패했다 — {} ({}). 인증키 등록·활용신청과 "
                    + "kickoff.weather.provider 설정을 확인할 것", path, reason);
            return;
        }
        log.debug("기상청 호출 실패 — {} ({})", path, reason);
    }

    private static LocalDateTime slotTime(String date, String time) {
        if (date == null || time == null || date.length() != 8 || time.length() < 4) {
            return null;
        }
        try {
            return LocalDateTime.of(LocalDate.parse(date, DATE),
                    LocalTime.of(Integer.parseInt(time.substring(0, 2)),
                            Integer.parseInt(time.substring(2, 4))));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(Map<?, ?> item, String key) {
        Object value = item.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** 기상청은 숫자도 문자열로 준다. 숫자가 아닌 값이 섞여 오는 칸도 있다. */
    private static Integer number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(value.strip()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
