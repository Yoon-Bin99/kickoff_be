package com.kickoff.be.common;

/**
 * 이용자에게 그대로 보이는 문장의 조사를 고른다.
 *
 * <b>"이(가)"로 두지 않는다.</b> 개발자에게 보이는 로그가 아니라 사람이 읽는 알림·통지다.
 * 받침이 있으면 "이", 없으면 "가"라는 규칙 하나면 되고, 들어오는 이름이 늘어도 따로
 * 손볼 것이 없다.
 *
 * 한글이 아닌 글자로 끝나면("FC Seoul", "SC 90") 받침이 없는 것으로 본다. 영문·숫자의
 * 읽는 소리까지 맞추려면 예외가 줄줄이 붙는데, 그만한 값이 아니다.
 */
public final class Josa {

    private static final char HANGUL_FIRST = '가';
    private static final char HANGUL_LAST = '힣';

    /** 한글 음절 한 글자에 들어 있는 종성의 가짓수 — 나머지가 0이면 받침이 없다. */
    private static final int HANGUL_FINALS = 28;

    private Josa() {
    }

    /** 주격 조사를 붙인다 — "모집글이", "리뷰가", "FC 새벽이", "고양 킥커스가". */
    public static String subject(String noun) {
        if (noun == null || noun.isEmpty()) {
            return noun;
        }
        return noun + (hasFinalConsonant(noun) ? "이" : "가");
    }

    private static boolean hasFinalConsonant(String noun) {
        char last = noun.charAt(noun.length() - 1);
        return last >= HANGUL_FIRST && last <= HANGUL_LAST
                && (last - HANGUL_FIRST) % HANGUL_FINALS != 0;
    }
}
