package com.kickoff.be.holiday.client;

import com.kickoff.be.holiday.dto.Holiday;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오 톡캘린더 「공휴일 및 주요 기념일 조회」 어댑터 (계약서 §8-2).
 *
 * {@code GET https://kapi.kakao.com/v2/api/calendar/holidays}, 헤더
 * {@code Authorization: KakaoAK {어드민 키}}. {@code holiday: true} 인 것만 공휴일로 본다 —
 * 이 API 는 국군의날·어버이날 같은 <b>기념일도 같이 주는데 그날들은 쉬는 날이 아니다</b>.
 *
 * <h2>한 해를 한 번에 받을 수 없다</h2>
 * {@code from}/{@code to} 는 UTC RFC3339 이고 <b>{@code to} 가 {@code from}+31일 이내</b>로
 * 제한된다(32일로 부르면 400 {@code code:-2} — 2026-11-10 에 실제로 확인했고 그 응답을
 * {@code kakao-error-period.json} 픽스처로 남겼다). 그래서 31일 이하 창으로 쪼개 여러 번
 * 부르고 합친다({@link #windows}). <b>31일은 받아 준다</b> — 우리 창은 상한에 붙어 있다.
 * 이웃 창을 <b>하루 이상 겹치게</b> 잡는 이유는 경계 누락이다 — 창을 딱 맞게 이어 붙이면
 * 경계에 걸친 연휴가 어느 창에도 안 들어오는 일이 생긴다. 겹친 만큼 같은 날이 두 번 오므로
 * {@code (date, name)} 으로 중복을 지운다({@link #dedupeAndSort}).
 *
 * 전체 범위는 <b>전년도 12/31 00:00Z ~ 다음 해 1/2 00:00Z</b>다. 카카오가 날짜를 UTC 자정으로
 * 주든 KST 자정으로 주든(KST 자정은 UTC 로는 전날 15:00) 요청한 해의 1월 1일과 12월 31일이
 * 빠지지 않게 하려는 것이다. 대신 요청 해가 아닌 날짜가 섞여 들어오므로 {@link #parse} 에서
 * 버린다.
 *
 * <h2>실패는 예외로 알린다. 빈 목록으로 뭉개지 않는다</h2>
 * 호출자가 "공휴일 0건"과 "못 가져왔다"를 구별해야 한다 — 그 구분이 응답의 {@code source} 가
 * 되고, FE 가 자기 계산값으로 폴백할지를 그걸로 정한다. <b>창 하나라도 실패하면 그 해 전체가
 * 실패</b>다(계약서 §8-2) — 부분 성공을 성공으로 내리면 2월만 빠진 달력을 정상이라 믿는다.
 *
 * <h2>타임아웃: 창당 3초, 한 해 전체 10초</h2>
 * 이 호출은 <b>요청 스레드에서</b> 일어난다(계약서 §8-2). 창이 열세 개쯤 되므로 창당 제한만
 * 두면 최악이 39초다. 그래서 전체 예산도 함께 둔다 — 넘기면 받아 온 것을 버리고 실패로
 * 올린다. 전역 {@code RestClient.Builder}(read 5s)를 쓰지 않고 전용 팩토리로 3초를 잡았다.
 *
 * <h2>로그에 어드민 키를 남기지 않는다</h2>
 * 키는 헤더에 있어 URL 에는 안 실리지만, 예외·오류 본문에 헤더가 섞여 나올 여지가 있다.
 * {@link #maskKey} 로 {@code KakaoAK} 뒤를 가린다(테스트로 고정).
 */
@Slf4j
@Component
public class KakaoHolidayClient {

    private static final String BASE_URL = "https://kapi.kakao.com/v2/api/calendar/holidays";

    /** 카카오 제한. {@code to} 는 {@code from}+31일 이내여야 한다. */
    private static final int WINDOW_DAYS = 31;

    /** 창 시작 간격. 31 이 아니라 30 이라서 이웃 창이 하루 겹친다(경계 누락 방지). */
    private static final int STEP_DAYS = 30;

    private static final Duration WINDOW_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration TOTAL_BUDGET = Duration.ofSeconds(10);

    /** 계약서가 {@code name} 을 최대 50자로 정했다. 넘는 제목은 실제로 없지만 약속은 지킨다. */
    private static final int MAX_NAME = 50;

    /**
     * 한 건이 펼쳐질 수 있는 최대 일수. 설 연휴도 사흘이라 서른 날이면 넉넉하다 —
     * {@code end_at} 이 이상한 값으로 와도 루프가 폭주하지 않게 두는 상한이다.
     */
    private static final int MAX_SPAN_DAYS = 30;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** RFC3339 UTC. {@code Instant.toString()} 에 기대지 않고 초까지 고정한다. */
    private static final DateTimeFormatter RFC3339 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HolidayProperties properties;
    private final RestClient restClient;

    public KakaoHolidayClient(HolidayProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(WINDOW_TIMEOUT);
        factory.setReadTimeout(WINDOW_TIMEOUT);
        // 전역 RestClient.Builder 를 쓰지 않는다 — 타임아웃을 이 호출만 3초로 두어야 한다.
        this.restClient = RestClient.builder().requestFactory(factory).build();
        // 키가 없는 것이 정상 동작의 하나라 기동을 막지 않는다. 그래서 기동 로그 한 줄이
        // 유일한 단서다 — 없으면 "달력에 임시공휴일이 안 칠해진다"를 눈으로 알 길이 없다.
        // 장소 검색 키와 같은 방식이다(KakaoPlaceSearchClient). 값 자체는 찍지 않는다.
        log.info("공휴일 키 {}", properties.isConfigured()
                ? "설정됨"
                : "없음 — 공휴일이 source NONE 으로 나간다");
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * 한 해의 공휴일을 전부 받아 온다.
     *
     * @return {@code date} 오름차순(같은 날은 응답에서 온 순서). 카카오가 그 해 데이터를
     *         아직 주지 않으면 <b>빈 목록</b>이다 — 호출자가 그것을 보고
     *         {@code source: NONE} 으로 내린다
     * @throws HolidayLookupException 키가 없거나, 창 하나라도 실패했거나, 전체 예산을
     *                                넘겼을 때
     */
    public List<Holiday> fetch(int year) {
        if (!properties.isConfigured()) {
            throw new HolidayLookupException("KAKAO_ADMIN_KEY 가 없다");
        }
        long deadline = nanoTime() + TOTAL_BUDGET.toNanos();
        List<Holiday> collected = new ArrayList<>();
        for (Window window : windows(year)) {
            if (nanoTime() > deadline) {
                // 남은 창을 포기하고 실패로 올린다. 여기까지 모은 것을 내리면 부분 성공이다.
                throw new HolidayLookupException("%d년 조회가 %d초 예산을 넘겼다 (%s 창에서 중단)"
                        .formatted(year, TOTAL_BUDGET.toSeconds(), RFC3339.format(window.from())));
            }
            collected.addAll(parse(call(window), year));
        }
        return dedupeAndSort(collected);
    }

    /**
     * 창 하나를 실제로 부른다.
     *
     * 메서드로 떼어 둔 이유는 테스트다 — 창 분할·합치기·부분 실패 처리를 네트워크 없이
     * 검증하려면 이 지점만 갈아 끼울 수 있어야 한다. {@code private} 이면 그게 안 된다.
     */
    String call(Window window) {
        try {
            // URI 를 직접 만든다. RestClient.uri(String) 은 문자열을 템플릿으로 본다.
            return restClient.get()
                    .uri(window.toUri())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.adminKey())
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new HolidayLookupException("톡캘린더 호출 실패 — " + maskKey(e.getMessage()));
        }
    }

    /** 사용자가 이 시계를 볼 일은 없다. 테스트가 예산 초과를 만들려고 덮어쓴다. */
    long nanoTime() {
        return System.nanoTime();
    }

    /**
     * 한 해를 덮는 31일 이하 창들. 이웃 창은 하루 겹치고, 마지막 창은 범위 끝에 맞춰 짧다.
     *
     * 범위를 전년도 12/31 ~ 다음 해 1/2 로 넓게 잡는 이유는 위 클래스 주석에 적었다 —
     * KST 자정이 UTC 로는 전날 15:00 이라, 해 경계의 하루가 통째로 빠질 수 있다.
     */
    static List<Window> windows(int year) {
        Instant start = utcMidnight(year - 1, 12, 31);
        Instant end = utcMidnight(year + 1, 1, 2);
        List<Window> windows = new ArrayList<>();
        Instant from = start;
        while (true) {
            Instant to = from.plus(WINDOW_DAYS, ChronoUnit.DAYS);
            if (!to.isBefore(end)) {
                windows.add(new Window(from, end));
                return List.copyOf(windows);
            }
            windows.add(new Window(from, to));
            from = from.plus(STEP_DAYS, ChronoUnit.DAYS);
        }
    }

    private static Instant utcMidnight(int year, int month, int day) {
        return OffsetDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();
    }

    /**
     * 응답 한 장을 공휴일 목록으로 옮긴다. 창 하나의 결과라 중복·정렬은 여기서 하지 않는다.
     *
     * 응답이 JSON 이 아니거나 {@code events} 가 없으면 <b>예외</b>다. 조용히 0건으로 넘기면
     * "성공한 0건"이 되어 달력에서 공휴일이 사라진 채 아무도 모른다.
     */
    static List<Holiday> parse(String body, int year) {
        if (body == null || body.isBlank()) {
            throw new HolidayLookupException("응답이 비어 있다");
        }
        Map<?, ?> root;
        try {
            root = MAPPER.readValue(body, Map.class);
        } catch (RuntimeException e) {
            throw new HolidayLookupException("응답을 JSON 으로 읽지 못했다 — " + head(body));
        }
        if (!(root.get("events") instanceof List<?> events)) {
            // 오류 응답({"msg":...,"code":-401})도 여기로 떨어진다. 실패로 올려야 한다.
            throw new HolidayLookupException("응답에 events 배열이 없다 — " + head(body));
        }
        List<Holiday> holidays = new ArrayList<>();
        events.forEach(event -> addIfHoliday(holidays, event, year));
        return holidays;
    }

    /** 겹친 창이 만든 중복을 지우고 날짜 오름차순으로 정렬한다. 같은 날의 순서는 유지된다. */
    static List<Holiday> dedupeAndSort(List<Holiday> raw) {
        // Holiday 는 레코드라 equals 가 (date, name) 이다 — 계약서가 말한 중복 제거 키 그대로.
        List<Holiday> unique = new ArrayList<>(new LinkedHashSet<>(raw));
        // List.sort 는 안정 정렬이다. 같은 날에 이름이 둘이면 응답에서 온 순서가 남는다.
        unique.sort(Comparator.comparing(Holiday::date));
        return List.copyOf(unique);
    }

    private static void addIfHoliday(List<Holiday> holidays, Object raw, int year) {
        if (!(raw instanceof Map<?, ?> event)) {
            return;
        }
        // 기념일(국군의날·어버이날 등)은 holiday:false 로 같이 온다. 쉬는 날만 담는다.
        if (!Boolean.TRUE.equals(event.get("holiday"))) {
            return;
        }
        String name = clampName(text(event.get("title")));
        if (name == null) {
            return;
        }
        if (!(event.get("time") instanceof Map<?, ?> time)) {
            return;
        }
        Instant start = instant(text(time.get("start_at")));
        if (start == null) {
            return;
        }
        for (LocalDate date : days(start, instant(text(time.get("end_at"))))) {
            if (date.getYear() == year) {
                // 요청한 해만 담는 것이 응답의 약속이다. 넓게 부른 탓에 경계의 다른 해가 온다.
                holidays.add(new Holiday(date, name));
            }
        }
    }

    /**
     * 한 건이 덮는 한국 날짜들.
     *
     * <b>실응답은 연휴도 하루씩 끊어서 준다</b>(2026-11-10 캡처: 설날연휴 2/16, 설날 2/17,
     * 설날연휴 2/18 — 세 건). 그래서 펼칠 일이 실제로는 없다. 그래도 계약서 §8-2 가
     * "{@code end_at - start_at} 이 하루를 넘으면 그 범위 매일로 펼침"을 요구하므로 방어
     * 코드로 남긴다 — 한 건으로 묶여 오기 시작하면 그대로 두면 달력에 첫날만 빨갛다.
     *
     * <b>날짜를 길이로 센다.</b> 첫날은 {@code start_at} 을 KST 로 바꾼 날이고, 그 뒤로
     * {@code (end_at - start_at)} 일수만큼 이어 붙인다. {@code end_at} 을 KST 날짜로 바꿔
     * 끝을 잡지 않는 이유는 자정 기준이 두 가지이기 때문이다 — <b>실응답은 UTC
     * 자정</b>({@code 2026-02-16T00:00:00Z})이고 <b>카카오 문서 예시는 KST
     * 자정</b>({@code 2022-10-02T15:00:00Z})이다. 날짜로 끝을 잡으면 UTC 자정 쪽 하루가
     * 이틀이 된다. 길이로 세면 둘 다 같은 답이 나온다.
     */
    private static List<LocalDate> days(Instant start, Instant end) {
        LocalDate first = LocalDate.ofInstant(start, KST);
        if (end == null || !end.isAfter(start)) {
            return List.of(first);
        }
        long span = Duration.between(start, end).toDays();
        if (span <= 1) {
            // 하루짜리다. 종일 일정은 end_at 이 다음 자정이라 정확히 1일로 온다.
            return List.of(first);
        }
        // 이상한 end_at 로 루프가 폭주하지 않게 자른다.
        long length = Math.min(span, MAX_SPAN_DAYS);
        List<LocalDate> days = new ArrayList<>();
        for (long offset = 0; offset < length; offset++) {
            days.add(first.plusDays(offset));
        }
        return days;
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String clampName(String name) {
        if (name == null) {
            return null;
        }
        return name.length() <= MAX_NAME ? name : name.substring(0, MAX_NAME);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).strip();
        return s.isEmpty() ? null : s;
    }

    private static String head(String body) {
        String masked = maskKey(body);
        return masked.length() <= 200 ? masked : masked.substring(0, 200) + "…";
    }

    /**
     * 로그에 어드민 키가 섞이지 않게 한다. 키는 헤더에 있어 URL 에는 안 실리지만, 예외
     * 메시지나 오류 본문에 요청 헤더가 묻어 나오는 경우를 막는다.
     */
    static String maskKey(String text) {
        return text == null ? "" : text.replaceAll("(?i)(KakaoAK\\s+)[^\\s\"',}&]+", "$1***");
    }

    /**
     * 호출할 한 구간. {@code to - from} 은 항상 31일 이하다(카카오 제한).
     *
     * 레코드로 둔 이유는 테스트다 — 창 분할만 떼어 검증할 수 있다.
     */
    record Window(Instant from, Instant to) {

        URI toUri() {
            return URI.create("%s?from=%s&to=%s"
                    .formatted(BASE_URL, RFC3339.format(from), RFC3339.format(to)));
        }
    }
}
