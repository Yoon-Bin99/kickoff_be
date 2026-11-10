package com.kickoff.be.holiday.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kickoff.be.holiday.client.KakaoHolidayClient.Window;
import com.kickoff.be.holiday.dto.Holiday;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 카카오 톡캘린더 응답 파싱·창 분할·합치기 (계약서 §8-2).
 *
 * <b>이 테스트가 증명하는 것과 증명하지 못하는 것.</b> 필드 이름과 값의 생김새
 * ({@code events[].time.start_at} 이 UTC RFC3339, {@code holiday} 가 boolean, 종일 일정의
 * {@code start_at} 이 KST 자정 = UTC 전날 15:00)는 카카오 톡캘린더 문서의 예시 그대로다.
 * 다만 <b>픽스처는 그 형식에 맞춰 쓴 것이고 실제 응답을 캡처한 것이 아니다</b> — 어드민
 * 키가 없어 호출할 수 없다. 그래서 "우리 파서가 이 형식을 제대로 읽는다"는 증명하지만
 * "카카오가 정말 이 값을 준다"는 증명하지 않는다. 키가 생기면 실응답 한 장으로 픽스처를
 * 갈아 끼울 것.
 *
 * 네트워크는 타지 않는다. {@link KakaoHolidayClient#call} 만 갈아 끼워 창 분할·중복 제거·
 * 부분 실패 처리를 본다 — 같은 패키지라 package-private 가 그대로 보인다(운영 코드의
 * 가시성을 넓히지 않으려고 테스트를 이 패키지에 뒀다).
 */
class KakaoHolidayClientTest {

    // ───────────────────────── 파싱 ─────────────────────────

    /**
     * 이 API 는 기념일도 같이 준다. 국군의날(10-01)은 {@code holiday: false} 라 쉬는 날이
     * 아니고, 개천절·한글날만 빨간날이다.
     */
    @Test
    @DisplayName("holiday:true 만 공휴일로 본다 — 기념일은 버린다")
    void onlyRealHolidays() throws IOException {
        List<Holiday> holidays =
                KakaoHolidayClient.parse(fixture("kakao-holidays-2022-10.json"), 2022);

        assertThat(holidays).extracting(Holiday::name)
                .containsExactly("개천절", "한글날")
                .doesNotContain("국군의 날");
        assertThat(holidays).extracting(Holiday::date)
                .containsExactly(LocalDate.of(2022, 10, 3), LocalDate.of(2022, 10, 9));
    }

    /**
     * 연휴는 <b>한 건으로</b> 온다. 펼치지 않으면 달력에 설날 첫날만 빨갛다.
     * 2026-02-15T15:00Z ~ 02-18T15:00Z = KST 02-16 00:00 ~ 02-19 00:00, 사흘이다.
     */
    @Test
    @DisplayName("연휴 한 건을 매일로 펼친다")
    void expandsMultiDayHoliday() throws IOException {
        List<Holiday> holidays =
                KakaoHolidayClient.parse(fixture("kakao-holidays-lunar-new-year.json"), 2026);

        assertThat(holidays).extracting(Holiday::date).containsExactly(
                LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17), LocalDate.of(2026, 2, 18));
        assertThat(holidays).extracting(Holiday::name).containsOnly("설날");
    }

    /**
     * 요청 범위를 전년도 12/31 ~ 다음 해 1/2 로 넓게 잡기 때문에 다른 해가 섞여 온다.
     * 그대로 담으면 2026년 달력에 2025-12-25 가 찍힌다.
     */
    @Test
    @DisplayName("요청한 해가 아닌 날짜는 버린다")
    void dropsOtherYears() throws IOException {
        List<Holiday> holidays =
                KakaoHolidayClient.parse(fixture("kakao-holidays-boundary.json"), 2026);

        assertThat(holidays).extracting(Holiday::date).allMatch(date -> date.getYear() == 2026);
        assertThat(holidays).extracting(Holiday::name).doesNotContain("기독탄신일");
    }

    /**
     * 자정 기준이 두 가지다. KST 자정(신정: UTC 전날 15:00)과 UTC 자정(임시공휴일) 둘 다
     * 하루로 읽혀야 한다 — {@code end_at} 을 KST 날짜로 바꿔 끝을 잡으면 UTC 자정 쪽이
     * 이틀이 된다. 그래서 길이(end-start)로 센다.
     */
    @Test
    @DisplayName("KST 자정·UTC 자정 어느 쪽으로 와도 하루는 하루다")
    void handlesBothMidnightConventions() throws IOException {
        List<Holiday> holidays =
                KakaoHolidayClient.parse(fixture("kakao-holidays-boundary.json"), 2026);

        assertThat(holidays).containsExactly(
                new Holiday(LocalDate.of(2026, 1, 1), "신정"),
                new Holiday(LocalDate.of(2026, 1, 2), "임시공휴일"));
    }

    /**
     * 0건이면 {@code events} 가 빈 배열이다. <b>예외가 아니라 빈 목록이다</b> — 호출자가
     * 그걸 보고 {@code source: NONE} 으로 내린다. 여기서 예외를 던지면 "호출 실패"와
     * "카카오에 데이터 없음"이 구별되지 않는다(응답은 어차피 같지만 로그가 달라진다).
     */
    @Test
    @DisplayName("events 가 빈 배열이면 빈 목록이다 — 예외가 아니다")
    void emptyEventsIsEmptyList() throws IOException {
        assertThat(KakaoHolidayClient.parse(fixture("kakao-holidays-empty.json"), 2030)).isEmpty();
    }

    /**
     * 어드민 키 오류는 {@code {"msg":...,"code":-401}} 로 온다. 조용히 0건으로 넘기면
     * "성공한 0건"으로 보여서 달력에 공휴일이 사라진 채 아무도 모른다.
     */
    @Test
    @DisplayName("오류 응답은 예외다 — 빈 목록이 아니다")
    void errorBodyThrows() throws IOException {
        assertThatThrownBy(() -> KakaoHolidayClient.parse(fixture("kakao-error.json"), 2026))
                .isInstanceOf(HolidayLookupException.class);
    }

    @Test
    @DisplayName("빈 응답·JSON 아닌 응답은 예외다")
    void emptyOrBrokenBodyThrows() {
        assertThatThrownBy(() -> KakaoHolidayClient.parse("", 2026))
                .isInstanceOf(HolidayLookupException.class);
        assertThatThrownBy(() -> KakaoHolidayClient.parse(null, 2026))
                .isInstanceOf(HolidayLookupException.class);
        assertThatThrownBy(() -> KakaoHolidayClient.parse("<html>502</html>", 2026))
                .isInstanceOf(HolidayLookupException.class);
    }

    // ───────────────────────── 창 분할 ─────────────────────────

    /**
     * 카카오가 {@code to <= from + 31일} 로 제한한다. 넘기면 그 창이 오류가 되고, 창 하나가
     * 실패하면 그 해 전체가 NONE 이다.
     */
    @Test
    @DisplayName("모든 창이 31일 이내다 — 카카오 제한")
    void everyWindowIsWithin31Days() {
        for (Window window : KakaoHolidayClient.windows(2026)) {
            assertThat(Duration.between(window.from(), window.to()))
                    .as("%s ~ %s", window.from(), window.to())
                    .isLessThanOrEqualTo(Duration.ofDays(31));
        }
    }

    /**
     * 이웃 창은 하루 이상 겹쳐야 한다. 딱 맞게 이어 붙이면 경계에 걸친 연휴가 어느 창에도
     * 안 들어오는 일이 생긴다 (계약서 §8-2).
     */
    @Test
    @DisplayName("이웃 창이 하루 이상 겹친다 — 경계 누락 방지")
    void neighbouringWindowsOverlap() {
        List<Window> windows = KakaoHolidayClient.windows(2026);

        assertThat(windows).hasSizeGreaterThan(1);
        for (int i = 1; i < windows.size(); i++) {
            Instant previousTo = windows.get(i - 1).to();
            Instant currentFrom = windows.get(i).from();
            assertThat(Duration.between(currentFrom, previousTo))
                    .as("%d번째 창이 이전 창과 겹치는 길이", i)
                    .isGreaterThanOrEqualTo(Duration.ofDays(1));
        }
    }

    /**
     * 전체 범위는 전년도 12/31 00:00Z ~ 다음 해 1/2 00:00Z 여야 한다. 자정 기준이 어느
     * 쪽이든(KST 자정은 UTC 로는 전날 15:00) 요청한 해의 1/1 과 12/31 이 들어오게 하는
     * 여유다.
     */
    @Test
    @DisplayName("전체 범위가 전년도 12/31 ~ 다음 해 1/2 를 덮는다")
    void windowsCoverTheWholeYearWithMargin() {
        List<Window> windows = KakaoHolidayClient.windows(2026);

        assertThat(windows.get(0).from()).isEqualTo(Instant.parse("2025-12-31T00:00:00Z"));
        assertThat(windows.get(windows.size() - 1).to())
                .isEqualTo(Instant.parse("2027-01-02T00:00:00Z"));
    }

    @Test
    @DisplayName("창 사이에 빈틈이 없다")
    void windowsHaveNoGap() {
        List<Window> windows = KakaoHolidayClient.windows(2026);

        for (int i = 1; i < windows.size(); i++) {
            assertThat(windows.get(i).from())
                    .as("%d번째 창의 시작", i)
                    .isBefore(windows.get(i - 1).to());
        }
    }

    /** 경계 연도에서도 터지지 않아야 한다 — 컨트롤러가 1900~2200 을 받는다. */
    @Test
    @DisplayName("경계 연도(1900·2200)도 창으로 쪼개진다")
    void boundaryYearsSplit() {
        assertThat(KakaoHolidayClient.windows(1900)).isNotEmpty();
        assertThat(KakaoHolidayClient.windows(2200)).isNotEmpty();
    }

    @Test
    @DisplayName("창 URI 에 from·to 가 UTC RFC3339 로 들어간다")
    void windowUriIsRfc3339() {
        Window window = new Window(
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"));

        assertThat(window.toUri().toString())
                .isEqualTo("https://kapi.kakao.com/v2/api/calendar/holidays"
                        + "?from=2026-01-01T00:00:00Z&to=2026-02-01T00:00:00Z");
    }

    // ───────────────────────── 합치기 ─────────────────────────

    /** 창이 겹치므로 같은 날이 두 번 온다. {@code (date, name)} 으로 지운다. */
    @Test
    @DisplayName("같은 (날짜, 이름)은 한 번만 남는다")
    void dedupesByDateAndName() {
        Holiday newYear = new Holiday(LocalDate.of(2026, 1, 1), "신정");

        List<Holiday> merged = KakaoHolidayClient.dedupeAndSort(List.of(newYear, newYear, newYear));

        assertThat(merged).containsExactly(newYear);
    }

    /** 같은 날에 이름이 둘이면 둘 다 남고 순서도 유지된다 (계약서 §8-2). */
    @Test
    @DisplayName("날짜 오름차순으로 정렬하고, 같은 날의 순서는 유지한다")
    void sortsByDateKeepingSameDayOrder() {
        Holiday later = new Holiday(LocalDate.of(2026, 3, 1), "삼일절");
        Holiday firstOfTwo = new Holiday(LocalDate.of(2026, 1, 1), "신정");
        Holiday secondOfTwo = new Holiday(LocalDate.of(2026, 1, 1), "임시공휴일");

        List<Holiday> merged =
                KakaoHolidayClient.dedupeAndSort(List.of(later, firstOfTwo, secondOfTwo));

        assertThat(merged).containsExactly(firstOfTwo, secondOfTwo, later);
    }

    // ───────────────────────── 전체 호출 ─────────────────────────

    /**
     * 창을 전부 불러 합친다. 모든 창이 같은 응답을 주면 중복이 지워져 <b>한 벌</b>만 남아야
     * 한다 — 겹침이 만든 중복이 응답으로 새어 나가지 않는지 보는 것이다.
     */
    @Test
    @DisplayName("창을 전부 불러 합치고 중복을 지운다")
    void fetchMergesEveryWindow() throws IOException {
        String body = fixture("kakao-holidays-lunar-new-year.json");
        StubClient client = StubClient.returning(window -> body);

        List<Holiday> holidays = client.fetch(2026);

        assertThat(client.calls).hasValueGreaterThan(1);
        assertThat(holidays).extracting(Holiday::date).containsExactly(
                LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17), LocalDate.of(2026, 2, 18));
    }

    /**
     * <b>창 하나라도 실패하면 그 해 전체가 실패</b>다 (계약서 §8-2). 받아 온 창만 내리면
     * 2월만 빠진 달력을 FE 가 정상이라 믿는다 — 서비스는 이 예외를 받아 NONE 으로 바꾸고,
     * FE 는 자기 계산값으로 폴백한다.
     */
    @Test
    @DisplayName("창 하나가 실패하면 전체가 실패다 — 부분 성공을 내리지 않는다")
    void oneFailedWindowFailsTheYear() throws IOException {
        String body = fixture("kakao-holidays-lunar-new-year.json");
        AtomicInteger seen = new AtomicInteger();
        StubClient partial = StubClient.returning(window -> {
            if (seen.incrementAndGet() == 1) {
                return body;
            }
            throw new HolidayLookupException("두 번째 창이 죽었다");
        });

        assertThatThrownBy(() -> partial.fetch(2026))
                .isInstanceOf(HolidayLookupException.class);
        assertThat(partial.calls).as("실패한 창에서 멈춘다").hasValue(2);
    }

    /**
     * 창당 3초만 두면 열세 개가 최악 39초다. 요청 스레드에서 도는 호출이라 전체 예산을
     * 넘기면 포기한다 — 받아 온 것을 내리면 그게 부분 성공이 된다.
     */
    @Test
    @DisplayName("한 해 전체 10초를 넘기면 실패다")
    void totalBudgetIsEnforced() throws IOException {
        String body = fixture("kakao-holidays-lunar-new-year.json");
        StubClient client = StubClient.returning(window -> body);
        // 창마다 4초가 흐른 것처럼 꾸민다. 세 창이면 예산을 넘긴다.
        client.nanosPerCall = Duration.ofSeconds(4).toNanos();

        assertThatThrownBy(() -> client.fetch(2026))
                .isInstanceOf(HolidayLookupException.class)
                .hasMessageContaining("예산");
        // 0초·4초·8초에 세 번 부르고, 12초가 되면 남은 창을 포기한다.
        assertThat(client.calls).as("예산을 넘긴 뒤로는 부르지 않는다").hasValue(3);
    }

    @Test
    @DisplayName("키가 없으면 부르지도 않고 실패다")
    void withoutKeyFails() {
        StubClient client = new StubClient(new HolidayProperties(""), window -> "{\"events\":[]}");

        assertThatThrownBy(() -> client.fetch(2026))
                .isInstanceOf(HolidayLookupException.class)
                .hasMessageContaining("KAKAO_ADMIN_KEY");
        assertThat(client.calls).hasValue(0);
    }

    // ───────────────────────── 로그 ─────────────────────────

    /**
     * 어드민 키는 헤더에 있어 URL 에는 안 실리지만, 예외 메시지나 오류 본문에 요청 헤더가
     * 묻어 나오는 경우가 있다. <b>비밀값</b>이라 로그에 남으면 안 된다 (계약서 §8-2).
     */
    @Test
    @DisplayName("로그용 문자열에서 어드민 키가 가려진다")
    void adminKeyIsMasked() {
        String raw = "I/O error on GET request: headers=[Authorization:\"KakaoAK "
                + "abcd1234SECRETADMINKEY\"], timeout";

        String masked = KakaoHolidayClient.maskKey(raw);

        assertThat(masked).doesNotContain("abcd1234SECRETADMINKEY");
        assertThat(masked).contains("KakaoAK ***");
        // 나머지는 남아 있어야 원인을 읽을 수 있다
        assertThat(masked).contains("timeout");
    }

    // ───────────────────────── 거푸집 ─────────────────────────

    /** {@link KakaoHolidayClient#call} 만 갈아 끼운다. 네트워크를 타지 않는다. */
    private static final class StubClient extends KakaoHolidayClient {

        private interface Responder {
            String respond(Window window);
        }

        private final Responder responder;
        private final AtomicInteger calls = new AtomicInteger();
        private long nanosPerCall;
        private long now;

        private StubClient(HolidayProperties properties, Responder responder) {
            super(properties);
            this.responder = responder;
        }

        static StubClient returning(Responder responder) {
            return new StubClient(new HolidayProperties("test-admin-key"), responder);
        }

        @Override
        String call(Window window) {
            calls.incrementAndGet();

            now += nanosPerCall;
            return responder.respond(window);
        }

        @Override
        long nanoTime() {
            return now;
        }
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("fixtures/" + name)) {
            assertThat(in).as("픽스처 %s 가 있어야 한다", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
