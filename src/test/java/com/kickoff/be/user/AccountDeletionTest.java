package com.kickoff.be.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.chat.entity.ChatLeave;
import com.kickoff.be.chat.entity.ChatMessage;
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
 * 회원 탈퇴 (계약서 §3-4, v1.23.0).
 *
 * <b>이 기능이 깨지는 방식은 둘이고, 둘 다 테스트로만 잡힌다.</b>
 *
 * 하나, <b>삭제 순서를 어기면 500 이 난다.</b> 외래키가 걸린 자식을 부모보다 늦게 지우면
 * 그 자리에서 막히는데, 사용자에게는 "탈퇴가 안 된다"만 보이고 이유가 안 보인다.
 * 그래서 얽힐 대로 얽힌 계정 하나를 만들어 놓고 지워 본다 — 팀·글·신청·리뷰·전적·채팅·
 * 문의·소속·가입신청이 전부 달린 계정이다.
 *
 * 둘, <b>남의 것까지 지우면 상대 팀 화면이 깨진다.</b> 특히 상대 팀이 자기 페이지에 가진
 * 수동 전적은 살아남아야 한다 — 그 팀은 실제로 그 경기를 했고, 우리가 탈퇴했다고 그
 * 기록까지 없앨 권리는 없다. 이건 에러가 아니라 <b>남의 화면에서 조용히 사라지는</b>
 * 종류라 더 나쁘다.
 */
class AccountDeletionTest extends IntegrationTestSupport {

    private User owner;
    private User opponent;
    private User member;
    private Team ownerTeam;
    private Team opponentTeam;

    @BeforeEach
    void setUpAccounts() {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        opponent = createUser("opponent@example.com", "이감독", "010-2222-2222");
        member = createUser("member@example.com", "박팀원", "010-3333-3333");
        ownerTeam = createTeam(owner, "FC 새벽", "서울 강서구");
        opponentTeam = createTeam(opponent, "마포 유나이티드", "서울 마포구");
    }

    // ── 인증·권한

