package com.kickoff.be.sms.client;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 제공자 어댑터가 없을 때 남는 자리 (계약서 §3-2).
 *
 * <b>여기까지 왔다는 건 보낼 수단이 없는데 발송이 요청됐다는 뜻이다.</b> 발송 여부는
 * 서비스가 SMS_ENABLED 로 이미 걸렀으므로, 이 메서드가 불렸다면 스위치는 켜져 있고
 * 제공자만 빠진 상태다. 그래서 502 로 실패시킨다.
 *
 * 조용히 성공시키는 쪽이 훨씬 위험하다: 아무도 인증번호를 못 받는데 API 는 204 를 주고,
 * 사용자에게는 "문자가 안 온다"로만 보이며 서버 로그에도 아무 흔적이 없다. 가입이 막히는
 * 건 눈에 띄지만 잘못 보낸 성공은 눈에 안 띈다.
 *
 * 실제 제공자를 붙이면 그쪽이 {@code @Primary} 로 등록되어 이 클래스는 쓰이지 않는다
 * ({@link SmsClientConfig}).
 */
@Slf4j
@Component
public class LoggingSmsClient implements SmsClient {

    @Override
    public void send(String phone, String text) {
        log.error("문자 제공자 어댑터가 없는데 발송이 요청됐습니다 — SMS_ENABLED 는 켜져"
                + " 있고 솔라피 키·발신번호가 비어 있습니다 (phone={})", masked(phone));
        throw new BusinessException(ErrorCode.SMS_SEND_FAILED);
    }

    /** 실패 로그에는 번호를 통째로 남기지 않는다. 어느 번호였는지 알 정도만 남긴다. */
    private String masked(String phone) {
        return phone == null || phone.length() < 4
                ? "?"
                : "***-****-" + phone.substring(phone.length() - 4);
    }
}
