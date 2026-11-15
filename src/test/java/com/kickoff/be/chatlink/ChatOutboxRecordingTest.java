package com.kickoff.be.chatlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.chatlink.entity.ChatEventType;
import com.kickoff.be.chatlink.entity.ChatOutbox;
import com.kickoff.be.chatlink.service.ChatOutboxRecorder;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 채팅 서버에 알릴 일이 chat_outbox 에 적히는 자리들 (계약서 §10).
 *
 * 세 곳이다 — 매칭 취소(§6-2), 수락된 신청이 있는 글의 matchAt 수정, 회원 탈퇴로 인한 팀
 * 삭제(§3-4). <b>적히는 것과 보내는 것은 다른 일이다.</b> 여기서는 "원 동작과 같은
 * 트랜잭션에서 행이 남는가"만 보고, 보내기는 {@code ChatOutboxDeliveryTest} 가 본다.
 *
 * 쌓이는 쪽을 따로 못박는 이유: 전달이 안 되는 것은 로그로 드러나지만, <b>애초에 적히지
 * 않는 것은 아무 증상이 없다.</b> 채팅 서버는 옛 정보를 믿고 멀쩡히 돌고, 탈퇴한 사람의
 * 대화는 그쪽에 남는다.
 */
class ChatOutboxRecordingTest extends IntegrationTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    private ChatOutboxRecorder chatOutboxRecorder;

    private User postOwner;
    private User applicantOwner;
    private Team postTeam;
    private Team applicantTeam;
    private MatchPost post;

    @BeforeEach
    void setUpActors() {
        postOwner = createUser("post-owner@example.com", "김주장", "010-1111-1111");
        applicantOwner = createUser("applicant@example.com", "이감독", "010-2222-2222");
        postTeam = createTeam(postOwner, "FC 새벽", "서울 강서구");
        applicantTeam = createTeam(applicantOwner, "마포 유나이티드", "서울 마포구");
        post = createPost(postTeam, "토요일 아침 상대 구합니다");
    }

    // ── ① 매칭 취소 (계약서 §6-2 → ROOM_INVALIDATED)

    @Test
    @DisplayName("매칭을 취소하면 ROOM_INVALIDATED 가 쌓인다")
    void cancelingMatchRecordsRoomInvalidated() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);

        mockMvc.perform(post("/api/requests/{id}/cancel-match", accepted.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner)))
                .andExpect(status().isOk());

        ChatOutbox event = onlyEvent();
        assertThat(event.getEventType()).isEqualTo(ChatEventType.ROOM_INVALIDATED);
        assertThat(event.requestIdList()).containsExactly(accepted.getId());
        assertThat(event.teamIdList()).isEmpty();
        // 아직 아무도 보내지 않았다 — 바로 집어 갈 수 있는 상태여야 한다
        assertThat(event.getDeliveredAt()).isNull();
        assertThat(event.getAttempts()).isZero();
    }

    /**
     * 취소가 막히는 경우까지 이벤트가 남으면, 채팅 서버는 멀쩡한 방의 캐시를 버리고
     * 바뀐 것 없는 ROOM_STATE 를 쏜다. 같은 트랜잭션이라 롤백과 함께 사라져야 한다.
     */
    @Test
    @DisplayName("취소가 거절되면 아무것도 쌓이지 않는다")
    void rejectedCancelRecordsNothing() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);
        User outsider = createUser("outsider@example.com", "정주장", "010-3333-3333");

        mockMvc.perform(post("/api/requests/{id}/cancel-match", accepted.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden());

        assertThat(chatOutboxRepository.findAll()).isEmpty();
    }

    // ── ② matchAt 수정 (계약서 §10 "수락된 신청이 있는 글의 matchAt 수정")

    @Test
    @DisplayName("수락된 신청이 있는 글의 경기 일시를 바꾸면 ROOM_INVALIDATED 가 쌓인다")
    void changingMatchAtRecordsRoomInvalidated() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);

        patchMatchAt(post, OffsetDateTime.now().plusDays(10));

        ChatOutbox event = onlyEvent();
        assertThat(event.getEventType()).isEqualTo(ChatEventType.ROOM_INVALIDATED);
        assertThat(event.requestIdList()).containsExactly(accepted.getId());
    }

    /**
     * FE 는 수정 폼 전체를 보내므로 matchAt 이 바뀌지 않은 PATCH 가 흔하다. 그때마다
     * 이벤트를 쌓으면 바뀐 것 없는 ROOM_STATE 가 오간다 — 계약서가 "실제로 바뀐" 경우로
     * 한정한 이유다.
     */
    @Test
    @DisplayName("같은 경기 일시를 다시 보내면 쌓이지 않는다")
    void unchangedMatchAtRecordsNothing() throws Exception {
        acceptedRequest(post, applicantTeam);

        // <b>저장된 값</b>을 다시 보낸다. 메모리에 있는 엔티티는 나노초까지 들고 있지만
        // 칸이 timestamp(6) 이라 DB 를 거치면 마이크로초로 깎인다 — 그 둘을 비교하면 늘
        // "바뀐 것"이 된다. FE 는 GET 으로 받은(= 저장된) 값을 그대로 돌려보내므로 이쪽이
        // 실제 흐름이다.
        patchMatchAt(post, storedMatchAt());

        assertThat(chatOutboxRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("다른 필드만 고치면 쌓이지 않는다")
    void otherFieldPatchRecordsNothing() throws Exception {
        acceptedRequest(post, applicantTeam);

        mockMvc.perform(patch("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목만 바꿉니다\"}"))
                .andExpect(status().isOk());

        assertThat(chatOutboxRepository.findAll()).isEmpty();
    }

    /** 수락된 신청이 없으면 방이 없다. 대기 중 신청만 있는 글도 마찬가지다. */
    @Test
    @DisplayName("수락된 신청이 없는 글은 일시를 바꿔도 쌓이지 않는다")
    void matchAtChangeWithoutAcceptedRequestRecordsNothing() throws Exception {
        pendingRequest(post, applicantTeam);

        patchMatchAt(post, OffsetDateTime.now().plusDays(10));

        assertThat(chatOutboxRepository.findAll()).isEmpty();
    }

    // ── ③ 회원 탈퇴 (계약서 §3-4 → PURGE)

    @Test
    @DisplayName("탈퇴로 팀이 지워지면 PURGE 에 신청 id 와 팀 id 가 함께 담긴다")
    void withdrawalRecordsPurge() throws Exception {
        MatchRequest pending = pendingRequest(post, applicantTeam);

        withdraw(applicantOwner);

        ChatOutbox event = onlyEvent();
        assertThat(event.getEventType()).isEqualTo(ChatEventType.PURGE);
        assertThat(event.requestIdList()).containsExactly(pending.getId());
        assertThat(event.teamIdList()).containsExactly(applicantTeam.getId());
    }

    /**
     * 신청이 하나도 없어도 적는다 — 그 팀의 나가기 기록은 신청과 따로 남아 있을 수 있어서,
     * teamIds 만 담긴 PURGE 가 할 일이 있다. BE 의 탈퇴 처리도 같은 이유로 신청이 없는
     * 경우에 deleteChatLeaves 를 따로 부른다.
     */
    @Test
    @DisplayName("신청이 없는 팀이 탈퇴해도 팀 id 만 담긴 PURGE 가 쌓인다")
    void withdrawalWithoutRequestsStillRecordsPurge() throws Exception {
        withdraw(applicantOwner);

        ChatOutbox event = onlyEvent();
        assertThat(event.getEventType()).isEqualTo(ChatEventType.PURGE);
        assertThat(event.requestIdList()).isEmpty();
        assertThat(event.teamIdList()).containsExactly(applicantTeam.getId());
    }

    @Test
    @DisplayName("팀이 없는 계정이 탈퇴하면 아무것도 쌓이지 않는다")
    void withdrawalWithoutTeamRecordsNothing() throws Exception {
        User teamless = createUser("teamless@example.com", "최총무", "010-4444-4444");

        withdraw(teamless);

        assertThat(chatOutboxRepository.findAll()).isEmpty();
    }

    /**
     * 비밀번호가 틀려 탈퇴가 막히면 팀은 그대로 있다. 그런데 PURGE 만 나가면 채팅 서버는
     * <b>멀쩡히 쓰고 있는 방의 대화를 지운다</b> — 같은 트랜잭션에 둔 이유가 이것이다.
     */
    @Test
    @DisplayName("탈퇴가 막히면 PURGE 도 남지 않는다")
    void failedWithdrawalRecordsNothing() throws Exception {
        pendingRequest(post, applicantTeam);

        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"틀린비밀번호1!\"}"))
                .andExpect(status().isBadRequest());

        assertThat(chatOutboxRepository.findAll()).isEmpty();
    }

    // ── 담는 방식

    /**
     * id 목록은 한 칸(varchar 4000)에 들어간다. 넘치면 insert 가 실패하고 <b>탈퇴가 통째로
     * 롤백된다</b> — 채팅 뒷정리 때문에 사용자가 탈퇴를 못 하는 일은 없어야 한다. 그래서
     * 기록하는 쪽이 미리 쪼갠다. 각 행은 그 자체로 온전한 PURGE 다.
     */
    @Test
    @DisplayName("신청 id 가 많으면 여러 행으로 쪼개고, 팀 id 는 첫 행에만 싣는다")
    void longIdListIsSplitAcrossRows() {
        List<Long> many = java.util.stream.LongStream
                .rangeClosed(1, ChatOutbox.MAX_IDS_PER_ROW + 10).boxed().toList();

        transactionTemplate.executeWithoutResult(status ->
                chatOutboxRecorder.purge(many, List.of(3L)));

        List<ChatOutbox> events = chatOutboxRepository.findAll();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).requestIdList()).hasSize(ChatOutbox.MAX_IDS_PER_ROW);
        assertThat(events.get(0).teamIdList()).containsExactly(3L);
        assertThat(events.get(1).requestIdList()).hasSize(10);
        assertThat(events.get(1).teamIdList()).isEmpty();
        // 쪼개도 전부가 한 번씩은 담겨야 한다
        assertThat(events.stream().flatMap(event -> event.requestIdList().stream()).toList())
                .containsExactlyElementsOf(many);
    }

    /**
     * 트랜잭션 없이 불리면 "원 동작과 함께 커밋된다"는 보장이 조용히 사라진다. 새 호출
     * 지점이 생겼을 때 기동이 아니라 그 호출에서 바로 드러나야 한다.
     */
    @Test
    @DisplayName("트랜잭션 밖에서 부르면 거부한다")
    void recordingOutsideTransactionFails() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> chatOutboxRecorder.roomInvalidated(List.of(7L)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    private OffsetDateTime storedMatchAt() {
        return postRepository.findById(post.getId()).orElseThrow().getMatchAt();
    }

    private void patchMatchAt(MatchPost target, OffsetDateTime matchAt) throws Exception {
        mockMvc.perform(patch("/api/posts/{id}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchAt\":\"%s\"}"
                                .formatted(matchAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))))
                .andExpect(status().isOk());
    }

    private void withdraw(User user) throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());
    }

    private ChatOutbox onlyEvent() {
        List<ChatOutbox> events = chatOutboxRepository.findAll();
        assertThat(events).hasSize(1);
        return events.get(0);
    }
}
