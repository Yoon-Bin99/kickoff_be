package com.kickoff.be.support;

import com.kickoff.be.sms.client.SmsClient;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 실제 제공자를 때리지 않고 무엇이 어디로 나갔는지 기록한다.
 *
 * <b>인증번호를 기록해 두지 않는다.</b> 본문에서 뽑아 쓴다 — 그래야 테스트가 발송 문구까지
 * 함께 검증하게 되고, 서비스가 만든 코드와 문자로 나간 코드가 다른 상황을 잡을 수 있다.
 *
 * {@link #willFail()} 로 발송 실패를 흉내 낼 수 있다. 푸시 스텁과 달리 실제 구현도
 * 예외를 던지므로(계약서가 502 SMS_SEND_FAILED 를 정했다) 이건 예외 경로의 재현이다.
 */
public class StubSmsClient implements SmsClient {

    /** 발송 문구에서 6자리를 뽑는다 (계약서 §3-2 문구 기준). */
    private static final Pattern CODE = Pattern.compile("(\\d{6})");

    public record Sms(String phone, String text) {
    }

    private final List<Sms> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failNext;

    @Override
    public void send(String phone, String text) {
        if (failNext) {
            throw new IllegalStateException("스텁이 일부러 낸 발송 실패");
        }
        sent.add(new Sms(phone, text));
    }

    public List<Sms> sent() {
        return List.copyOf(sent);
    }

    public Sms last() {
        return sent.isEmpty() ? null : sent.get(sent.size() - 1);
    }

    /** 마지막으로 나간 문자의 인증번호. 없으면 null. */
    public String lastCode() {
        Sms last = last();
        if (last == null) {
            return null;
        }
        Matcher matcher = CODE.matcher(last.text());
        return matcher.find() ? matcher.group(1) : null;
    }

    public void willFail() {
        this.failNext = true;
    }

    public void reset() {
        sent.clear();
        failNext = false;
    }
}