    @Test
    @DisplayName("미인증은 401")
    void anonymousUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 400 PASSWORD_MISMATCH — 401 이 아니다")
    void wrongPasswordIs400() throws Exception {
        // 401 을 주면 FE 인터셉터가 세션 만료로 오인해 refresh 를 탄다. 사용자는
        // 비밀번호를 틀린 줄도 모른 채 화면이 튄다.
        deleteMe(owner, "\"password\":\"wrong-pass1!\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        assertThat(userRepository.findById(owner.getId())).isPresent();
    }

    @Test
    @DisplayName("비밀번호를 아예 안 보내도 400 — 이메일 계정은 확인이 필수다")
    void missingPasswordIs400() throws Exception {
        deleteMe(owner, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
    }

    @Test
    @DisplayName("소셜 계정은 body 없이 탈퇴한다 — 요구하면 영영 못 나간다")
    void socialAccountDeletesWithoutBody() throws Exception {
        User social = createUserWithoutPhone("social@example.com", "소셜사용자");
        // 소셜 전용 계정은 비밀번호가 없다. 여기서 비밀번호를 요구하면 그 사용자는
        // 탈퇴할 방법이 아예 없어진다.
        social.updatePassword(null);
        userRepository.save(social);

        deleteMe(social, null).andExpect(status().isNoContent());
        assertThat(userRepository.findById(social.getId())).isEmpty();
    }

    // ── 차단 조건

    @Test
    @DisplayName("내 글에 잡힌 예정 매칭이 있으면 409 — 내가 글쓴이인 경우")
    void blockedWhenMyPostHasUpcomingMatch() throws Exception {
        MatchPost post = createPost(ownerTeam, "토요일 경기", OffsetDateTime.now().plusDays(3), true);
        acceptedRequest(post, opponentTeam);

        deleteMe(owner, password())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_MATCH_EXISTS"));

        assertThat(userRepository.findById(owner.getId())).isPresent();
    }

    @Test
    @DisplayName("남의 글에 내가 신청해 잡힌 매칭도 409 — 방향만 다를 뿐 같은 일이다")
    void blockedWhenIAmTheApplicant() throws Exception {
        // 이걸 빠뜨리면 신청 방향일 때만 상대의 확정 매칭이 소리 없이 증발한다.
        // 상대 팀은 경기 당일에야 안다.
        MatchPost theirPost = createPost(opponentTeam, "남의 글",
                OffsetDateTime.now().plusDays(3), true);
        acceptedRequest(theirPost, ownerTeam);

        deleteMe(owner, password())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_MATCH_EXISTS"));
    }

    @Test
    @DisplayName("지난 매칭은 차단 사유가 아니다")
    void pastMatchDoesNotBlock() throws Exception {
        MatchPost past = createPost(ownerTeam, "지난 경기",
                OffsetDateTime.now().minusDays(3), true);
        acceptedRequest(past, opponentTeam);

        deleteMe(owner, password()).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PENDING 신청도 차단 사유가 아니다")
    void pendingRequestDoesNotBlock() throws Exception {
        MatchPost post = createPost(ownerTeam, "모집중", OffsetDateTime.now().plusDays(3), true);
        pendingRequest(post, opponentTeam);

        deleteMe(owner, password()).andExpect(status().isNoContent());
    }

    // ── 얽힌 계정 지우기 (500 으로 막히는 경로가 없는가)

    @Test
    @DisplayName("팀·글·신청·리뷰·전적·채팅·문의가 다 달린 계정도 지워진다")
    void deletesFullyEntangledAccount() throws Exception {
        // 지난 경기의 수락된 매칭 — 리뷰·전적·채팅이 달릴 수 있는 유일한 조합이다
        MatchPost past = createPost(ownerTeam, "지난 경기",
                OffsetDateTime.now().minusDays(3), true);
        MatchRequest done = acceptedRequest(past, opponentTeam);

        // 양 팀이 서로 리뷰를 쓴다
        writeReview(owner, done.getId(), 5);
        writeReview(opponent, done.getId(), 4);
        // 양 팀이 그 경기를 전적으로 기록한다 — 상대 것은 살아남아야 한다
        writeRecord(owner, done.getId());
        writeRecord(opponent, done.getId());
        // 채팅도 오간다
        chatHistory(done);
        // 내 팀에 팀원이 있고, 나는 남의 팀에 가입 신청을 넣었고, 고객센터 문의도 했다
        addMember(owner, ownerTeam.getId(), "새팀원");
        applyToJoin(member, ownerTeam.getId());
        sendSupport(owner, "문의 드립니다");

        deleteMe(owner, password()).andExpect(status().isNoContent());

        assertThat(userRepository.findById(owner.getId())).isEmpty();
        assertThat(teamRepository.findById(ownerTeam.getId())).isEmpty();
    }

    @Test
    @DisplayName("상대 팀의 수동 전적은 살아남고 연결만 끊긴다")
    void opponentRecordSurvivesWithRequestDetached() throws Exception {
        MatchPost past = createPost(ownerTeam, "지난 경기",
                OffsetDateTime.now().minusDays(3), true);
        MatchRequest done = acceptedRequest(past, opponentTeam);
        writeRecord(opponent, done.getId());

        deleteMe(owner, password()).andExpect(status().isNoContent());

        // 상대 팀은 실제로 그 경기를 했다. 우리가 탈퇴했다고 그 기록까지 없앨 권리는 없다.
        var survived = teamRecordRepository.findAll();
        assertThat(survived).hasSize(1);
        assertThat(survived.get(0).getTeam().getId()).isEqualTo(opponentTeam.getId());
        // 다만 사라진 매칭을 가리킬 수는 없다
        assertThat(survived.get(0).getRequest()).isNull();
    }

    @Test
    @DisplayName("상대 팀 페이지가 탈퇴 뒤에도 열린다")
    void opponentPageStillWorks() throws Exception {
        MatchPost past = createPost(ownerTeam, "지난 경기",
                OffsetDateTime.now().minusDays(3), true);
        MatchRequest done = acceptedRequest(past, opponentTeam);
        writeReview(owner, done.getId(), 5);
        writeRecord(opponent, done.getId());

        deleteMe(owner, password()).andExpect(status().isNoContent());

        // 계약이 못박은 두 원칙 중 하나 — "상대 팀 화면이 깨지지 않을 것"
        mockMvc.perform(get("/api/teams/" + opponentTeam.getId())).andExpect(status().isOk());
        mockMvc.perform(get("/api/teams/" + opponentTeam.getId() + "/records"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/teams/" + opponentTeam.getId() + "/reviews"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/teams/" + opponentTeam.getId() + "/members"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("내가 쓴 리뷰가 사라지면 상대 팀 평점이 재계산된다")
    void opponentRatingIsRecalculated() throws Exception {
        MatchPost past = createPost(ownerTeam, "지난 경기",
                OffsetDateTime.now().minusDays(3), true);
        MatchRequest done = acceptedRequest(past, opponentTeam);
        writeReview(owner, done.getId(), 5);

        mockMvc.perform(get("/api/teams/" + opponentTeam.getId()))
                .andExpect(jsonPath("$.reviewCount").value(1));

        deleteMe(owner, password()).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams/" + opponentTeam.getId()))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.averageRating").doesNotExist());
    }

    @Test
    @DisplayName("팀이 없는 계정도 지워진다 — 남의 팀 소속·문의만 있는 경우")
    void deletesAccountWithoutTeam() throws Exception {
        addMember(opponent, opponentTeam.getId(), "박팀원");
        applyToJoin(member, ownerTeam.getId());
        sendSupport(member, "문의합니다");

        deleteMe(member, password()).andExpect(status().isNoContent());
        assertThat(userRepository.findById(member.getId())).isEmpty();
        // 남의 팀은 그대로다
        assertThat(teamRepository.findById(opponentTeam.getId())).isPresent();
    }

    // ── 탈퇴 후

    @Test
    @DisplayName("같은 전화번호로 다시 가입할 수 있다 — 유니크가 풀린다")
    void phoneCanBeReused() throws Exception {
        deleteMe(owner, password()).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "again@example.com", "password": "pass1234!",
                                 "nickname": "다시가입", "phone": "010-1111-1111",
                                 "termsAgreed": true}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("전화 인증 이력은 남는다 — 부정 이용 방지 목적 (방침 §3-2)")
    void phoneVerificationHistorySurvives() throws Exception {
        mockMvc.perform(post("/api/auth/phone/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1111-1111\"}"))
                .andExpect(status().isNoContent());
        long before = phoneVerificationRepository.count();

        deleteMe(owner, password()).andExpect(status().isNoContent());

        assertThat(phoneVerificationRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("탈퇴한 토큰으로 다시 부르면 401 — 멱등이 아니다")
    void deletedTokenIsUnauthorized() throws Exception {
        String token = bearer(owner);
        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + password() + "}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + password() + "}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 헬퍼

    private String password() {
        return "\"password\":\"" + PASSWORD + "\"";
    }

    private ResultActions deleteMe(User who, String body) throws Exception {
        var request = delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(who));
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content("{" + body + "}");
        }
        return mockMvc.perform(request);
    }

    private void writeReview(User who, long requestId, int rating) throws Exception {
        mockMvc.perform(post("/api/requests/" + requestId + "/review")
                        .header(HttpHeaders.AUTHORIZATION, bearer(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":" + rating + ",\"comment\":\"좋았습니다\"}"))
                .andExpect(status().isCreated());
    }

    private void writeRecord(User who, long requestId) throws Exception {
        mockMvc.perform(post("/api/requests/" + requestId + "/record")
                        .header(HttpHeaders.AUTHORIZATION, bearer(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ourScore\":3,\"opponentScore\":1}"))
                .andExpect(status().isCreated());
    }

    /**
     * 채팅은 API 로 만들 수 없다 — 리뷰를 쓰려면 지난 경기여야 하는데, 지난 경기의
     * 채팅은 닫혀 있다(409 CHAT_CLOSED). "경기 전에 나눈 대화가 남아 있는 방"은 실제로
     * 존재하는 상태라, 엔티티로 직접 만든다.
     */
    private void chatHistory(MatchRequest request) {
        chatMessageRepository.save(ChatMessage.text(request, ownerTeam, "안녕하세요"));
        chatMessageRepository.save(ChatMessage.text(request, opponentTeam, "네 반갑습니다"));
        // 나가기 기록도 삭제 경로에 걸린다 — 여기가 빠지면 외래키에서 막힌다
        chatLeaveRepository.save(ChatLeave.of(request, ownerTeam));
    }

    private void addMember(User teamOwner, long teamId, String name) throws Exception {
        mockMvc.perform(post("/api/teams/" + teamId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teamOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated());
    }

    private void applyToJoin(User who, long teamId) throws Exception {
        mockMvc.perform(post("/api/teams/" + teamId + "/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"가입하고 싶습니다\"}"))
                .andExpect(status().isCreated());
    }

    private void sendSupport(User who, String content) throws Exception {
        mockMvc.perform(post("/api/support/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated());
    }
}
