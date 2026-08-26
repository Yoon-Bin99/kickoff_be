package com.kickoff.be.team;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 매칭에서 기록 만들기 (계약서 §4-1, v1.10.0).
 *
 * 리뷰와 대칭이다 — 양 팀이 <b>각자 자기 관점으로</b> 한 건씩 남긴다. 그래서 "한 매칭에 두
 * 기록"이 정상이고, 중복 금지는 팀별로만 건다.
 *
 * 자동으로 채워지는 건 상대 팀 이름과 경기 날짜뿐이다. 스코어는 사람이 적는다 — 앱은
 * 경기 결과를 알 방법이 없다. 그래서 계약서도 "자동 연동"이 아니라 "연결"이라고 쓴다.
 */
class MatchRecordTest extends IntegrationTestSupport {

    private User postOwner;
    private User applicantOwner;
    private User stranger;
    private Team postTeam;
    private Team applicantTeam;
    private MatchPost pastPost;
    private MatchRequest accepted;

    @BeforeEach
    void setUpMatch() {
        postOwner = createUser("post@example.com", "김주장", "010-1111-1111");
        applicantOwner = createUser("applicant@example.com", "이감독", "010-2222-2222");
        stranger = createUser("stranger@example.com", "남", "010-3333-3333");
        postTeam = createTeam(postOwner, "FC 새벽", "서울 강서구");
        applicantTeam = createTeam(applicantOwner, "마포 유나이티드", "서울 마포구");

        pastPost = createPost(postTeam, "지난 경기", OffsetDateTime.now().minusDays(3), false);
        accepted = acceptedRequest(pastPost, applicantTeam);
    }

