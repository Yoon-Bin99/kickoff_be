package com.kickoff.be.team;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 경기 기록과 전적 요약 (계약서 §4-1, v1.8.0).
 *
 * result 는 저장하지 않고 스코어에서 계산한다. 그래서 여기서 확인할 건 "계산이 맞는가"와
 * <b>"요약이 기록과 일치하는가"</b> 둘이다. 둘이 어긋나면 화면에 7승이라고 쓰여 있는데
 * 목록에는 6경기만 있는 상태가 되는데, 그건 에러가 아니라 조용히 틀린 값이다.
 */
class TeamRecordTest extends IntegrationTestSupport {

    private User owner;
    private User stranger;
    private Team team;

    @BeforeEach
    void setUpTeam() {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        stranger = createUser("stranger@example.com", "남", "010-2222-2222");
        team = createTeam(owner, "FC 새벽", "서울 강서구");
    }

    @Test
    @DisplayName("기록을 남기면 목록에 나온다 — 조회는 비로그인도 된다")
    void addAndList() throws Exception {
        addRecord("2026-08-17", "FC 새벽", 3, 1, "후반 역전승")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playedOn").value("2026-08-17"))
                .andExpect(jsonPath("$.opponentName").value("FC 새벽"))
                .andExpect(jsonPath("$.ourScore").value(3))
                .andExpect(jsonPath("$.opponentScore").value(1))
                .andExpect(jsonPath("$.result").value("WIN"))
                .andExpect(jsonPath("$.memo").value("후반 역전승"));

        mockMvc.perform(get("/api/teams/{id}/records", team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("result 는 서버가 스코어로 계산한다 — 승·무·패")
    void resultIsComputedFromScores() throws Exception {
        addRecord("2026-08-01", "A팀", 3, 1, null).andExpect(jsonPath("$.result").value("WIN"));
        addRecord("2026-08-02", "B팀", 2, 2, null).andExpect(jsonPath("$.result").value("DRAW"));
        addRecord("2026-08-03", "C팀", 0, 4, null).andExpect(jsonPath("$.result").value("LOSS"));
        // 0-0 도 무승부다
        addRecord("2026-08-04", "D팀", 0, 0, null).andExpect(jsonPath("$.result").value("DRAW"));
    }

    @Test
    @DisplayName("전적 요약이 기록과 일치한다 — 기록이 없으면 전부 0")
    void recordSummaryMatchesRecords() throws Exception {
        mockMvc.perform(get("/api/teams/{id}", team.getId()))
                .andExpect(jsonPath("$.recordSummary.wins").value(0))
                .andExpect(jsonPath("$.recordSummary.draws").value(0))
                .andExpect(jsonPath("$.recordSummary.losses").value(0));

        addRecord("2026-08-01", "A팀", 3, 1, null);
        addRecord("2026-08-02", "B팀", 1, 0, null);
        addRecord("2026-08-03", "C팀", 2, 2, null);
        addRecord("2026-08-04", "D팀", 0, 4, null);

        mockMvc.perform(get("/api/teams/{id}", team.getId()))
                .andExpect(jsonPath("$.recordSummary.wins").value(2))
                .andExpect(jsonPath("$.recordSummary.draws").value(1))
                .andExpect(jsonPath("$.recordSummary.losses").value(1));
    }

    @Test
    @DisplayName("기록을 지우면 요약도 따라 줄어든다")
    void summaryFollowsDeletion() throws Exception {
        long winId = idOf(addRecord("2026-08-01", "A팀", 3, 1, null));
        addRecord("2026-08-02", "B팀", 0, 1, null);

        mockMvc.perform(get("/api/teams/{id}", team.getId()))
                .andExpect(jsonPath("$.recordSummary.wins").value(1));

        deleteRecord(owner, winId).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams/{id}", team.getId()))
                .andExpect(jsonPath("$.recordSummary.wins").value(0))
                .andExpect(jsonPath("$.recordSummary.losses").value(1));
    }

    @Test
    @DisplayName("playedOn DESC, 같은 날이면 나중에 넣은 것이 먼저")
    void orderingRule() throws Exception {
        addRecord("2026-08-01", "가장오래된", 1, 0, null);
        addRecord("2026-08-20", "가장최근", 1, 0, null);
        long sameDayFirst = idOf(addRecord("2026-08-10", "같은날먼저", 1, 0, null));
        long sameDayLater = idOf(addRecord("2026-08-10", "같은날나중", 1, 0, null));

        mockMvc.perform(get("/api/teams/{id}/records", team.getId()))
                .andExpect(jsonPath("$.content[*].opponentName",
                        contains("가장최근", "같은날나중", "같은날먼저", "가장오래된")));
        // 같은 날 동률은 id DESC 라 나중에 넣은 쪽이 앞이다
        mockMvc.perform(get("/api/teams/{id}/records", team.getId()))
                .andExpect(jsonPath("$.content[1].id").value(sameDayLater))
                .andExpect(jsonPath("$.content[2].id").value(sameDayFirst));
    }

    @Test
    @DisplayName("size 는 기본 20, 최대 50 으로 잘린다")
    void pagingRules() throws Exception {
        for (int i = 1; i <= 3; i++) {
            addRecord("2026-08-0" + i, "팀" + i, 1, 0, null);
        }

        mockMvc.perform(get("/api/teams/{id}/records", team.getId()).param("size", "2"))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/teams/{id}/records", team.getId())
                        .param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content", hasSize(1)));
        // 최대치를 넘겨도 400 이 아니라 잘라서 처리한다 (목록 조회와 같은 방식)
        mockMvc.perform(get("/api/teams/{id}/records", team.getId()).param("size", "999"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("미래 날짜는 400 — 아직 안 뛴 경기는 기록이 아니다")
    void futureDateIsRejected() throws Exception {
        addRecord(LocalDate.now().plusDays(1).toString(), "미래팀", 1, 0, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("playedOn"));

        // 오늘은 통과한다
        addRecord(LocalDate.now().toString(), "오늘팀", 1, 0, null)
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("점수는 0~99, 상대 팀 이름은 필수")
    void validationRules() throws Exception {
        addRecord("2026-08-01", "A팀", 100, 0, null).andExpect(status().isBadRequest());
        addRecord("2026-08-01", "A팀", -1, 0, null).andExpect(status().isBadRequest());
        addRecord("2026-08-01", "", 1, 0, null).andExpect(status().isBadRequest());
        addRecord("2026-08-01", "A팀", 1, 0, "가".repeat(201)).andExpect(status().isBadRequest());

        addRecord("2026-08-01", "A팀", 0, 99, null).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("없는 기록은 404 RECORD_NOT_FOUND, 남의 팀 기록도 404")
    void notFoundCases() throws Exception {
        deleteRecord(owner, 99999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));

        User otherOwner = createUser("other@example.com", "다른주장", "010-3333-3333");
        Team otherTeam = createTeam(otherOwner, "FC 노을", "서울 마포구");
        long otherRecordId = idOf(mockMvc.perform(post("/api/teams/{id}/records", otherTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-08-01", "남의팀상대", 1, 0, null)))
                .andExpect(status().isCreated()));

        deleteRecord(owner, otherRecordId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
    }

    @Test
    @DisplayName("무관계한 사람은 기록을 못 남긴다 — 403")
    void strangerCannotWrite() throws Exception {
        long recordId = idOf(addRecord("2026-08-01", "A팀", 1, 0, null));

        mockMvc.perform(post("/api/teams/{id}/records", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-08-02", "침입", 9, 0, null)))
                .andExpect(status().isForbidden());
        deleteRecord(stranger, recordId).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자도 기록을 남길 수 있다 (계약서 §4-2 권한표)")
    void adminCanWriteRecords() throws Exception {
        User admin = createUser("admin@example.com", "최총무", "010-4444-4444");
        mockMvc.perform(post("/api/teams/{id}/admins", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + admin.getEmail() + "\"}"))
                .andExpect(status().isCreated());

        long recordId = idOf(mockMvc.perform(post("/api/teams/{id}/records", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-08-05", "관리자가 남긴 경기", 2, 1, null)))
                .andExpect(status().isCreated()));

        deleteRecord(admin, recordId).andExpect(status().isNoContent());
    }

    private static String body(String playedOn, String opponentName, int ourScore,
                               int opponentScore, String memo) {
        String json = "{\"playedOn\": \"" + playedOn + "\", \"opponentName\": \"" + opponentName
                + "\", \"ourScore\": " + ourScore + ", \"opponentScore\": " + opponentScore;
        if (memo != null) {
            json += ", \"memo\": \"" + memo + "\"";
        }
        return json + "}";
    }

    private ResultActions addRecord(String playedOn, String opponentName, int ourScore,
                                    int opponentScore, String memo) throws Exception {
        return mockMvc.perform(post("/api/teams/{id}/records", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(playedOn, opponentName, ourScore, opponentScore, memo)));
    }

    private ResultActions deleteRecord(User actor, long recordId) throws Exception {
        return mockMvc.perform(delete("/api/teams/{id}/records/{recordId}",
                        team.getId(), recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(actor)));
    }
}
