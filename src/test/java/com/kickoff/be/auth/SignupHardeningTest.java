package com.kickoff.be.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 가입 폼 강화 (계약서 §3, v1.16.0) — 유니크 규칙·비밀번호 규칙·사전 중복 확인.
 *
 * 이 셋은 성질이 다르다. 유니크는 <b>DB 제약과 응답이 함께</b> 맞아야 하고(둘 중 하나만
 * 있으면 500 이 나가거나 중복이 새어 든다), 비밀번호 규칙은 <b>가입에만</b> 걸려야 하며
 * (소급하면 기존 사용자가 로그인하지 못한다), 사전 확인은 <b>진실이 아니라 힌트</b>다
 * (확인과 제출 사이의 경합은 409 가 답한다).
 */
class SignupHardeningTest extends IntegrationTestSupport {

    // ── 비밀번호 규칙

    @Test
    @DisplayName("영문·숫자·특수문자를 모두 갖춰야 가입된다")
    void passwordNeedsAllThreeKinds() throws Exception {
        signup("ok@example.com", "pass1234!", "규칙맞음", "010-4000-0001")
                .andExpect(status().isCreated());

        // 특수문자 없음 — v1.15.0 까지 통하던 pass1234 가 여기서 걸린다
        signup("nospecial@example.com", "pass1234", "특수없음", "010-4000-0002")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(org.hamcrest.Matchers.hasItem("password")));
        // 숫자 없음
        signup("nodigit@example.com", "password!", "숫자없음", "010-4000-0003")
                .andExpect(status().isBadRequest());
        // 영문 없음
        signup("noletter@example.com", "12345678!", "영문없음", "010-4000-0004")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("길이와 구성은 따로 알려준다 — 고치는 방법이 다르다")
    void lengthAndCompositionAreSeparateRules() throws Exception {
        // 8자 미만이면서 구성도 안 맞으면 안내가 둘 다 나가야 한다. 하나로 합쳐 두면
        // 짧기만 한 사용자에게 "특수문자를 넣으라"는 엉뚱한 안내만 나간다
        signup("short@example.com", "ab1!", "짧음", "010-4000-0005")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]")
                        .value(org.hamcrest.Matchers.hasSize(1)));

        signup("shortbad@example.com", "abc", "짧고엉망", "010-4000-0006")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]")
                        .value(org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    @DisplayName("규칙을 어기는 기존 비밀번호로도 로그인은 된다 — 소급하지 않는다")
    void legacyPasswordsStillLogIn() throws Exception {
        // 시드와 기존 계정의 비밀번호는 pass1234 다. 규칙을 로그인에도 걸면 규칙을 만든
        // 날 멀쩡한 사용자가 전부 못 들어온다 — 그 순간에는 서버 잘못으로 보이지도 않는다
        createUserWithPassword("legacy@example.com", "예전사람", "010-4000-0007", "pass1234");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"legacy@example.com\", \"password\": \"pass1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    // ── 로그인 조회 (v1.16.0)

    @Test
    @DisplayName("로그인도 이메일 대소문자를 무시한다 — 가입 판정과 같은 기준이어야 한다")
    void loginIgnoresEmailCase() throws Exception {
        createUser("case@example.com", "케이스", "010-4500-0001");

        // 기준이 어긋나면 kim@ 으로 가입한 사람이 Kim@ 으로는 못 들어오는데, 다시
        // 가입하려 해도 중복이라 막힌다 — 어느 쪽으로도 못 가는 상태가 된다
        login("CASE@Example.com").andExpect(status().isOk());
        login("case@example.com").andExpect(status().isOk());
    }

    @Test
    @DisplayName("케이스만 다른 계정이 둘 있으면 정확히 친 쪽으로 들어간다")
    void exactMatchWinsOverCaseInsensitive() throws Exception {
        // DB 제약이 아직 정확 일치라(V12 보류) 이런 쌍이 이론상 존재할 수 있다.
        // 자기 주소를 정확히 친 사람이 남의 계정으로 들어가면 안 된다.
        createUser("dup@example.com", "소문자쪽", "010-4500-0002");
        createUser("DUP@example.com", "대문자쪽", "010-4500-0003");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"DUP@example.com\", \"password\": \"%s\"}"
                                .formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.nickname").value("대문자쪽"));

        // 어느 쪽과도 정확히 맞지 않으면 무시 검색으로 내려간다. 여럿이면 가장 오래된
        // 계정 — 무엇을 고르든 결과가 조회마다 달라지지만 않으면 된다
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"Dup@Example.com\", \"password\": \"%s\"}"
                                .formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.nickname").value("소문자쪽"));
    }

    // ── 유니크 규칙

    @Test
    @DisplayName("닉네임이 겹치면 409 NICKNAME_ALREADY_EXISTS")
    void duplicateNicknameIsRejected() throws Exception {
        createUser("first@example.com", "겹치는닉", "010-4100-0001");

        signup("second@example.com", PASSWORD, "겹치는닉", "010-4100-0002")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("닉네임 비교도 대소문자를 무시한다 — 사칭에 쓰이는 자리다")
    void nicknameComparisonIgnoresCase() throws Exception {
        createUser("origin@example.com", "Kickoff", "010-4110-0001");

        mockMvc.perform(get("/api/auth/availability").param("nickname", "kickoff"))
                .andExpect(jsonPath("$.nickname").value(false));

        signup("copycat@example.com", PASSWORD, "kickoff", "010-4110-0002")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_EXISTS"));
        signup("copycat2@example.com", PASSWORD, "KICKOFF", "010-4110-0003")
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("저장은 입력 표기 그대로다 — 서버가 대소문자를 바꾸지 않는다")
    void nicknameKeepsItsOriginalCasing() throws Exception {
        signup("keeper@example.com", PASSWORD, "KickOff", "010-4120-0001")
                .andExpect(status().isCreated())
                // 비교만 무시하고 표기는 건드리지 않는다. 소문자로 눕혀 저장하면
                // 사용자가 고른 모양이 사라진다
                .andExpect(jsonPath("$.user.nickname").value("KickOff"));
    }

    @Test
    @DisplayName("전화번호가 겹치면 409 PHONE_ALREADY_EXISTS")
    void duplicatePhoneIsRejected() throws Exception {
        createUser("firstphone@example.com", "폰주인", "010-4100-1111");

        signup("secondphone@example.com", PASSWORD, "다른닉", "010-4100-1111")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("여러 개가 겹치면 폼 위쪽 칸부터 알려준다 — FE 가 포커스를 돌린다")
    void firstConflictingFieldWins() throws Exception {
        createUser("all@example.com", "전부겹침", "010-4100-2222");

        signup("all@example.com", PASSWORD, "전부겹침", "010-4100-2222")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("PATCH 도 같은 규칙 — 다만 자기 자신의 값은 중복이 아니다")
    void patchAppliesTheSameRuleButAllowsOwnValues() throws Exception {
        User me = createUser("me@example.com", "내닉", "010-4200-0001");
        createUser("other@example.com", "남의닉", "010-4200-0002");

        patchMe(me, "{\"nickname\": \"남의닉\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_EXISTS"));

        // 자기 닉네임을 그대로 다시 보내는 건 통과해야 한다. 여기서 막으면 닉네임은
        // 그대로 두고 다른 필드만 고치는 요청이 자기 자신에 걸려 영영 안 된다
        patchMe(me, "{\"nickname\": \"내닉\", \"activityRegion\": \"서울\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityRegion").value("서울"));
    }

    // ── 사전 중복 확인

    @Test
    @DisplayName("요청한 키만 응답에 담긴다")
    void availabilityReturnsOnlyRequestedKeys() throws Exception {
        createUser("taken@example.com", "이미쓰는닉", "010-4300-0001");

        mockMvc.perform(get("/api/auth/availability")
                        .param("email", "taken@example.com")
                        .param("nickname", "안쓰는닉"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(false))
                .andExpect(jsonPath("$.nickname").value(true))
                // 묻지 않은 키가 null 로라도 나가면 FE 가 판단을 그르친다
                .andExpect(jsonPath("$.phone").doesNotExist());

        mockMvc.perform(get("/api/auth/availability").param("phone", "010-4300-0001"))
                .andExpect(jsonPath("$.phone").value(false))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.nickname").doesNotExist());
    }

    @Test
    @DisplayName("이메일 비교는 대소문자를 무시한다 — availability 와 가입이 같은 기준이다")
    void emailComparisonIgnoresCase() throws Exception {
        createUser("mixed@example.com", "대소문자", "010-4300-0002");

        mockMvc.perform(get("/api/auth/availability").param("email", "MiXeD@Example.COM"))
                .andExpect(jsonPath("$.email").value(false));

        // 여기가 핵심이다. 한쪽만 대소문자를 무시하면 "사용 불가라는데 가입은 되는" 값이
        // 생긴다 — 사용자는 같은 주소로 계정을 두 개 갖게 되고, 어느 쪽으로 로그인해야
        // 하는지 알 수 없다.
        signup("MiXeD@Example.COM", PASSWORD, "다른닉네임", "010-4300-0003")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("형식이 틀리면 400, 아무것도 안 물으면 400")
    void availabilityValidatesInput() throws Exception {
        mockMvc.perform(get("/api/auth/availability").param("email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/auth/availability").param("phone", "01043000003"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/auth/availability").param("nickname", "짧"))
                .andExpect(status().isBadRequest());

        // 빈 객체를 200 으로 주면 FE 가 "전부 사용 가능"으로 읽는다
        mockMvc.perform(get("/api/auth/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("인증 없이 열려 있다 — 가입 전에 부르는 API 다")
    void availabilityIsPublic() throws Exception {
        mockMvc.perform(get("/api/auth/availability").param("nickname", "아무도안씀"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(true));
    }

    @Test
    @DisplayName("사전 확인은 힌트일 뿐 — 최종 판정은 제출의 409 다")
    void availabilityIsOnlyAHint() throws Exception {
        mockMvc.perform(get("/api/auth/availability").param("nickname", "경합닉"))
                .andExpect(jsonPath("$.nickname").value(true));

        // 확인과 제출 사이에 다른 사람이 가져간 상황
        createUser("racer@example.com", "경합닉", "010-4400-0001");

        signup("late@example.com", PASSWORD, "경합닉", "010-4400-0002")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_EXISTS"));
    }

    // ── 헬퍼

    private ResultActions signup(String email, String password, String nickname, String phone)
            throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "nickname": "%s", "phone": "%s",
                         "termsAgreed": true}
                        """.formatted(email, password, nickname, phone)));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}"
                        .formatted(email, PASSWORD)));
    }

    private ResultActions patchMe(User user, String body) throws Exception {
        return mockMvc.perform(patch("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
