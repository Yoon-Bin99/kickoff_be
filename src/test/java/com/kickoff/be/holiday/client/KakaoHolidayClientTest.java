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
 * <h2>픽스처는 실응답 캡처다 (2026-11-10)</h2>
 * {@code kakao-holidays-2026-*.json}, {@code kakao-holidays-empty.json},
 * {@code kakao-error-period.json} 은 <b>실제 호출 응답을 그대로 넣은 것</b>이다(공백만
 * 정리했고 필드·값·순서는 원문 그대로). 그래서 "우리 파서가 이 형식을 읽는다"뿐 아니라
 * "카카오가 정말 이 값을 준다"까지 고정한다. 캡처한 창은 파일마다 아래 테스트 주석에
 * 적었다.
 *
 * 예외가 둘 있다.
 * <ul>
 *   <li>{@code kakao-holidays-2022-10.json} — 카카오 <b>문서의 예시</b>다. 실응답과 자정
 *       기준이 달라서(아래) 일부러 남겨 뒀다</li>
 *   <li>연휴를 한 건으로 주는 경우 — <b>실응답에는 없다</b>. 계약서가 대비하라고 한
 *       경우라 방어 코드만 유지하고, 그 테스트만 인라인 JSON 으로 둔다</li>
 * </ul>
 *
 * <h2>실응답에서 알게 된 두 가지</h2>
 * ① <b>카카오는 UTC 자정으로 준다</b>({@code start_at: "2026-02-16T00:00:00Z"}). 문서 예시는
 * KST 자정(UTC 전날 15:00)이라 둘이 다르다 — 그래서 끝을 날짜가 아니라 길이로 세는 쪽이
 * 맞았다. 두 모양을 각각 테스트로 고정한다.
 * ② <b>연휴가 한 건이 아니라 날짜별 개별 건으로 온다</b>(설날연휴 2/16, 설날 2/17,
 * 설날연휴 2/18 — 세 건). 펼칠 일이 실제로는 없다.
 *
 * 네트워크는 타지 않는다. {@link KakaoHolidayClient#call} 만 갈아 끼워 창 분할·중복 제거·
 * 부분 실패 처리를 본다 — 같은 패키지라 package-private 가 그대로 보인다(운영 코드의
 * 가시성을 넓히지 않으려고 테스트를 이 패키지에 뒀다).
 */
class KakaoHolidayClientTest {

    // ───────────────────────── 파싱 (실응답) ─────────────────────────

    /**
     * 캡처 창: 2026-02-10 ~ 2026-03-05. 이 창에 <b>정월대보름(3/3)</b>이 {@code holiday:false}
     * 로 함께 왔다 — 쉬는 날이 아니라서 빼야 한다. 기념일이 섞여 온다는 것을 실응답으로
     * 증명하는 자리다.
     */
    @Test
    @DisplayName("holiday:true 만 공휴일로 본다 — 기념일(정월대보름)은 버린다")
    void onlyRealHolidays() throws IOException {
        List<Holiday> holidays =
                KakaoHolidayClient.parse(fixture("kakao-holidays-2026-02.json"), 2026);

        assertThat(holidays).extracting(Holiday::name).doesNotContain("정월대보름");
        assertThat(holidays).containsExactly(
                new Holiday(LocalDate.of(2026, 2, 16), "설날연휴"),
                new Holiday(LocalDate.of(2026, 2, 17), "설날"),
                new Holiday(LocalDate.of(2026, 2, 18), "설날연휴"),
                new Holiday(LocalDate.of(2026, 3, 1), "삼일절"),
                new Holiday(LocalDate.of(2026, 3, 2), "대체휴일"));
    }

    /**
     * <b>연휴는 한 건이 아니라 하루씩 세 건으로 온다.</b> 그래서 펼치지 않아도 2/16·17·18 이
     * 다 나온다. 같은 이름("설날연휴")이 다른 날에 두 번 오는데, 중복 제거 키가
     * {@code (date, name)} 이라 날짜가 다르면 둘 다 남는다 — 이름만으로 지우면 2/18 이
     * 사라진다.
     */
    @Test
    @DisplayName("연휴가 날짜별 개별 건으로 와도 사흘이 다 남는다")
    void consecutiveHolidaysArriveAsSeparateEvents() throws IOException {
        List<Holiday> holidays =
                KakaoHolidayClient.parse(fixture("kakao-holidays-2026-02.json"), 2026);

        assertThat(holidays).filteredOn(holiday -> holiday.name().equals("설날연휴"))
                .extracting(Holiday::date)
                .containsExactly(LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 18));
    }

    /**
     * 캡처 창: 2026-05-20 ~ 2026-06-10. <b>지방선거(6/3)</b>가 임시공휴일이다 — 이 기능이
     * 존재하는 이유 그 자체다. FE 의 계산값(법정 규칙)으로는 알 수 없는 날이라, 서버가
     * 받아 오지 않으면 그날 달력이 틀린다 (계약서 §8-2).
     *
     * 대체휴일(5/25)도 같은 창에 있다 — 이름이 "부처님오신날 대체휴일"이 아니라 그냥
     * "대체휴일"로 온다. 원문을 고치지 않는 것이 약속이라 그대로 내린다.
     */
    @Test
    @DisplayName("임시공휴일(지방선거)·대체휴일도 그대로 받는다")
    void temporaryAndSubstituteHolidays() throws IOException {
        List<Holiday> holidays =
                KakaoHolidayClient.parse(fixture("kakao-holidays-2026-06.json"), 2026);

        assertThat(holidays).containsExactly(
                new Holiday(LocalDate.of(2026, 5, 24), "부처님오신날"),
                new Holiday(LocalDate.of(2026, 5, 25), "대체휴일"),
                new Holiday(LocalDate.of(2026, 6, 3), "지방선거"),
                new Holiday(LocalDate.of(2026, 6, 6), "현충일"));
    }

    /**
     * 캡처 창: 2026-12-20 ~ 2027-01-02. <b>마지막 창이 실제로 다음 해를 물고 온다</b> —
     * 우리가 전체 범위를 다음 해 1/2 까지 넓게 잡기 때문이다(계약서 §8-2). 버리지 않으면
     * 2026년 달력에 2027-01-01 이 찍힌다.
     */
    @Test
    @DisplayName("요청한 해가 아닌 날짜는 버린다 — 마지막 창의 다음 해 새해")
    void dropsOtherYears() throws IOException {
        String body = fixture("kakao-holidays-2026-12.json");

        assertThat(KakaoHolidayClient.parse(body, 2026))
                .containsExactly(new Holiday(LocalDate.of(2026, 12, 25), "크리스마스"));
        assertThat(KakaoHolidayClient.parse(body, 2027))
                .containsExactly(new Holiday(LocalDate.of(2027, 1, 1), "새해"));
    }

    /**
     * 자정 기준이 두 가지다. <b>실응답은 UTC 자정</b>(2026-02-16T00:00:00Z)이고
     * <b>카카오 문서 예시는 KST 자정</b>(2022-10-02T15:00:00Z = 10-03 00:00+09)이다.
     * 어느 쪽이든 하루는 하루로 읽혀야 한다 — {@code end_at} 을 KST 날짜로 바꿔 끝을
     * 잡으면 UTC 자정 쪽이 이틀이 된다. 그래서 길이(end-start)로 센다.
     */
    @Test
    @DisplayName("UTC 자정(실응답)·KST 자정(문서 예시) 어느 쪽이든 하루는 하루다")
    void handlesBothMidnightConventions() throws IOException {
        // 실응답: start_at 이 2026-02-17T00:00:00Z → KST 2026-02-17 09:00 → 2026-02-17
        assertThat(KakaoHolidayClient.parse(fixture("kakao-holidays-2026-02.json"), 2026))
                .contains(new Holiday(LocalDate.of(2026, 2, 17), "설날"));

        // 문서 예시: start_at 이 2022-10-02T15:00:00Z → KST 2022-10-03 00:00 → 2022-10-03
        assertThat(KakaoHolidayClient.parse(fixture("kakao-holidays-2022-10.json"), 2022))
                .containsExactly(
                        new Holiday(LocalDate.of(2022, 10, 3), "개천절"),
                        new Holiday(LocalDate.of(2022, 10, 9), "한글날"));
    }

    /**
     * 문서 예시에서도 기념일이 걸러져야 한다 — 국군의 날(10-01)이 {@code holiday:false} 다.
     * 실응답(정월대보름)과 같은 규칙이고, 자정 기준만 다르다.
     */
    @Test
    @DisplayName("문서 예시 모양에서도 holiday:false 는 버린다")
    void documentedShapeFiltersAnniversaries() throws IOException {
        assertThat(KakaoHolidayClient.parse(fixture("kakao-holidays-2022-10.json"), 2022))
                .extracting(Holiday::name).doesNotContain("국군의 날");
    }

    /**
     * <b>실응답에는 이 모양이 오지 않는다.</b> 캡처해 보니 연휴가 날짜별 개별 건으로 왔다
     * (위 {@code consecutiveHolidaysArriveAsSeparateEvents}). 그래도 계약서 §8-2 가
     * "{@code end_at - start_at} 이 하루를 넘으면 그 범위 매일로 펼침"을 요구하므로 방어
     * 코드를 유지하고, 그 코드가 동작한다는 것만 인라인 JSON 으로 고정한다 — 실캡처가
     * 아니라서 픽스처 파일로 두지 않았다.
     */
    @Test
    @DisplayName("연휴를 한 건으로 주는 경우도 펼친다 — 실응답에는 없는 방어 코드")
    void expandsMultiDayEventIfItEverArrives() {
        String body = """
                {"events":[{"id":"x","title":"설날연휴",
                "time":{"start_at":"2026-02-16T00:00:00Z","end_at":"2026-02-19T00:00:00Z",
                "all_day":true},"holiday":true}]}
                """;

        assertThat(KakaoHolidayClient.parse(body, 2026)).extracting(Holiday::date)
                .containsExactly(LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17),
                        LocalDate.of(2026, 2, 18));
    }

    /**
     * 캡처 창: 2026-07-20 ~ 2026-08-10 (공휴일도 기념일도 없는 구간). {@code events} 가 빈
     * 배열로 온다. <b>예외가 아니라 빈 목록이다</b> — 호출자가 그걸 보고
     * {@code source: NONE} 으로 내린다. 2060년처럼 API 상한을 넘은 해도 같은 모양이다
     * (2050은 데이터가 있고 2060은 비어 있는 것을 캡처 때 확인했다).
     */
    @Test
    @DisplayName("events 가 빈 배열이면 빈 목록이다 — 예외가 아니다")
    void emptyEventsIsEmptyList() throws IOException {
        assertThat(KakaoHolidayClient.parse(fixture("kakao-holidays-empty.json"), 2030)).isEmpty();
    }

    /**
     * 실제 오류 응답이다. 창을 32일로 넓혀 부르면 400 과 함께
     * {@code {"msg":"The period 'from' to 'to' must be within 31 days...","code":-2}} 가 온다
     * (2026-11-10 캡처). {@code events} 가 없으므로 <b>예외</b>여야 한다 — 조용히 0건으로
     * 넘기면 "성공한 0건"으로 보여서 달력에 공휴일이 사라진 채 아무도 모른다.
     */
    @Test
    @DisplayName("오류 응답은 예외다 — 빈 목록이 아니다")
    void errorBodyThrows() throws IOException {
        assertThatThrownBy(() -> KakaoHolidayClient.parse(fixture("kakao-error-period.json"), 2026))
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
     * 카카오가 {@code to <= from + 31일} 로 제한한다. <b>31일은 받아 주고 32일은 400
     * {@code code:-2} 로 거부한다</b> — 2026-11-10 에 실제로 양쪽을 불러 확인했고, 거부
     * 응답은 {@code kakao-error-period.json} 으로 남겼다. 우리 창은 정확히 상한에 붙어
     * 있으므로, 이 테스트가 깨지면 그 해 전체가 NONE 이 된다.
     */
    @Test
    @DisplayName("모든 창이 31일 이내다 — 32일이면 카카오가 400 을 준다")
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
     * 쪽이든 요청한 해의 1/1 과 12/31 이 들어오게 하는 여유다 — 실응답이 UTC 자정이라
     * 지금은 여유가 남지만, KST 자정으로 바뀌어도 안 깨지게 두는 쪽이다.
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

    /** 실제로 불러서 200 을 받은 창 하나와 같은 모양이어야 한다(2026-11-10 확인). */
    @Test
    @DisplayName("창 URI 에 from·to 가 UTC RFC3339 로 들어간다")
    void windowUriIsRfc3339() {
        Window window = new Window(
                Instant.parse("2025-12-31T00:00:00Z"), Instant.parse("2026-01-31T00:00:00Z"));

        assertThat(window.toUri().toString())
                .isEqualTo("https://kapi.kakao.com/v2/api/calendar/holidays"
                        + "?from=2025-12-31T00:00:00Z&to=2026-01-31T00:00:00Z");
    }

    // ───────────────────────── 합치기 ─────────────────────────

    /** 창이 겹치므로 같은 날이 두 번 온다. {@code (date, name)} 으로 지운다. */
    @Test
    @DisplayName("같은 (날짜, 이름)은 한 번만 남는다")
    void dedupesByDateAndName() {
        Holiday newYear = new Holiday(LocalDate.of(2026, 1, 1), "새해");

        List<Holiday> merged = KakaoHolidayClient.dedupeAndSort(List.of(newYear, newYear, newYear));

        assertThat(merged).containsExactly(newYear);
    }

    /** 같은 날에 이름이 둘이면 둘 다 남고 순서도 유지된다 (계약서 §8-2). */
    @Test
    @DisplayName("날짜 오름차순으로 정렬하고, 같은 날의 순서는 유지한다")
    void sortsByDateKeepingSameDayOrder() {
        Holiday later = new Holiday(LocalDate.of(2026, 3, 1), "삼일절");
        Holiday firstOfTwo = new Holiday(LocalDate.of(2026, 1, 1), "새해");
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
        String body = fixture("kakao-holidays-2026-02.json");
        StubClient client = StubClient.returning(window -> body);

        List<Holiday> holidays = client.fetch(2026);

        assertThat(client.calls).hasValueGreaterThan(1);
        assertThat(holidays).containsExactly(
                new Holiday(LocalDate.of(2026, 2, 16), "설날연휴"),
                new Holiday(LocalDate.of(2026, 2, 17), "설날"),
                new Holiday(LocalDate.of(2026, 2, 18), "설날연휴"),
                new Holiday(LocalDate.of(2026, 3, 1), "삼일절"),
                new Holiday(LocalDate.of(2026, 3, 2), "대체휴일"));
    }

    /**
     * <b>창 하나라도 실패하면 그 해 전체가 실패</b>다 (계약서 §8-2). 받아 온 창만 내리면
     * 2월만 빠진 달력을 FE 가 정상이라 믿는다 — 서비스는 이 예외를 받아 NONE 으로 바꾸고,
     * FE 는 자기 계산값으로 폴백한다.
     */
    @Test
    @DisplayName("창 하나가 실패하면 전체가 실패다 — 부분 성공을 내리지 않는다")
    void oneFailedWindowFailsTheYear() throws IOException {
        String body = fixture("kakao-holidays-2026-02.json");
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
        String body = fixture("kakao-holidays-2026-02.json");
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
