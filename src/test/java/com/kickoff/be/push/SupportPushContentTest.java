package com.kickoff.be.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.push.dto.SupportPushEvent;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.support.service.SupportService;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 고객센터 푸시에는 <b>내용이 한 글자도 실리지 않는다</b> (계약서 §7-1, v1.38.3).
 *
 * 이 경로로 나가는 통지에는 신고 처리 결과·임시 가림·이용 제한이 들어 있다. 그대로
 * 실으면 제재 사실이 잠금 화면에 뜨고, 신고 결과를 그렇게 알리면 신고자를 숨겨 온
 * 설계와 어긋난다. 게다가 푸시는 국외 업체(Expo)를 거친다.
 *
 * <b>문구가 아니라 "내용이 없음"을 본다.</b> 문구는 바뀔 수 있지만, 통지 원문의 어느
 * 조각도 본문에 들어가지 않는다는 성질은 바뀌면 안 된다.
 */
class SupportPushContentTest extends IntegrationTestSupport {

    /** 실제 제재 통지 문구 그대로다 (report-notice.properties 의 reported.suspended). */
    private static final String SUSPENSION_NOTICE = """
            회원님의 계정이 운영 정책 위반으로 7일 동안 이용이 제한됐습니다.
            사유: 욕설·비방·괴롭힘
            이의가 있으면 14일 안에 이 고객센터로 알려 주세요.""";

    @Autowired
    private SupportService supportService;

    private User user;

    @BeforeEach
    void setUpUser() throws Exception {
        user = createUser("user@example.com", "김감독", "010-1111-1111");
        mockMvc.perform(put("/api/users/me/push-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":\"ExponentPushToken[uuuuuuuuuuuuuuuuuuuuuu]\"}"))
                .andExpect(status().isNoContent());
        pushClient.reset();
    }

    @Test
    @DisplayName("제재 통지 푸시에 사유도 기간도 제재 사실도 실리지 않는다")
    void suspensionNoticeCarriesNoContent() {
        supportService.notice(user, SUSPENSION_NOTICE);

        assertThat(pushClient.sent()).hasSize(1);
        assertThat(pushClient.last().title()).isEqualTo("킥오프 고객센터");
        assertThat(pushClient.last().body())
                .isEqualTo("새 알림이 있습니다. 앱에서 확인해 주세요")
                .doesNotContain("이용이 제한", "욕설", "7일", "운영 정책", "위반", "이의");
    }

    /** 통지 원문은 <b>앱 안에서</b> 읽는다 — 푸시에서 빼는 대신 사라지면 안 된다. */
    @Test
    @DisplayName("통지 원문은 문의방에 그대로 남는다")
    void theNoticeItselfIsStillDelivered() {
        supportService.notice(user, SUSPENSION_NOTICE);

        assertThat(supportMessageRepository.findAll())
                .singleElement()
                .satisfies(message -> assertThat(message.getContent())
                        .isEqualTo(SUSPENSION_NOTICE));
    }

    /** 탭하면 자기 문의방이 열려야 한다 — 내용을 뺀다고 딥링크까지 빼면 안 된다. */
    @Test
    @DisplayName("data 는 그대로다")
    void deepLinkIsUnchanged() {
        supportService.notice(user, SUSPENSION_NOTICE);

        assertThat(pushClient.last().data())
                .containsEntry("type", "SUPPORT_MESSAGE")
                .containsEntry("userId", user.getId());
    }

    /**
     * 세 방향 모두 본문이 <b>고정 문구</b>다. 문구를 만드는 자리에서 한 번 더 못박는다 —
     * 나중에 누가 "내용을 조금만" 붙이면 여기서 걸린다.
     */
    @Test
    @DisplayName("문구를 만드는 자리에 메시지 내용을 넣을 자리가 없다")
    void everyBodyIsAFixedPhrase() {
        assertThat(SupportPushEvent.toOperator(1L, 2L, "김감독").body())
                .isEqualTo("김감독님의 새 문의가 있습니다");
        assertThat(SupportPushEvent.toUser(2L).body())
                .isEqualTo("문의에 답변이 도착했습니다");
        assertThat(SupportPushEvent.notice(2L).body())
                .isEqualTo("새 알림이 있습니다. 앱에서 확인해 주세요");
    }

    /**
     * 토큰이 없으면 Expo 로 발송 요청 자체를 하지 않는다 (계약서 §8).
     *
     * 알림 권한을 끈 사람의 몫까지 국외 업체로 나가면, 본문을 비운 보람이 반쯤 사라진다.
     */
    @Test
    @DisplayName("토큰을 해제한 사용자에게는 발송 요청 자체를 하지 않는다")
    void noRequestWithoutAToken() throws Exception {
        mockMvc.perform(put("/api/users/me/push-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":null}"))
                .andExpect(status().isNoContent());
        pushClient.reset();

        supportService.notice(user, SUSPENSION_NOTICE);

        assertThat(pushClient.sent()).isEmpty();
    }
}
