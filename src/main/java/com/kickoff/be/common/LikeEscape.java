package com.kickoff.be.common;

/**
 * 검색어의 LIKE 와일드카드를 리터럴로 만든다 (계약서 §4·§5).
 *
 * 검색창에 친 {@code %} 는 "이름에 % 가 든 것"을 찾겠다는 뜻이지 "전부 보여 달라"가
 * 아니다. 그대로 넘기면 {@code %} 하나로 모든 행이, {@code _} 하나로 한 글자 이상인 행이
 * 전부 나온다 — <b>에러가 아니라 결과가 너무 많이 나올 뿐이라 눈에 잘 안 띈다.</b>
 * {@code %%%%%} 같은 입력으로 전체 스캔을 유도할 수도 있다.
 *
 * <b>이스케이프 문자로 역슬래시가 아니라 {@code !} 를 쓴다.</b> 역슬래시는 자바 문자열,
 * JPQL 문자열, DB 의 문자열 리터럴 규칙(PostgreSQL 의 standard_conforming_strings)을
 * 차례로 지나며 몇 겹으로 해석되는데, 그 층이 어긋나면 조용히 다른 패턴이 된다.
 * {@code !} 는 어느 층에서도 특별하지 않아 셀 것이 없다.
 *
 * <b>쿼리 쪽에 {@code escape '!'} 절이 함께 있어야 한다.</b> 한쪽만 하면 더 나빠진다 —
 * 절 없이 값만 이스케이프하면 {@code !} 가 문자 그대로 매칭되어, {@code 50%} 를 찾는
 * 사람이 {@code 50!%} 를 찾게 된다.
 *
 * 팀 검색이 v1.14.0 에서 먼저 이걸 갖췄고 모집글 검색에는 빠져 있었다. 같은 규칙을 두 번
 * 적지 않으려고 여기로 옮겼다 — 한쪽만 고치는 일이 실제로 벌어졌던 자리다.
 */
public final class LikeEscape {

    /** 쿼리의 {@code escape} 절에 그대로 적는 문자. 여기와 쿼리가 같아야 한다. */
    public static final String ESCAPE_CHAR = "!";

    private LikeEscape() {
    }

    /**
     * <b>순서가 중요하다.</b> 이스케이프 문자 자신을 먼저 처리해야 한다. 뒤로 미루면
     * {@code %} 를 감싸며 넣은 {@code !} 까지 다시 이스케이프돼 패턴이 망가진다.
     */
    public static String escape(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