    @Test
    @DisplayName("상대팀명과 경기일은 서버가 매칭에서 채운다 — 본문은 스코어뿐")
    void serverFillsOpponentAndDate() throws Exception {
        record(postOwner, "{\"ourScore\": 3, \"opponentScore\": 1, \"memo\": \"후반 역전승\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.opponentName").value("마포 유나이티드"))
                .andExpect(jsonPath("$.playedOn")
                        .value(pastPost.getMatchAt().toLocalDate().toString()))
                .andExpect(jsonPath("$.ourScore").value(3))
                .andExpect(jsonPath("$.result").value("WIN"))
                .andExpect(jsonPath("$.memo").value("후반 역전승"))
                .andExpect(jsonPath("$.requestId").value(accepted.getId()));
    }

    @Test
    @DisplayName("양 팀이 각자 자기 관점으로 기록한다 — 상대는 서로 반대로 적힌다")
    void bothTeamsRecordTheirOwnView() throws Exception {
        record(postOwner, "{\"ourScore\": 3, \"opponentScore\": 1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.opponentName").value("마포 유나이티드"))
                .andExpect(jsonPath("$.result").value("WIN"));

        record(applicantOwner, "{\"ourScore\": 1, \"opponentScore\": 3}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.opponentName").value("FC 새벽"))
                .andExpect(jsonPath("$.result").value("LOSS"));

        // 각 팀 목록에 한 건씩. 한 매칭에 두 기록이 정상이다
        records(postTeam).andExpect(jsonPath("$.content", hasSize(1)));
        records(applicantTeam).andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("같은 팀이 같은 매칭으로 두 번 기록하면 409")
    void duplicateIsConflict() throws Exception {
        record(postOwner, "{\"ourScore\": 3, \"opponentScore\": 1}").andExpect(status().isCreated());

        record(postOwner, "{\"ourScore\": 2, \"opponentScore\": 2}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("지우면 다시 기록할 수 있다 — myRecordWritten 도 false 로 돌아간다")
    void deletingAllowsRecordingAgain() throws Exception {
        long recordId = idOf(record(postOwner, "{\"ourScore\": 3, \"opponentScore\": 1}"));
        assertMyRecordWritten(postOwner, true);

        mockMvc.perform(delete("/api/teams/{id}/records/{recordId}", postTeam.getId(), recordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner)))
                .andExpect(status().isNoContent());

        assertMyRecordWritten(postOwner, false);
        record(postOwner, "{\"ourScore\": 0, \"opponentScore\": 0}").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("수락 전이거나 경기 전이면 409 RECORD_NOT_AVAILABLE")
    void notAvailableCases() throws Exception {
        // 수락 안 된 신청
        MatchPost anotherPast = createPost(postTeam, "지난 경기2",
                OffsetDateTime.now().minusDays(1), false);
        MatchRequest pending = pendingRequest(anotherPast, applicantTeam);
        recordOn(pending.getId(), postOwner, "{\"ourScore\": 1, \"opponentScore\": 0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_AVAILABLE"));

        // 아직 안 뛴 경기
        MatchPost future = createPost(postTeam, "다음 주 경기",
                OffsetDateTime.now().plusDays(5), false);
        MatchRequest futureAccepted = acceptedRequest(future, applicantTeam);
        recordOn(futureAccepted.getId(), postOwner, "{\"ourScore\": 1, \"opponentScore\": 0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("제3자는 403 — 매칭 상태를 알려주지 않는다")
    void thirdPartyIsForbidden() throws Exception {
        record(stranger, "{\"ourScore\": 9, \"opponentScore\": 0}")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("팀이 없으면 403 — 어느 매칭의 당사자도 될 수 없다")
    void userWithoutTeamIsForbidden() throws Exception {
        User teamless = createUser("teamless@example.com", "팀없음", "010-4444-4444");

        record(teamless, "{\"ourScore\": 1, \"opponentScore\": 0}")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("myRecordWritten 은 팀 관점별로 갈린다")
    void myRecordWrittenIsPerTeam() throws Exception {
        record(postOwner, "{\"ourScore\": 3, \"opponentScore\": 1}").andExpect(status().isCreated());

        // 글 작성 팀은 썼고, 신청 팀은 아직 안 썼다
        assertMyRecordWritten(postOwner, true);
        assertMyRecordWritten(applicantOwner, false);
    }

    @Test
    @DisplayName("매칭 기록도 팀 목록·전적 요약에 함께 합산된다")
    void matchRecordsJoinTheSameListAndSummary() throws Exception {
        // 손으로 넣은 기록 하나
        mockMvc.perform(post("/api/teams/{id}/records", postTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playedOn\": \"2026-08-01\", \"opponentName\": \"수동입력팀\","
                                + " \"ourScore\": 0, \"opponentScore\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").isEmpty());

        record(postOwner, "{\"ourScore\": 3, \"opponentScore\": 1}").andExpect(status().isCreated());

        records(postTeam).andExpect(jsonPath("$.content", hasSize(2)));
        mockMvc.perform(get("/api/teams/{id}", postTeam.getId()))
                .andExpect(jsonPath("$.recordSummary.wins").value(1))
                .andExpect(jsonPath("$.recordSummary.losses").value(1));
    }

    @Test
    @DisplayName("수동 기록은 여러 건이어도 유니크 제약에 걸리지 않는다 — request_id 가 null 이라서")
    void manualRecordsDoNotCollide() throws Exception {
        // (request_id, team_id) 유니크를 걸었지만 NULL 은 서로 같지 않아 수동 기록은 자유롭다.
        // 부분 인덱스 없이 이게 성립하는지가 V7 설계의 전제라 여기서 못 박는다.
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/teams/{id}/records", postTeam.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(postOwner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"playedOn\": \"2026-08-0" + i + "\","
                                    + " \"opponentName\": \"수동" + i + "\","
                                    + " \"ourScore\": 1, \"opponentScore\": 0}"))
                    .andExpect(status().isCreated());
        }

        records(postTeam).andExpect(jsonPath("$.content", hasSize(3)));
    }

    @Test
    @DisplayName("없는 매칭은 404")
    void unknownRequestIsNotFound() throws Exception {
        recordOn(99999L, postOwner, "{\"ourScore\": 1, \"opponentScore\": 0}")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("비로그인은 기록할 수 없다 — 401")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(post("/api/requests/{id}/record", accepted.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ourScore\": 1, \"opponentScore\": 0}"))
                .andExpect(status().isUnauthorized());
    }

    private void assertMyRecordWritten(User viewer, boolean expected) throws Exception {
        String path = viewer.getId().equals(postOwner.getId())
                ? "/api/requests/received"
                : "/api/requests/sent";
        // /received·/sent 는 페이지가 아니라 배열이다 (계약서 §6)
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].myRecordWritten").value(expected));
    }

    private ResultActions record(User actor, String body) throws Exception {
        return recordOn(accepted.getId(), actor, body);
    }

    private ResultActions recordOn(long requestId, User actor, String body) throws Exception {
        return mockMvc.perform(post("/api/requests/{id}/record", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions records(Team team) throws Exception {
        return mockMvc.perform(get("/api/teams/{id}/records", team.getId()))
                .andExpect(status().isOk());
    }
}
