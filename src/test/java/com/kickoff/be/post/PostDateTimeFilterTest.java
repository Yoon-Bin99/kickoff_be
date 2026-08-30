package com.kickoff.be.post;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 홈 날짜·시간대 필터 (계약서 §5, v1.18.0).
 *
 * 판정 기준이 <b>KST</b> 인데 matchAt 은 UTC 로 정규화되어 저장된다. 그래서 "9월 1일
 * 새벽 6시 경기"는 DB 에 8월 31일 21시로 들어 있다 — 날짜도 시각도 어긋난 채로. 이 어긋남을
 * 어디서 되돌리느냐가 이 기능의 전부고, 틀리면 <b>하루 밀린 목록</b>이 조용히 나온다.
 * 에러가 없으니 화면을 날짜별로 세어 보기 전에는 아무도 모른다.
 *
 * 그래서 경계를 촘촘히 본다 — 자정 직전·직후, 슬롯 시작 정각, 그리고 자정을 넘는 NIGHT.
 */
class PostDateTimeFilterTest extends IntegrationTestSupport {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private Team team;
    private LocalDate day;

    @BeforeEach
    void setUpTeam() {
        User owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        team = createTeam(owner, "FC 새벽", "서울 강서구");
        // 오늘 기준으로 잡으면 자정을 걸칠 때 테스트가 날마다 다른 결과를 낸다.
        // 충분히 미래의 고정된 날을 쓴다 (지난 경기는 목록에서 빠지므로 과거는 못 쓴다).
        day = LocalDate.now(KST).plusDays(30);
    }

    // ── 전제

    @Test
    @DisplayName("저장 시간대가 KST 로 고정돼 있다 — 이 전제가 깨지면 필터가 조용히 어긋난다")
    void storageZoneIsPinned() {
        // Hibernate 의 NORMALIZE 는 JVM 기본 시간대로 타임스탬프를 정규화한다. 호스트가
        // UTC 인 컨테이너에서 돌면 같은 경기가 아홉 시간 다른 값으로 저장되고, 시간대
        // 필터는 그만큼 어긋난 목록을 낸다 — 에러 없이.
        //
        // BeApplication 의 정적 초기화가 그 흔들림을 없앤다. 그 한 줄이 사라지면 여기서
        // 먼저 깨져야 한다. 아니면 배포한 뒤 "새벽으로 걸렀는데 낮 경기가 나온다"로
        // 드러나는데, 그때는 원인이 필터에 있어 보이지 저 초기화에 있어 보이지 않는다.
        org.assertj.core.api.Assertions.assertThat(java.time.ZoneId.systemDefault())
                .as("BeApplication 이 고정하는 시간대")
                .isEqualTo(java.time.ZoneId.of("Asia/Seoul"));
    }

    // ── 날짜

