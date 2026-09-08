package com.kickoff.be.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청을 보낸 클라이언트의 IP.
 *
 * <b>{@code remoteAddr} 만 보면 안 된다.</b> 운영은 Railway 프록시 뒤라, 거기서는 모든
 * 요청이 프록시의 주소 하나로 보인다 — IP 기준 한도가 전부 같은 키로 묶여 첫 사용자가
 * 한도를 다 쓰면 나머지가 막힌다.
 *
 * <b>그렇다고 {@code X-Forwarded-For} 를 믿을 수도 없다.</b> 클라이언트가 마음대로 넣어
 * 보낼 수 있는 헤더라, 값을 바꿔 가며 부르면 IP 한도를 그냥 우회한다. 그래서 이 값은
 * <b>한도의 유일한 방어선이 되어서는 안 된다</b> — 전체 한도를 따로 두는 이유가 그것이다.
 * 여기서 얻는 것은 "정상 사용자를 서로 구분하는 힘"이지 "공격자를 특정하는 힘"이 아니다.
 *
 * 첫 값을 쓴다. 프록시를 지날 때마다 오른쪽에 덧붙는 형식이라 맨 앞이 원래 클라이언트다.
 */
public final class ClientIp {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String first = forwarded.split(",", 2)[0].trim();
        return first.isEmpty() ? request.getRemoteAddr() : first;
    }
}
