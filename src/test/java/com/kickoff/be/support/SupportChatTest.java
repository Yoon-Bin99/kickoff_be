package com.kickoff.be.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.entity.SupportSender;
import com.kickoff.be.user.entity.User;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 고객센터 문의 — 3단 응대 (계약서 §7-1, v1.22.0).
 *
 * 이 기능이 조용히 틀리는 자리는 <b>분기</b>다. AI 가 답해야 할 때 침묵하거나, 침묵해야 할
 * 때 끼어들거나, 운영자 알림이 엉뚱한 쪽으로 간다 — 전부 200 이 나가고 로그도 조용하다.
 * 특히 <b>운영자 모드에서 AI 가 끼어드는 것</b>은 사용자가 사람과 이야기하는 줄 아는
 * 상태라 가장 나쁘다.
 */
class SupportChatTest extends IntegrationTestSupport {

    private User user;
    private User other;
    private User operator;

    @BeforeEach
    void setUpUsers() {
        user = createUser("member@example.com", "김감독", "010-1111-1111");
        other = createUser("other@example.com", "이감독", "010-2222-2222");
        // application-test.yaml 의 operator-email 과 같아야 운영자로 판정된다
        operator = createUser("operator@kickoff.test", "운영자", "010-9999-9999");
        aiClient.reset();
        pushClient.reset();
    }

    // ── 1단계 FAQ

    @Test
    @DisplayName("FAQ 는 문서에서 읽어 그대로 내려준다 — 서버 저장도 AI 호출도 없다")
    void faqComesFromDocument() throws Exception {
        mockMvc.perform(get("/api/support/faq")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].question").isNotEmpty())
                .andExpect(jsonPath("$[0].answer").isNotEmpty());

        // 무료·즉답이라는 게 이 단계의 존재 이유다. 하나라도 어기면 값이 없어진다.
        assertThat(aiClient.calls()).isEmpty();
        assertThat(supportMessageRepository.count()).isZero();
    }

