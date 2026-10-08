package com.kickoff.be.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 스쿼드 메이커 (계약서 §4-4, v1.28.0).
 *
 * 권한 경계와 검증이 이 기능의 전부라 둘을 같은 무게로 고정한다. 특히 <b>읽기도 비공개</b>
 * 라는 점이 팀 페이지의 다른 조회(§4-1 명단·전적은 공개)와 달라서, 경계가 새면 남의 팀
 * 선발 명단이 그대로 보인다.
 */
class SquadTest extends IntegrationTestSupport {

    private User owner;
    private User admin;
    private User member;
    private User stranger;
    private Team team;

    @BeforeEach
    void setUpTeam() throws Exception {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        admin = createUser("admin@example.com", "최총무", "010-2222-2222");
        member = createUser("member@example.com", "박멤버", "010-3333-3333");
        stranger = createUser("stranger@example.com", "남", "010-4444-4444");
        team = createTeam(owner, "FC 새벽", "서울 강서구");

        grantAdmin(admin);
        joinAndAccept(member);
    }

    // ── 저장·조회

    @Test
    @DisplayName("소유자가 스쿼드를 만들면 201 — 자리·교체가 그대로 돌아온다")
    void ownerCreates() throws Exception {
        long kim = addMember("김철수");
        long park = addMember("박영수");

        ResultActions created = save(owner, body("10/12 vs 마포", "F7_2_3_1",
                slots("{\"memberId\":" + kim + "}", "{\"name\":\"게스트 민수\"}", "{}", "{}", "{}",
                        "{}", "{}"),
                "[{\"memberId\":" + park + "}]"));

        created.andExpect(status().isCreated())
                .andExpect(jsonPath("$.squadId").isNumber())
                .andExpect(jsonPath("$.teamId").value(team.getId()))
                .andExpect(jsonPath("$.title").value("10/12 vs 마포"))
                .andExpect(jsonPath("$.formation").value("F7_2_3_1"))
                .andExpect(jsonPath("$.slots", hasSize(7)))
                // slot 0 은 GK 다. 번호는 배열 순서로 정해진다.
                .andExpect(jsonPath("$.slots[0].slot").value(0))
                .andExpect(jsonPath("$.slots[0].memberId").value(kim))
                .andExpect(jsonPath("$.slots[0].name").value("김철수"))
                // 명단 밖 사람은 memberId 없이 이름만
                .andExpect(jsonPath("$.slots[1].slot").value(1))
                .andExpect(jsonPath("$.slots[1].memberId").value(nullValue()))
                .andExpect(jsonPath("$.slots[1].name").value("게스트 민수"))
                .andExpect(jsonPath("$.bench", hasSize(1)))
                // 교체에는 slot 이 없다 (키는 남고 값이 null — 계약서 §2)
                .andExpect(jsonPath("$.bench[0].slot").value(nullValue()))
                .andExpect(jsonPath("$.bench[0].memberId").value(park))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    /**
     * 빈 자리가 허용된다 (계약서 §4-4 "짜다 만 스쿼드도 저장된다").
     *
     * 이게 막히면 사용자는 11자리를 다 채우기 전에 저장할 수 없다 — 보드를 조금씩 짜는
     * 사용 방식과 정면으로 어긋난다.
     */
    @Test
    @DisplayName("빈 자리만으로도 저장된다")
    void emptySlotsAreAllowed() throws Exception {
        save(owner, body("초안", "F6_2_2_1", emptySlots(6), "[]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slots", hasSize(6)))
                .andExpect(jsonPath("$.slots[0].memberId").value(nullValue()))
                .andExpect(jsonPath("$.slots[0].name").value(nullValue()))
                .andExpect(jsonPath("$.bench", hasSize(0)));
    }

    @Test
    @DisplayName("PUT 은 전체 교체 — 포메이션이 바뀌면 자리도 갈린다")
    void putReplacesEverything() throws Exception {
        long squadId = squadId(save(owner, body("초안", "F6_2_2_1", emptySlots(6), "[]")));

        mockMvc.perform(put("/api/teams/{t}/squads/{s}", team.getId(), squadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("확정", "F7_3_2_1", emptySlots(7), "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.squadId").value(squadId))
                .andExpect(jsonPath("$.title").value("확정"))
                .andExpect(jsonPath("$.formation").value("F7_3_2_1"))
                .andExpect(jsonPath("$.slots", hasSize(7)));
    }

    @Test
    @DisplayName("목록은 updatedAt 내림차순, 페이지 없음")
    void listIsNewestFirst() throws Exception {
        save(owner, body("첫 번째", "F6_2_2_1", emptySlots(6), "[]"));
        save(owner, body("두 번째", "F6_1_3_1", emptySlots(6), "[]"));

        mockMvc.perform(get("/api/teams/{t}/squads", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("두 번째"))
                .andExpect(jsonPath("$[1].title").value("첫 번째"))
                // 목록에는 자리 내용이 없다 (계약서 §4-4)
                .andExpect(jsonPath("$[0].slots").doesNotExist())
                .andExpect(jsonPath("$[0].formation").value("F6_1_3_1"))
                .andExpect(jsonPath("$[0].updatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("소유자가 지우면 204")
    void ownerDeletes() throws Exception {
        long squadId = squadId(save(owner, body("초안", "F6_2_2_1", emptySlots(6), "[]")));

        mockMvc.perform(delete("/api/teams/{t}/squads/{s}", team.getId(), squadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        detail(owner, squadId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SQUAD_NOT_FOUND"));
    }

    // ── 권한 (계약서 §4-4)

    @Test
    @DisplayName("읽기는 소속 전원 — OWNER·ADMIN·MEMBER 가 본다")
    void membersCanRead() throws Exception {
        long squadId = squadId(save(owner, body("초안", "F6_2_2_1", emptySlots(6), "[]")));

        for (User reader : new User[]{owner, admin, member}) {
            list(reader).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
            detail(reader, squadId).andExpect(status().isOk());
        }
    }

    /**
     * <b>읽기도 비공개다.</b> 팀 명단·전적(§4-1)은 공개인데 스쿼드는 아니다 — 선발 명단은
     * 상대에게 보여 줄 정보가 아니라 팀 안에서 짜는 것이다.
     */
    @Test
    @DisplayName("비소속은 읽기도 403")
    void strangerCannotRead() throws Exception {
        long squadId = squadId(save(owner, body("초안", "F6_2_2_1", emptySlots(6), "[]")));

        list(stranger).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        detail(stranger, squadId).andExpect(status().isForbidden());
    }

    /**
     * 비로그인도 403 이다 (계약서 §4-4) — 401 이 아니다.
     *
     * 그래서 이 경로는 시큐리티에서 인증을 걸지 않는다. 그 대가로 서비스의 소속 검사가
     * 유일한 방어선이 되므로, 이 테스트가 그 방어선을 지킨다.
     */
    @Test
    @DisplayName("비로그인은 403 — 401 이 아니다")
    void anonymousIsForbidden() throws Exception {
        long squadId = squadId(save(owner, body("초안", "F6_2_2_1", emptySlots(6), "[]")));

        mockMvc.perform(get("/api/teams/{t}/squads", team.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/teams/{t}/squads/{s}", team.getId(), squadId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("쓰기는 OWNER·ADMIN — 관리자도 만들고 고치고 지운다")
    void adminCanWrite() throws Exception {
        long squadId = squadId(save(admin, body("관리자 초안", "F6_2_2_1", emptySlots(6), "[]")));

        mockMvc.perform(put("/api/teams/{t}/squads/{s}", team.getId(), squadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("관리자 수정", "F6_2_2_1", emptySlots(6), "[]")))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/teams/{t}/squads/{s}", team.getId(), squadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
    }

    /**
     * MEMBER 는 읽기만 된다. v1.11.0 에서 "소속이면 통과"가 조용히 틀린 표현이 된 것과
     * 같은 함정이라 쓰기 세 메서드를 모두 확인한다.
     */
    @Test
    @DisplayName("MEMBER 는 쓰기 403 — 읽기는 되는데 만들 수 없다")
    void memberCannotWrite() throws Exception {
        long squadId = squadId(save(owner, body("초안", "F6_2_2_1", emptySlots(6), "[]")));

        save(member, body("멤버 초안", "F6_2_2_1", emptySlots(6), "[]"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/teams/{t}/squads/{s}", team.getId(), squadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", "F6_2_2_1", emptySlots(6), "[]")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/teams/{t}/squads/{s}", team.getId(), squadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isForbidden());
    }

    // ── 검증 (계약서 §4-4)

    @Test
    @DisplayName("자리 개수가 포메이션 총원과 다르면 400")
    void slotCountMustMatchFormation() throws Exception {
        save(owner, body("초안", "F11_4_3_3", emptySlots(10), "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("slots"));

        save(owner, body("초안", "F11_4_3_3", emptySlots(12), "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("slots"));
    }

    @Test
    @DisplayName("같은 팀원이 선발에 두 번이면 400 — field 가 두 번째 자리를 가리킨다")
    void duplicateInStartersIsRejected() throws Exception {
        long kim = addMember("김철수");

        save(owner, body("초안", "F6_2_2_1",
                slots("{\"memberId\":" + kim + "}", "{}", "{\"memberId\":" + kim + "}", "{}", "{}",
                        "{}"), "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("slots[2].memberId"));
    }

    /**
     * 선발과 교체를 <b>통틀어</b> 중복을 본다 (계약서 §4-4). 한 사람이 선발이면서 교체일
     * 수는 없다 — 두 표로 나눠 저장했다면 이 검사가 빠지기 쉬운 자리다.
     */
    @Test
    @DisplayName("선발에 있는 팀원을 교체에도 넣으면 400 — field 는 bench 쪽")
    void duplicateAcrossStartersAndBenchIsRejected() throws Exception {
        long kim = addMember("김철수");

        save(owner, body("초안", "F6_2_2_1",
                slots("{\"memberId\":" + kim + "}", "{}", "{}", "{}", "{}", "{}"),
                "[{\"memberId\":" + kim + "}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("bench[0].memberId"));
    }

    @Test
    @DisplayName("다른 팀의 memberId 는 400 \"이 팀의 팀원이 아닙니다\"")
    void foreignMemberIsRejected() throws Exception {
        User otherOwner = createUser("other@example.com", "남주장", "010-5555-5555");
        Team otherTeam = createTeam(otherOwner, "FC 남", "서울 중구");
        long foreignMemberId = idOf(mockMvc.perform(
                post("/api/teams/{id}/members", otherTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"남의 팀원\"}")));

        save(owner, body("초안", "F6_2_2_1",
                slots("{}", "{\"memberId\":" + foreignMemberId + "}", "{}", "{}", "{}", "{}"),
                "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("slots[1].memberId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("이 팀의 팀원이 아닙니다"));
    }

    /** 없는 id 도 같은 오류다 — 구분해 주면 남의 팀 명단 id 를 떠볼 수 있다. */
    @Test
    @DisplayName("없는 memberId 도 같은 400 으로 막힌다")
    void unknownMemberIsRejected() throws Exception {
        save(owner, body("초안", "F6_2_2_1",
                slots("{\"memberId\":999999}", "{}", "{}", "{}", "{}", "{}"), "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("slots[0].memberId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("이 팀의 팀원이 아닙니다"));
    }

    @Test
    @DisplayName("팀당 30개를 넘으면 400 — field 는 squads")
    void thirtyPerTeamIsTheLimit() throws Exception {
        for (int i = 0; i < 30; i++) {
            save(owner, body("초안 " + i, "F6_2_2_1", emptySlots(6), "[]"))
                    .andExpect(status().isCreated());
        }

        save(owner, body("31번째", "F6_2_2_1", emptySlots(6), "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("squads"));
    }

    /**
     * 상한에 걸려도 <b>고치기는 된다.</b> 수정은 개수를 늘리지 않으므로, 여기서 막으면
     * "오래된 스쿼드를 지워 주세요" 안내가 막다른 길이 된다 — 지우려고 열어 본 보드조차
     * 저장할 수 없게 된다.
     */
    @Test
    @DisplayName("30개가 차 있어도 기존 스쿼드 수정은 된다")
    void limitDoesNotBlockUpdate() throws Exception {
        long first = squadId(save(owner, body("초안 0", "F6_2_2_1", emptySlots(6), "[]")));
        for (int i = 1; i < 30; i++) {
            save(owner, body("초안 " + i, "F6_2_2_1", emptySlots(6), "[]"));
        }

        mockMvc.perform(put("/api/teams/{t}/squads/{s}", team.getId(), first)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("고친 제목", "F6_2_2_1", emptySlots(6), "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("고친 제목"));
    }

    @Test
    @DisplayName("남의 팀 스쿼드 id 를 내 팀 주소에 끼우면 404")
    void squadOfAnotherTeamIsNotFound() throws Exception {
        User otherOwner = createUser("other@example.com", "남주장", "010-5555-5555");
        Team otherTeam = createTeam(otherOwner, "FC 남", "서울 중구");
        long foreignSquadId = squadId(mockMvc.perform(
                post("/api/teams/{id}/squads", otherTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("남의 스쿼드", "F6_2_2_1", emptySlots(6), "[]"))));

        detail(owner, foreignSquadId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SQUAD_NOT_FOUND"));
    }

    // ── 스냅샷·cascade

    /**
     * 팀원이 명단에서 지워져도 스쿼드는 남는다 (계약서 §4-4 "과거 기록으로 남는다").
     *
     * <b>이 테스트가 지키는 것은 외래키 설정이다.</b> {@code member_id} 를 cascade 로
     * 걸면 팀원 한 명을 지울 때 그 사람이 들어간 스쿼드가 통째로 사라진다. 제약을 아예
     * 안 걸면 지워진 id 가 응답에 그대로 나간다. {@code on delete set null} 이라야
     * memberId 만 비워지고 스냅샷 이름이 남는다.
     */
    @Test
    @DisplayName("팀원을 명단에서 지우면 memberId 는 null, 이름은 저장 시점 스냅샷")
    void removedMemberLeavesNameSnapshot() throws Exception {
        long kim = addMember("김철수");
        long squadId = squadId(save(owner, body("초안", "F6_2_2_1",
                slots("{\"memberId\":" + kim + "}", "{}", "{}", "{}", "{}", "{}"), "[]")));

        mockMvc.perform(delete("/api/teams/{t}/members/{m}", team.getId(), kim)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        detail(owner, squadId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].memberId").value(nullValue()))
                .andExpect(jsonPath("$.slots[0].name").value("김철수"));
    }

    /**
     * 명단에 남아 있는 동안은 스냅샷이 아니라 <b>현재 이름</b>을 쓴다 (계약서 §4-4).
     *
     * 스냅샷을 그냥 보여 주면 이름을 고친 뒤 스쿼드에 옛 이름이 남아 같은 사람이 둘로
     * 보인다. 저장은 성공하고 로그도 조용해서 화면을 들여다볼 때까지 모른다.
     */
    @Test
    @DisplayName("팀원 이름을 바꾸면 스쿼드에도 따라간다")
    void nameFollowsRoster() throws Exception {
        long kim = addMember("김철수");
        long squadId = squadId(save(owner, body("초안", "F6_2_2_1",
                slots("{\"memberId\":" + kim + "}", "{}", "{}", "{}", "{}", "{}"), "[]")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/teams/{t}/members/{m}", team.getId(), kim)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김철수A\"}"))
                .andExpect(status().isOk());

        detail(owner, squadId)
                .andExpect(jsonPath("$.slots[0].memberId").value(kim))
                .andExpect(jsonPath("$.slots[0].name").value("김철수A"));
    }

    /** 팀 삭제·소유자 탈퇴 때 스쿼드도 함께 지운다 (계약서 §4-4 cascade, §3-4). */
    @Test
    @DisplayName("소유자가 탈퇴하면 스쿼드도 사라진다 — 500 으로 막히지 않는다")
    void withdrawalRemovesSquads() throws Exception {
        long kim = addMember("김철수");
        save(owner, body("초안", "F6_2_2_1",
                slots("{\"memberId\":" + kim + "}", "{}", "{}", "{}", "{}", "{}"), "[]"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(squadRepository.count()).isZero();
    }

    // ── 헬퍼

    private ResultActions save(User actor, String body) throws Exception {
        return mockMvc.perform(post("/api/teams/{id}/squads", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions list(User actor) throws Exception {
        return mockMvc.perform(get("/api/teams/{id}/squads", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(actor)));
    }

    private ResultActions detail(User actor, long squadId) throws Exception {
        return mockMvc.perform(get("/api/teams/{t}/squads/{s}", team.getId(), squadId)
                .header(HttpHeaders.AUTHORIZATION, bearer(actor)));
    }

    private String body(String title, String formation, String slots, String bench) {
        return """
                {"title":"%s","formation":"%s","slots":%s,"bench":%s}
                """.formatted(title, formation, slots, bench);
    }

    private String slots(String... items) {
        return "[" + String.join(",", items) + "]";
    }

    /** 빈 자리 n개. 개수 검증·빈 자리 허용을 볼 때 쓴다. */
    private String emptySlots(int n) {
        return IntStream.range(0, n).mapToObj(i -> "{}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private long squadId(ResultActions actions) throws Exception {
        return ((Number) JsonPath.read(bodyOf(actions), "$.squadId")).longValue();
    }

    /** 명단에 수기 팀원을 넣고 id 를 돌려준다 (계정 연결 없음 — 게스트가 아니라 팀원이다). */
    private long addMember(String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/teams/{id}/members", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")));
    }

    private void grantAdmin(User who) throws Exception {
        mockMvc.perform(post("/api/teams/{id}/admins", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + who.getEmail() + "\"}"))
                .andExpect(status().isCreated());
    }

    private void joinAndAccept(User who) throws Exception {
        long joinId = idOf(mockMvc.perform(post("/api/teams/{id}/join", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/teams/{t}/join-requests/{j}/accept", team.getId(), joinId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
    }
}
