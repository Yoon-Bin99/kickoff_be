package com.kickoff.be.support;

import com.kickoff.be.email.client.EmailClient;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 메일 스텁 (계약서 §3-3). 실제로 보내지 않는다.
 *
 * <b>코드를 따로 기록해 두지 않고 본문에서 뽑는다.</b> 문자 스텁과 같은 이유다 — 그래야
 * 테스트가 발송 문구까지 함께 검증하게 되고, 서비스가 만든 코드와 메일로 나간 코드가
 * 다른 상황을 잡을 수 있다.
 */
public class StubEmailClient implements EmailClient {

    private static final Pattern CODE = Pattern.compile("인증번호:\\s*(\\d{6})");

    private final List<Mail> sent = new ArrayList<>();

    public record Mail(String to, String subject, String body) {
    }

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new Mail(to, subject, body));
    }

    public List<Mail> sent() {
        return sent;
    }

    public Mail last() {
        return sent.get(sent.size() - 1);
    }

    /** 마지막 메일 본문에 실린 6자리 코드. 없으면 테스트가 그 자리에서 깨진다. */
    public String lastCode() {
        Matcher m = CODE.matcher(last().body());
        if (!m.find()) {
            throw new IllegalStateException("메일 본문에서 인증번호를 찾지 못했다:\n" + last().body());
        }
        return m.group(1);
    }

    public void reset() {
        sent.clear();
    }
}