    @Test
    @DisplayName("FAQ 도 로그인은 필요하다")
    void faqRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/support/faq")).andExpect(status().isUnauthorized());
    }

    // ── 2단계 AI

    @Test
    @DisplayName("보내면 AI 가 답하고, 다음 폴링에 잡힌다")
    void aiRepliesOnNextPoll() throws Exception {
        aiClient.willAnswer("매칭은 홈에서 신청하시면 됩니다.");

        send(user, "매칭 어떻게 하나요?").andExpect(status().isCreated())
                .andExpect(jsonPath("$.sender").value("USER"));

        awaitSenders(user, SupportSender.USER, SupportSender.AI);
        assertThat(lastContent(user)).isEqualTo("매칭은 홈에서 신청하시면 됩니다.");
    }

    @Test
    @DisplayName("AI 에게 서비스 지식이 실려 간다 — 비어도 그럴듯하게 답하므로 여기서 본다")
    void systemPromptCarriesKnowledge() throws Exception {
        send(user, "환불해 주세요").andExpect(status().isCreated());
        await().atMost(Duration.ofSeconds(5)).until(() -> !aiClient.calls().isEmpty());

        String prompt = aiClient.last().systemPrompt();
        // 지식이 안 실리면 AI 는 서비스를 모르는 채로 답한다. 200 이고 말도 그럴듯해서
        // 화면만 봐서는 알 수 없다.
        assertThat(prompt).contains("킥오프", "11대11");
        // 하드 제약이 빠지면 AI 가 환불·제재를 약속할 수 있다 (계약서 §7-1)
        assertThat(prompt).contains("환불");
    }

    @Test
    @DisplayName("대화 이력이 오래된 것부터 넘어간다 — 뒤집히면 AI 가 거꾸로 읽는다")
    void historyIsChronological() throws Exception {
        send(user, "첫 번째 질문").andExpect(status().isCreated());
        awaitCalls(1);
        send(user, "두 번째 질문").andExpect(status().isCreated());
        awaitCalls(2);

        List<String> contents = aiClient.last().history().stream()
                .map(t -> t.content()).toList();
        assertThat(contents).containsSubsequence("첫 번째 질문", "두 번째 질문");
        // 마지막이 방금 보낸 말이어야 한다 — 아니면 AI 가 이전 질문에 답한다
        assertThat(contents.get(contents.size() - 1)).isEqualTo("두 번째 질문");
    }

    @Test
    @DisplayName("AI 가 실패하면 강등 안내를 남긴다 — 사용자의 말은 그대로 남는다")
    void aiFailureDegradesGracefully() throws Exception {
        aiClient.willFail();

        send(user, "도와주세요").andExpect(status().isCreated());

        awaitSenders(user, SupportSender.USER, SupportSender.AI);
        assertThat(lastContent(user)).contains("운영자");
        // AI 가 못 답한다고 사용자의 문의가 사라지면 안 된다 — 운영자가 읽어야 한다
        assertThat(supportMessageRepository.findAll().stream()
                .anyMatch(m -> m.getContent().equals("도와주세요"))).isTrue();
    }

    @Test
    @DisplayName("분당 5회를 넘으면 429 — 저장도 안 된다")
    void rateLimited() throws Exception {
        for (int i = 1; i <= 5; i++) {
            send(user, "질문 " + i).andExpect(status().isCreated());
        }
        send(user, "여섯 번째")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SUPPORT_RATE_LIMITED"));

        // 막힌 메시지가 저장되면 답 없는 말이 방에 쌓이고, 한도가 풀려도 답이 안 달린다
        assertThat(supportMessageRepository.findAll().stream()
                .noneMatch(m -> m.getContent().equals("여섯 번째"))).isTrue();
    }

    @Test
    @DisplayName("한도는 사용자별이다 — 남의 문의가 내 한도를 깎으면 안 된다")
    void rateLimitIsPerUser() throws Exception {
        for (int i = 1; i <= 5; i++) {
            send(user, "질문 " + i).andExpect(status().isCreated());
        }
        send(other, "저도 질문이요").andExpect(status().isCreated());
    }

    // ── 3단계 운영자 연결

    @Test
    @DisplayName("에스컬레이트하면 그 뒤로 AI 가 답하지 않는다")
    void aiStaysSilentAfterEscalation() throws Exception {
        escalate(user).andExpect(status().isNoContent());
        aiClient.reset();

        send(user, "사람과 이야기하고 싶어요").andExpect(status().isCreated());

        // 여기서 AI 가 끼어들면 사용자는 사람과 이야기하는 줄 알고 있다
        assertThat(aiClient.calls()).isEmpty();
        awaitSenders(user, SupportSender.USER);
    }

    @Test
    @DisplayName("두 번 눌러도 204 — FE 가 버튼을 상시 노출한다")
    void escalateIsIdempotent() throws Exception {
        escalate(user).andExpect(status().isNoContent());
        escalate(user).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("운영자 모드에서는 사용자 메시지에 운영자 푸시가 간다")
    void operatorGetsPushInOperatorMode() throws Exception {
        registerToken(operator, "ExponentPushToken[oooooooooooooooooooooo]");
        escalate(user).andExpect(status().isNoContent());
        pushClient.reset();

        send(user, "연락이 안 됩니다").andExpect(status().isCreated());

        await().atMost(Duration.ofSeconds(5)).until(() -> !pushClient.sent().isEmpty());
        assertThat(pushClient.last().title()).isEqualTo("고객센터 문의");
        assertThat(pushClient.last().body()).contains("김감독", "연락이 안 됩니다");
        assertThat(pushClient.last().data()).containsEntry("type", "SUPPORT_MESSAGE");
        // 탭했을 때 열 방의 주인이다 — 수신자(운영자)가 아니다
        assertThat(pushClient.last().data()).containsEntry("userId", user.getId());
    }

    @Test
    @DisplayName("AI 응대 중에는 운영자 푸시를 보내지 않는다")
    void noOperatorPushWhileAiHandles() throws Exception {
        registerToken(operator, "ExponentPushToken[oooooooooooooooooooooo]");
        pushClient.reset();

        send(user, "간단한 질문이요").andExpect(status().isCreated());
        awaitSenders(user, SupportSender.USER, SupportSender.AI);

        // 여기서 보내면 운영자가 AI 가 처리 중인 문의까지 전부 알림으로 받는다
        assertThat(pushClient.sent()).isEmpty();
    }

    // ── 운영자 쪽

    @Test
    @DisplayName("운영자가 아니면 문의함은 전부 403")
    void inboxIsOperatorOnly() throws Exception {
        mockMvc.perform(get("/api/support/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/support/rooms/" + user.getId() + "/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/support/rooms/" + user.getId() + "/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"남의 방에 답장\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("문의함에는 메시지가 있는 방만 나온다")
    void inboxSkipsEmptyRooms() throws Exception {
        // other 는 조회만 해서 방이 생긴다 — 문의를 시작하지 않았다
        mockMvc.perform(get("/api/support/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk());
        send(user, "문의 드립니다").andExpect(status().isCreated());

        // 미리보기는 <b>가장 최근 메시지</b>다 — AI 가 답했으면 그 답이 보인다.
        // 운영자가 문의함을 열었을 때 "AI 가 어디까지 답했는지"가 먼저 보이는 게 맞다.
        awaitSenders(user, SupportSender.USER, SupportSender.AI);
        mockMvc.perform(get("/api/support/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(user.getId()))
                .andExpect(jsonPath("$[0].nickname").value("김감독"))
                .andExpect(jsonPath("$[0].lastMessage.sender").value("AI"));
    }

    @Test
    @DisplayName("운영자가 답하면 그 사용자에게 푸시가 가고, 이후 AI 는 침묵한다")
    void operatorReplySilencesAi() throws Exception {
        registerToken(user, "ExponentPushToken[uuuuuuuuuuuuuuuuuuuuuu]");
        send(user, "문의 드립니다").andExpect(status().isCreated());
        awaitSenders(user, SupportSender.USER, SupportSender.AI);
        pushClient.reset();
        aiClient.reset();

        mockMvc.perform(post("/api/support/rooms/" + user.getId() + "/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"확인 후 안내드리겠습니다.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sender").value("OPERATOR"));

        await().atMost(Duration.ofSeconds(5)).until(() -> !pushClient.sent().isEmpty());
        assertThat(pushClient.last().title()).isEqualTo("킥오프 고객센터");

        // 운영자가 답했는데 다음 사용자 메시지에 AI 가 끼어들면, 사람이 보고 있는 방에
        // 기계가 대신 답한다. 에스컬레이트를 안 눌렀어도 그래서는 안 된다.
        send(user, "감사합니다").andExpect(status().isCreated());
        assertThat(aiClient.calls()).isEmpty();
    }

    @Test
    @DisplayName("없는 사용자의 방은 404")
    void unknownRoomIs404() throws Exception {
        mockMvc.perform(get("/api/support/rooms/999999/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ── 방과 폴링

    @Test
    @DisplayName("남의 방은 볼 수 없다 — 사용자 API 는 언제나 자기 방이다")
    void usersOnlySeeTheirOwnRoom() throws Exception {
        send(user, "내 문의").andExpect(status().isCreated());

        mockMvc.perform(get("/api/support/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isEmpty());
    }

    @Test
    @DisplayName("after 로 그 이후만 받는다")
    void pollingUsesCursor() throws Exception {
        long first = idOf(send(user, "첫 메시지").andExpect(status().isCreated()));
        awaitSenders(user, SupportSender.USER, SupportSender.AI);
        send(user, "둘째 메시지").andExpect(status().isCreated());

        mockMvc.perform(get("/api/support/chat").param("after", String.valueOf(first))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].id").value(org.hamcrest.Matchers
                        .greaterThan((int) first)));
    }

    // ── operatorMode (계약서 §7-1 확정판)

    @Test
    @DisplayName("operatorMode 는 escalate 직후 바로 true — 운영자 답이 없어도")
    void operatorModeIsTrueRightAfterEscalation() throws Exception {
        mockMvc.perform(get("/api/support/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.operatorMode").value(false));

        escalate(user).andExpect(status().isNoContent());

        // 여기가 이 필드의 존재 이유다. 말풍선으로 추론하면 <b>운영자를 불렀지만 아직
        // 답이 없는 구간</b>이 안 보이고, 화면에는 "운영자 연결하기"가 계속 떠 있다 —
        // 사용자는 이미 부른 버튼을 또 누른다.
        mockMvc.perform(get("/api/support/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.operatorMode").value(true))
                .andExpect(jsonPath("$.messages").isEmpty());
    }

    @Test
    @DisplayName("운영자가 답장하면 escalate 없이도 operatorMode 가 true 가 된다")
    void operatorReplyTurnsOnOperatorMode() throws Exception {
        send(user, "문의 드립니다").andExpect(status().isCreated());
        awaitSenders(user, SupportSender.USER, SupportSender.AI);

        mockMvc.perform(post("/api/support/rooms/" + user.getId() + "/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"확인했습니다.\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/support/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.operatorMode").value(true));
    }

    @Test
    @DisplayName("운영자가 보는 방에도 operatorMode 가 실린다 — 형태가 같다")
    void operatorSeesRoomState() throws Exception {
        send(user, "문의 드립니다").andExpect(status().isCreated());
        escalate(user).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/support/rooms/" + user.getId() + "/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorMode").value(true));
    }

    @Test
    @DisplayName("운영자가 남의 방을 열어 봐도 빈 방이 생기지 않는다")
    void operatorViewDoesNotCreateRoom() throws Exception {
        mockMvc.perform(get("/api/support/rooms/" + other.getId() + "/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorMode").value(false));

        // 문의를 시작한 적 없는 사용자의 방이 생기면 안 된다
        assertThat(supportRoomRepository.findByUser_Id(other.getId())).isEmpty();
    }

    @Test
    @DisplayName("문의방에는 chatOpen 이 없다 — 만료가 없어서 항상 열려 있다")
    void noChatOpenField() throws Exception {
        String body = mockMvc.perform(get("/api/support/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("chatOpen");
    }

    @Test
    @DisplayName("내용은 1~500자")
    void contentIsValidated() throws Exception {
        send(user, "").andExpect(status().isBadRequest());
        send(user, "가".repeat(501)).andExpect(status().isBadRequest());
        send(user, "가".repeat(500)).andExpect(status().isCreated());
    }

    // ── 헬퍼

    private ResultActions send(User who, String content) throws Exception {
        return mockMvc.perform(post("/api/support/chat")
                .header(HttpHeaders.AUTHORIZATION, bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}"));
    }

    private ResultActions escalate(User who) throws Exception {
        return mockMvc.perform(post("/api/support/chat/escalate")
                .header(HttpHeaders.AUTHORIZATION, bearer(who)));
    }

    private void awaitCalls(int count) {
        await().atMost(Duration.ofSeconds(5)).until(() -> aiClient.calls().size() >= count);
    }

    /** AI 답변이 비동기라 폴링으로 기다린다 — 계약서가 "다음 폴링에 잡힘"이라고 적었다. */
    private void awaitSenders(User owner, SupportSender... expected) {
        await().atMost(Duration.ofSeconds(5)).until(() -> senders(owner).size() >= expected.length);
        assertThat(senders(owner)).containsExactly(expected);
    }

    private List<SupportSender> senders(User owner) {
        return supportMessageRepository.findAll().stream()
                .filter(m -> m.getUser().getId().equals(owner.getId()))
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(m -> m.getSender())
                .toList();
    }

    private String lastContent(User owner) {
        List<com.kickoff.be.support.entity.SupportMessage> mine =
                supportMessageRepository.findAll().stream()
                        .filter(m -> m.getUser().getId().equals(owner.getId()))
                        .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                        .toList();
        return mine.get(mine.size() - 1).getContent();
    }

    private void registerToken(User who, String token) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/users/me/push-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
    }
}