    @Test
    @DisplayName("KST 하루의 경계 — 00:00 은 그 날, 23:59 도 그 날, 앞뒤는 다른 날")
    void dateBoundariesAreKstDays() throws Exception {
        postAt(day.minusDays(1), LocalTime.of(23, 59), "전날 끝");
        postAt(day, LocalTime.of(0, 0), "그날 시작");
        postAt(day, LocalTime.of(23, 59), "그날 끝");
        postAt(day.plusDays(1), LocalTime.of(0, 0), "다음날 시작");

        // UTC 로 저장하면 "그날 시작"은 전날 15시, "그날 끝"은 그날 14:59 다.
        // KST 로 되돌리지 않으면 여기서 앞뒤 글이 섞여 들어온다
        search("dates=" + day)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title",
                        containsInAnyOrder("그날 시작", "그날 끝")));
    }

    @Test
    @DisplayName("여러 날짜는 OR — 고른 날들만 나온다")
    void multipleDatesAreOr() throws Exception {
        postAt(day, LocalTime.of(10, 0), "첫날");
        postAt(day.plusDays(1), LocalTime.of(10, 0), "둘째날");
        postAt(day.plusDays(2), LocalTime.of(10, 0), "셋째날");

        search("dates=%s,%s".formatted(day, day.plusDays(2)))
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("첫날", "셋째날")));
    }

    @Test
    @DisplayName("날짜 형식이 하나라도 틀리면 400 — 틀린 것만 버리지 않는다")
    void malformedDateIsRejected() throws Exception {
        // 틀린 항목만 조용히 버리면 사용자가 고른 것과 다른 결과가 나온다
        search("dates=%s,2026-13-99".formatted(day))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        search("dates=abc").andExpect(status().isBadRequest());
        search("dates=2026-9-1").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("날짜는 최대 14개")
    void tooManyDatesRejected() throws Exception {
        StringBuilder fifteen = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            fifteen.append(i > 0 ? "," : "").append(day.plusDays(i));
        }
        search("dates=" + fifteen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 같은 날짜를 여러 번 보낸 건 한도 초과가 아니라 사용자 실수다
        StringBuilder repeated = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            repeated.append(i > 0 ? "," : "").append(day);
        }
        search("dates=" + repeated).andExpect(status().isOk());
    }

    // ── 시간대

    @Test
    @DisplayName("슬롯 경계는 시작 포함·끝 제외 — 08:00 정각은 MORNING")
    void slotBoundariesIncludeStartExcludeEnd() throws Exception {
        postAt(day, LocalTime.of(7, 59), "새벽 끝");
        postAt(day, LocalTime.of(8, 0), "오전 시작");

        search("times=DAWN")
                .andExpect(jsonPath("$.content[*].title", contains("새벽 끝")));
        search("times=MORNING")
                .andExpect(jsonPath("$.content[*].title", contains("오전 시작")));
    }

    @Test
    @DisplayName("NIGHT 은 자정을 넘는다 — 22시와 다음 날 04시가 같은 슬롯")
    void nightSpansMidnight() throws Exception {
        postAt(day, LocalTime.of(22, 0), "밤 시작");
        postAt(day, LocalTime.of(23, 30), "자정 직전");
        postAt(day.plusDays(1), LocalTime.of(0, 30), "자정 직후");
        postAt(day.plusDays(1), LocalTime.of(4, 59), "밤 끝");
        postAt(day.plusDays(1), LocalTime.of(5, 0), "새벽 시작");

        // "시작 ≤ 시각 < 끝" 이라는 단순한 식으로 짜면 NIGHT 만 통째로 빈다
        search("times=NIGHT")
                .andExpect(jsonPath("$.content[*].title",
                        containsInAnyOrder("밤 시작", "자정 직전", "자정 직후", "밤 끝")));
        search("times=DAWN")
                .andExpect(jsonPath("$.content[*].title", contains("새벽 시작")));
    }

    @Test
    @DisplayName("다섯 슬롯이 하루를 빈틈없이 덮는다")
    void slotsCoverTheWholeDay() throws Exception {
        for (int hour = 0; hour < 24; hour++) {
            postAt(day, LocalTime.of(hour, 0), "%02d시".formatted(hour));
        }

        // 하나라도 빠지거나 겹치면 24개가 안 된다 — 경계를 하나씩 보는 것보다
        // 이쪽이 "덮여 있는가"를 직접 말해 준다
        search("times=DAWN,MORNING,AFTERNOON,EVENING,NIGHT&size=50")
                .andExpect(jsonPath("$.content", hasSize(24)));
    }

    @Test
    @DisplayName("여러 시간대는 OR")
    void multipleSlotsAreOr() throws Exception {
        postAt(day, LocalTime.of(6, 0), "새벽");
        postAt(day, LocalTime.of(14, 0), "오후");
        postAt(day, LocalTime.of(20, 0), "저녁");

        search("times=DAWN,EVENING")
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("새벽", "저녁")));
    }

    @Test
    @DisplayName("모르는 시간대는 400")
    void unknownSlotRejected() throws Exception {
        search("times=LUNCH")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        search("times=DAWN,").andExpect(status().isBadRequest());
        search("times=dawn").andExpect(status().isBadRequest());
    }

    // ── 조합

    @Test
    @DisplayName("dates 와 times 는 AND — \"주말 새벽\"이 이 조합이다")
    void datesAndTimesAreAnded() throws Exception {
        postAt(day, LocalTime.of(6, 0), "그날 새벽");
        postAt(day, LocalTime.of(20, 0), "그날 저녁");
        postAt(day.plusDays(1), LocalTime.of(6, 0), "다음날 새벽");

        search("dates=%s&times=DAWN".formatted(day))
                .andExpect(jsonPath("$.content[*].title", contains("그날 새벽")));
    }

    @Test
    @DisplayName("다른 필터와도 AND — region 과 함께 걸린다")
    void combinesWithOtherFilters() throws Exception {
        User other = createUser("other@example.com", "이감독", "010-2222-2222");
        Team mapo = createTeam(other, "마포 유나이티드", "서울 마포구");
        postAt(day, LocalTime.of(6, 0), "강서 새벽");
        postAtFor(mapo, day, LocalTime.of(6, 0), "마포 새벽", "서울 마포구");

        // 한글은 param() 으로 넘긴다. 쿼리 문자열에 그대로 붙이면 MockMvc 가
        // 퍼센트 인코딩을 풀어 주지 않아 엉뚱한 지역으로 찾는다
        mockMvc.perform(get("/api/posts").param("times", "DAWN").param("region", "마포"))
                .andExpect(jsonPath("$.content[*].title", contains("마포 새벽")));
    }

    @Test
    @DisplayName("둘 다 없으면 예전 그대로 — 필터가 안 걸린다")
    void noFilterKeepsEverything() throws Exception {
        postAt(day, LocalTime.of(6, 0), "하나");
        postAt(day.plusDays(1), LocalTime.of(20, 0), "둘");

        search("").andExpect(jsonPath("$.content", hasSize(2)));
        search("dates=&times=").andExpect(jsonPath("$.content", hasSize(2)));
    }

    // ── 헬퍼

    private void postAt(LocalDate date, LocalTime time, String title) {
        postAtFor(team, date, time, title, "서울 강서구");
    }

    private void postAtFor(Team owner, LocalDate date, LocalTime time, String title,
                           String region) {
        OffsetDateTime matchAt = OffsetDateTime.of(date, time, KST);
        createPostIn(owner, title, matchAt, region);
    }

    private ResultActions search(String query) throws Exception {
        return mockMvc.perform(get("/api/posts?" + query));
    }
}
