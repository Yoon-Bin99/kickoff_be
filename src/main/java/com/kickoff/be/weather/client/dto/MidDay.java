package com.kickoff.be.weather.client.dto;

/**
 * 중기육상예보의 하루 (계약서 §8-3).
 *
 * 3~7일은 오전·오후로 나뉘고 8~10일은 하루 하나다. 하루짜리는 오전·오후에 같은 값을
 * 넣어 둔다 — 읽는 쪽이 "나뉜 날인가"를 매번 묻지 않아도 되게 한다.
 */
public record MidDay(String amText, String pmText, Integer amPop, Integer pmPop) {

    public static MidDay wholeDay(String text, Integer pop) {
        return new MidDay(text, text, pop, pop);
    }
}
