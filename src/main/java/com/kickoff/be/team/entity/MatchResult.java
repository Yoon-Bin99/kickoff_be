package com.kickoff.be.team.entity;

/**
 * 경기 결과 (계약서 §4-1, v1.8.0).
 *
 * 컬럼으로 저장하지 않는다. 스코어에서 계산되는 값이라 따로 들고 있으면 둘이 어긋날 수
 * 있고, 어긋나면 어느 쪽이 맞는지 알 방법이 없다. 기록은 수정이 없어 스코어가 바뀔 일도
 * 없지만, 저장하지 않으면 애초에 어긋날 수가 없다.
 */
public enum MatchResult {
    WIN, DRAW, LOSS;

    public static MatchResult of(int ourScore, int opponentScore) {
        if (ourScore > opponentScore) {
            return WIN;
        }
        return ourScore == opponentScore ? DRAW : LOSS;
    }
}
