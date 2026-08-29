package com.kickoff.be.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthenticationTest extends IntegrationTestSupport {

    @Value("${jwt.secret}")
    private String secret;

    @Test
    @DisplayName("가입하면 토큰과 사용자 정보가 함께 온다")
    void signupReturnsTokenAndUser() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com","password":"pass1234!",
                                 "nickname":"김주장","phone":"010-1234-5678"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("new@example.com"))
                .andExpect(jsonPath("$.user.hasTeam").value(false))
                .andExpect(jsonPath("$.user.teamId").isEmpty());
    }

    @Test
    @DisplayName("팀을 만들면 내 정보의 hasTeam/teamId 가 채워진다")
    void meReflectsTeamOwnership() throws Exception {
        User user = createUser("owner@example.com", "김주장", "010-1111-1111");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasTeam").value(false));

        Team team = createTeam(user, "FC 새벽", "서울 강서구");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasTeam").value(true))
                .andExpect(jsonPath("$.teamId").value(team.getId()));
    }

    @Test
    @DisplayName("이메일이 중복되면 409 EMAIL_ALREADY_EXISTS")
    void duplicateEmailIsRejected() throws Exception {
        createUser("dup@example.com", "김주장", "010-1111-1111");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dup@example.com","password":"pass1234!",
                                 "nickname":"다른사람","phone":"010-2222-2222"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 LOGIN_FAILED — 없는 이메일도 같은 응답")
    void loginFailureDoesNotRevealWhetherEmailExists() throws Exception {
        createUser("real@example.com", "김주장", "010-1111-1111");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"real@example.com\",\"password\":\"wrongpass1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("토큰이 없거나 위조·만료면 인증이 필요한 경로에서 401")
    void protectedEndpointsRejectBadTokens() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer garbage.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignlySignedToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("인증이 필요 없는 경로는 낡은 토큰이 붙어 있어도 정상 응답한다")
    void publicEndpointsIgnoreBadTokens() throws Exception {
        User user = createUser("owner@example.com", "김주장", "010-1111-1111");
        Team team = createTeam(user, "FC 새벽", "서울 강서구");
        createPost(team, "토요일 아침 풋살");

        // FE 에 만료 토큰이 남아 있어도 목록 탐색은 막히지 않아야 한다 (계약서 §0)
        for (String token : new String[]{"garbage.token.here", expiredToken()}) {
            mockMvc.perform(get("/api/posts").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));

            mockMvc.perform(get("/api/teams/{id}", team.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    // 인증이 안 됐으니 내 팀으로 보이지 않는다
                    .andExpect(jsonPath("$.isMine").value(false));
        }
    }

    @Test
    @DisplayName("비로그인 상세에서는 isAuthor 가 false 이고 개인화 필드가 비어 있다")
    void anonymousDetailHasNoPersonalFields() throws Exception {
        User user = createUser("owner@example.com", "김주장", "010-1111-1111");
        Team team = createTeam(user, "FC 새벽", "서울 강서구");
        var post = createPost(team, "토요일 아침 풋살");

        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAuthor").value(false))
                .andExpect(jsonPath("$.myRequestStatus").isEmpty())
                .andExpect(jsonPath("$.contact").isEmpty())
                .andExpect(jsonPath("$.payment").isEmpty());
    }

    @Test
    @DisplayName("매핑되지 않은 경로는 500 이 아니라 404 로 나간다")
    void unmappedPathReturnsNotFound() throws Exception {
        User user = createUser("owner@example.com", "김주장", "010-1111-1111");

        mockMvc.perform(get("/api/posts/1/nope")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /**
     * 위 404 와 짝이다 (계약서 §0, v1.12.1). 경로는 맞는데 메서드가 틀린 경우.
     *
     * 500 으로 나가면 클라이언트가 자기 실수를 서버 장애로 읽는다 — 실제로 FE 가 accept 를
     * PATCH 로 부르고 INTERNAL_ERROR 를 받아 "배포 직후 매칭이 깨졌다"는 잘못된 가설을
     * 쫓은 일이 있었다. 그 실사례로 계약이 405 를 신설했다.
     */
    @Test
    @DisplayName("틀린 HTTP 메서드는 500 이 아니라 405 로 나간다")
    void wrongMethodReturnsMethodNotAllowed() throws Exception {
        User user = createUser("owner@example.com", "김주장", "010-1111-1111");
        Team team = createTeam(user, "FC 새벽", "서울 강서구");
        MatchPost post = createPost(team, "경기 구합니다");
        User applicantOwner = createUser("applicant@example.com", "이감독", "010-2222-2222");
        MatchRequest request = pendingRequest(post,
                createTeam(applicantOwner, "마포 유나이티드", "서울 마포구"));

        // 계약서는 POST 다. PATCH 로 부르면 405 여야 한다
        mockMvc.perform(patch("/api/requests/{id}/accept", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        // 그리고 정상 메서드는 그대로 동작해야 한다 — 405 를 붙이다 막아버리면 더 큰 일이다
        mockMvc.perform(post("/api/requests/{id}/accept", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("가입 요청 검증 실패는 필드별로 알려준다")
    void signupValidationReportsEveryField() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short",
                                 "nickname":"김","phone":"01012345678"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                // hasItems 인 이유: v1.16.0 부터 password 는 길이와 구성이 별개 규칙이라
                // 한 필드에 항목이 둘 실릴 수 있다. 여기서 보고 싶은 건 "빠진 필드가
                // 없는가"이지 "정확히 넷인가"가 아니다.
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(org.hamcrest.Matchers.hasItems(
                                "email", "password", "nickname", "phone")));
    }

    private SecretKey key(String value) {
        return Keys.hmacShaKeyFor(value.getBytes(StandardCharsets.UTF_8));
    }

    /** 서명은 올바르지만 이미 만료된 토큰. */
    private String expiredToken() {
        long past = System.currentTimeMillis() - 1000L;
        return Jwts.builder()
                .subject("1")
                .issuedAt(new Date(past - 1000L))
                .expiration(new Date(past))
                .signWith(key(secret), Jwts.SIG.HS256)
                .compact();
    }

    /** 형식은 멀쩡하지만 다른 키로 서명된 토큰. */
    private String foreignlySignedToken() {
        return Jwts.builder()
                .subject("1")
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(key("someone-elses-secret-key-0123456789-0123456789"), Jwts.SIG.HS256)
                .compact();
    }
}
