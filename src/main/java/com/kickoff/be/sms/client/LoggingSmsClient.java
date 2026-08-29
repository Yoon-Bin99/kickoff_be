package com.kickoff.be.sms.client;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 제공자가 없을 때의 기본 어댑터 (계약서 §3-2, v1.15.0).
 *
 * <b>dev (SMS_ENABLED=false)</b> — 실제로 보내지 않고 본문을 로그로 남긴다. 계약이 정한
 * dev 동작이고, FE 가 로그에서 6자리를 읽어 화면을 붙일 수 있다.
 *
 * <b>운영 (SMS_ENABLED=true)</b> — 여기까지 왔다는 건 제공자 어댑터가 빠졌다는 뜻이라
 * 502 로 실패시킨다. 조용히 성공시키는 쪽이 훨씬 위험하다: 아무도 인증번호를 못 받는데
 * API 는 204 를 주고, 사용자에게는 "문자가 안 온다"로만 보이며 서버 로그에는 아무 흔적도
 * 남지 않는다. 가입 자체가 막히는 건 눈에 띄지만 잘못 보낸 성공은 눈에 안 띈다.
 *
 * 실제 제공자(쿨SMS·솔라피 등)를 붙일 때는 {@link SmsClient} 구현을 하나 더 만들고
 * {@code @Primary} 를 붙이면 된다 — 이 클래스는 손대지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingSmsClient implements SmsClient {

    private final SmsProperties properties;

    @Override
    public void send(String phone, String text) {
        if (properties.enabled()) {
            log.error("SMS 제공자 어댑터가 없는데 SMS_ENABLED=true 입니다 — 발송 불가 (phone={})",
                    masked(phone));
            throw new BusinessException(ErrorCode.SMS_SEND_FAILED);
        }
        // dev 전용 경로다. 인증번호가 그대로 찍히므로 운영에서는 절대 이 가지로 오면 안 된다.
        log.info("[SMS 생략 — SMS_ENABLED=false] to={} / {}", phone, text);
    }

    /** 실패 로그에는 번호를 통째로 남기지 않는다. 어느 번호였는지 알 정도만 남긴다. */
    private String masked(String phone) {
        return phone == null || phone.length() < 4
                ? "?"
                : "***-****-" + phone.substring(phone.length() - 4);
    }
}
