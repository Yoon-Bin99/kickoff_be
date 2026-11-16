package com.kickoff.be.support;

import com.kickoff.be.chatlink.client.ChatLinkProperties;
import com.kickoff.be.chatlink.client.ReportContextClient;
import com.kickoff.be.chatlink.dto.ReportContext;
import com.kickoff.be.common.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 채팅 메시지 신고가 채팅 서버에 묻는 자리를 대신한다 (계약서 §10 report-context).
 *
 * 테스트에서 진짜 채팅 서버를 띄울 수 없고, <b>판정은 그쪽 서버의 몫</b>이라(그 방의
 * 당사자인지, 나가기 전의 메시지인지) 여기서는 "무엇을 돌려주는가"와 "실패하면 어떻게
 * 되는가"만 흉내 낸다.
 */
public class StubReportContextClient extends ReportContextClient {

    private ReportContext next;
    private BusinessException failure;
    private Long lastRequestId;
    private Long lastMessageId;
    private Long lastReporterUserId;

    public StubReportContextClient(ChatLinkProperties properties) {
        super(properties);
    }

    @Override
    public ReportContext fetch(Long requestId, Long messageId, Long reporterUserId) {
        lastRequestId = requestId;
        lastMessageId = messageId;
        lastReporterUserId = reporterUserId;
        if (failure != null) {
            throw failure;
        }
        return next != null ? next : defaultContext(requestId, messageId);
    }

    /** 채팅 서버가 거절하거나 답하지 않는 경우. */
    public void failWith(BusinessException exception) {
        this.failure = exception;
    }

    public void willReturn(ReportContext context) {
        this.next = context;
    }

    public void reset() {
        this.next = null;
        this.failure = null;
        this.lastRequestId = null;
        this.lastMessageId = null;
        this.lastReporterUserId = null;
    }

    public Long lastRequestId() {
        return lastRequestId;
    }

    public Long lastMessageId() {
        return lastMessageId;
    }

    public Long lastReporterUserId() {
        return lastReporterUserId;
    }

    /** 신고된 메시지 + 앞 하나 + 뒤 하나. 보낸 사람은 상대 팀 감독(= id 를 주는 쪽)이다. */
    private ReportContext defaultContext(Long requestId, Long messageId) {
        OffsetDateTime base = OffsetDateTime.now().minusHours(1);
        ReportContext.InternalChatMessage before = message(requestId, messageId - 1,
                "안녕하세요", base, 9999L);
        ReportContext.InternalChatMessage reported = message(requestId, messageId,
                "신고된 메시지입니다", base.plusMinutes(1), 9999L);
        ReportContext.InternalChatMessage after = message(requestId, messageId + 1,
                "그 뒤의 말", base.plusMinutes(2), 8888L);
        return new ReportContext(reported, List.of(before, reported, after));
    }

    private ReportContext.InternalChatMessage message(Long requestId, Long id, String content,
                                                      OffsetDateTime at, Long senderUserId) {
        return new ReportContext.InternalChatMessage(id, requestId, "TEXT", 1L, senderUserId,
                content, at);
    }
}
