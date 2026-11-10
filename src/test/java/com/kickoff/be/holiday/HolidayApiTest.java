package com.kickoff.be.holiday;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * GET /api/holidays (계약서 §8-2).
 *
 * 테스트 환경에는 인증키가 없다(`KAKAO_ADMIN_KEY` 미설정). 그래서 여기서 보는 것은
 * <b>키 없이도 200 이 나가는가</b>와 응답 모양이다 — 그게 이 기능의 기본 상태다.
 * 파싱·캐시·실패 처리는 {@code client.KakaoHolidayClientTest}·{@code service.HolidayServiceTest}
 * 가 본다.
 */
class HolidayApiTest extends IntegrationTestSupport {

    @Test
    @DisplayName("인증 없이 조회된다 — 달력은 로그인 전에도 보인다")
    void isPublic() throws Exception {
        mockMvc.perform(get("/api/holidays").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.source").value("NONE"))
                .andExpect(jsonPath("$.holidays").isArray())
                .andExpect(jsonPath("$.holidays", Matchers.hasSize(0)));
    }

    /**
     * 키가 없어도 <b>200</b> 이어야 한다. 500 이면 달력 화면이 통째로 안 뜬다 —
     * 공휴일 색이 빠지는 것보다 훨씬 나쁘다 (계약서 §8-2).
     */
    @Test
    @DisplayName("키가 없어도 500 이 아니라 200 NONE")
    void missingKeyIsNotServerError() throws Exception {
        mockMvc.perform(get("/api/holidays").param("year", "2027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("NONE"));
    }

    @Test
    @DisplayName("year 가 없으면 400 — 필수 파라미터다")
    void yearIsRequired() throws Exception {
        mockMvc.perform(get("/api/holidays"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("year"));
    }

    /**
     * 범위를 두는 이유는 캐시다. 연도가 캐시 키라서 아무 값이나 받으면 {@code ?year=1} 을
     * 수만 번 부르는 것으로 맵을 불릴 수 있다.
     */
    @Test
    @DisplayName("범위 밖 연도는 400")
    void outOfRangeYearIsRejected() throws Exception {
        for (String year : new String[]{"1899", "2201", "0", "-1"}) {
            mockMvc.perform(get("/api/holidays").param("year", year))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("year"));
        }
    }

    @Test
    @DisplayName("숫자가 아니면 400 — 500 이 아니다")
    void nonNumericYearIsRejected() throws Exception {
        mockMvc.perform(get("/api/holidays").param("year", "올해"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /** 경계는 통과해야 한다. 범위 검사를 넣을 때 한 칸 어긋나는 일이 흔하다. */
    @Test
    @DisplayName("경계 연도(1900·2200)는 통과한다")
    void boundaryYearsAreAccepted() throws Exception {
        mockMvc.perform(get("/api/holidays").param("year", "1900"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/holidays").param("year", "2200"))
                .andExpect(status().isOk());
    }
}
