package com.kickoff.be.support;

import com.kickoff.be.support.client.AiSupportClient;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 상담 스텁 (계약서 §7-1). 실제 제공자를 부르지 않는다 — 테스트가 크레딧을 쓰면 안 되고,
 * 응답이 매번 달라지면 검증할 수가 없다.
 *
 * 무엇이 넘어갔는지 기록해 둔다. <b>시스템 프롬프트에 지식이 실렸는지</b>가 이 기능에서
 * 가장 조용히 깨지는 자리라 그것까지 본다 — 지식이 비어도 AI 는 그럴듯하게 답한다.
 */
public class StubAiSupportClient implements AiSupportClient {

    private final List<Call> calls = new ArrayList<>();
    private String answer = "안녕하세요, 무엇을 도와드릴까요?";
    private boolean fail;

    public record Call(String systemPrompt, List<AiTurn> history) {
    }

    @Override
    public String reply(String systemPrompt, List<AiTurn> history) {
        calls.add(new Call(systemPrompt, List.copyOf(history)));
        if (fail) {
            throw new AiUnavailableException("스텁 실패", null);
        }
        return answer;
    }

    public void willAnswer(String answer) {
        this.answer = answer;
    }

    /** 타임아웃·크레딧 부족 등을 흉내 낸다. 계약서가 강등 안내를 정해 뒀다. */
    public void willFail() {
        this.fail = true;
    }

    public List<Call> calls() {
        return calls;
    }

    public Call last() {
        return calls.get(calls.size() - 1);
    }

    public void reset() {
        calls.clear();
        answer = "안녕하세요, 무엇을 도와드릴까요?";
        fail = false;
    }
}
