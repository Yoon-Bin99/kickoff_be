package com.kickoff.be.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * PATCH 지우기 규칙 (계약서 §5, v1.5.1).
 *
 * 규칙은 한 줄이다 — <b>필드 없음 = 유지, 명시적 null = 지움</b>. 좌표에서 검증된 규칙을
 * optional 필드 전반으로 넓힌 것이라, 여기서 보는 건 "지워지는가"보다 <b>"안 보낸 게
 * 지워지지 않는가"</b>다. 좌표 때 실제로 그 방향으로 한 번 잘못 만들었다.
 *
 * 좌표 자체는 PostCoordinateTest 가 따로 본다.
 *
 * 계좌 3필드는 최상위가 아니라 {@code payment} 안에 있고, 작성자와 수락된 신청 팀에게만
 * 내려간다 (계약서 §5). 그래서 아래 단언이 {@code $.payment.bankName} 을 본다.
 */
class PostClearFieldsTest extends IntegrationTestSupport {

    private User author;

    @BeforeEach
    void setUpAuthor() {
        author = createUser("author@example.com", "김주장", "010-1111-1111");
        createTeam(author, "FC 새벽", "서울 강서구");
    }

    // ── 지울 수 있는 단일 필드

    @Test
    @DisplayName("preferredSkillLevel 을 null 로 보내면 실력 무관으로 되돌아간다")
    void preferredSkillLevelCanBeCleared() throws Exception {
        long postId = createPost(("\"preferredSkillLevel\": \"AMATEUR\""));

        patchPost(postId, "{\"preferredSkillLevel\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredSkillLevel").isEmpty());

        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(jsonPath("$.preferredSkillLevel").isEmpty());
    }

    @Test
    @DisplayName("rentalFee 를 null 로 보내면 대여료 미정으로 되돌아간다")
    void rentalFeeCanBeCleared() throws Exception {
        long postId = createPost(("\"rentalFee\": 80000"));

        patchPost(postId, "{\"rentalFee\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalFee").isEmpty());
    }

    // ── 입금액·계좌의 원자적 삭제

    @Test
    @DisplayName("depositAmount 를 null 로 보내면 계좌 3필드가 함께 지워진다")
    void clearingDepositAlsoClearsAccount() throws Exception {
        long postId = createPost((depositJson()));

        patchPost(postId, "{\"depositAmount\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositAmount").isEmpty())
                // 계좌가 사라지면 payment 오브젝트 자체가 내려가지 않는다 (계약서 §5)
                .andExpect(jsonPath("$.payment").isEmpty());

        // 되읽어도 그대로여야 한다. 금액만 지우고 계좌가 남으면 "무료 경기인데 입금 안내가
        // 붙은" 글이 되는데, 그게 이 규칙이 존재하는 이유다.
        mockMvc.perform(get("/api/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(jsonPath("$.depositAmount").isEmpty())
                .andExpect(jsonPath("$.payment").isEmpty());
    }

    @Test
    @DisplayName("계좌만 따로 null 로 지우려 하면 400 — 입금액을 지우라고 알려준다")
    void accountCannotBeClearedAlone() throws Exception {
        long postId = createPost((depositJson()));

        for (String field : new String[] {"bankName", "accountNumber", "accountHolder"}) {
            patchPost(postId, "{\"" + field + "\": null}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field));
        }

        // 400 이 났으니 원래 값이 온전해야 한다
        mockMvc.perform(get("/api/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(jsonPath("$.depositAmount").value(50000))
                .andExpect(jsonPath("$.payment.bankName").value("카카오뱅크"));
    }

    @Test
    @DisplayName("계좌 빈 문자열은 지우기로 쳐주지 않는다 — 길이 규칙으로 거절")
    void emptyStringIsNotAClear() throws Exception {
        long postId = createPost((depositJson()));

        patchPost(postId, "{\"bankName\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("bankName"));
    }

    @Test
    @DisplayName("POST 도 계좌 빈 문자열을 거절한다 — 나중에 고칠 수 없는 글을 막는다")
    void createRejectsEmptyAccountString() throws Exception {
        // POST 에서 "" 를 통과시키면 "은행명이 빈 칸인 계좌"가 저장되는데, PATCH 에서도
        // 빈 문자열은 거절되므로 그 글은 영원히 고칠 수 없게 된다 (계약서 §5).
        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("\"depositAmount\": 50000, \"bankName\": \"\","
                                + " \"accountNumber\": \"3333-01-1234567\","
                                + " \"accountHolder\": \"김주장\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("bankName"));
    }

    @Test
    @DisplayName("입금액만 고치는 PATCH 는 계속 통한다 — 계좌는 되읽을 수 없기 때문")
    void changingOnlyTheAmountStillWorks() throws Exception {
        long postId = createPost((depositJson()));

        patchPost(postId, "{\"depositAmount\": 30000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositAmount").value(30000))
                .andExpect(jsonPath("$.payment.bankName").value("카카오뱅크"));
    }

    // ── 지울 수 없는 필드

    @Test
    @DisplayName("필수 필드에 null 을 보내면 400 — 조용히 무시하지 않는다")
    void requiredFieldsRejectExplicitNull() throws Exception {
        long postId = createPost(("\"rentalFee\": 80000"));

        for (String field : new String[] {
                "title", "content", "matchAt", "location", "region", "status"}) {
            patchPost(postId, "{\"" + field + "\": null}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field));
        }
    }

    // ── 안 보낸 필드는 그대로 (이게 제일 중요하다)

    @Test
    @DisplayName("제목만 바꾸는 PATCH 는 나머지를 하나도 건드리지 않는다")
    void absentFieldsAreUntouched() throws Exception {
        long postId = createPost((depositJson() + ", \"rentalFee\": 80000,"
                + " \"preferredSkillLevel\": \"AMATEUR\""));

        patchPost(postId, "{\"title\": \"제목만 바꿉니다\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제목만 바꿉니다"))
                .andExpect(jsonPath("$.depositAmount").value(50000))
                .andExpect(jsonPath("$.payment.bankName").value("카카오뱅크"))
                .andExpect(jsonPath("$.payment.accountNumber").value("3333-01-1234567"))
                .andExpect(jsonPath("$.payment.accountHolder").value("김주장"))
                .andExpect(jsonPath("$.rentalFee").value(80000))
                .andExpect(jsonPath("$.preferredSkillLevel").value("AMATEUR"));
    }

    @Test
    @DisplayName("빈 본문 PATCH 는 아무것도 바꾸지 않는다")
    void emptyPatchChangesNothing() throws Exception {
        long postId = createPost((depositJson()));

        patchPost(postId, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositAmount").value(50000))
                .andExpect(jsonPath("$.payment.bankName").value("카카오뱅크"));
    }

    @Test
    @DisplayName("이미 비어 있는 필드를 지워도 200 — 멱등이다")
    void clearingAlreadyEmptyFieldIsFine() throws Exception {
        long postId = createPost(("\"rentalFee\": 80000"));

        patchPost(postId, "{\"preferredSkillLevel\": null, \"depositAmount\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredSkillLevel").isEmpty())
                .andExpect(jsonPath("$.depositAmount").isEmpty());
    }

    private static String depositJson() {
        return "\"depositAmount\": 50000, \"bankName\": \"카카오뱅크\","
                + " \"accountNumber\": \"3333-01-1234567\", \"accountHolder\": \"김주장\"";
    }

    /** 필수 필드를 채운 글을 만들고 id 를 준다. extraFields 는 그 위에 얹는 JSON 조각. */
    private long createPost(String extraFields) throws Exception {
        return idOf(mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody(extraFields)))
                .andExpect(status().isCreated()));
    }

    private static String postBody(String extraFields) {
        String matchAt = OffsetDateTime.now().plusDays(7)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String body = "{\"title\": \"주말 경기 상대 구합니다\", \"content\": \"편하게 한 판 하실 팀\","
                + " \"matchAt\": \"" + matchAt + "\", \"location\": \"강서구민운동장\","
                // fieldType 은 v1.19.0 에서 폐지됐지만 <b>일부러 계속 보낸다</b>.
                // 구버전 APK 가 이걸 실어 보내는데, 서버가 400 을 내면 그 앱들의 글쓰기가
                // 통째로 막힌다. 모르는 필드를 무시한다는 성질을 여기서 붙잡아 둔다.
                + " \"region\": \"서울 강서구\", \"fieldType\": \"SOCCER_11\"";
        if (extraFields != null) {
            body += ", " + extraFields;
        }
        return body + "}";
    }

    private ResultActions patchPost(long postId, String body) throws Exception {
        return mockMvc.perform(patch("/api/posts/{id}", postId)
                .header(HttpHeaders.AUTHORIZATION, bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
