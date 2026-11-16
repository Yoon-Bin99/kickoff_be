package com.kickoff.be.post.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 경기 뒤 연락처·계좌가 상대 팀에게 보이는 기간 (계약서 §5 「표시 기간」, v1.34.1).
 *
 * <b>왜 기간이 있는가.</b> 연락처는 "매칭 성사 시 상대 팀 감독에게 제공"에 동의해서 넘어간
 * 값이고, 그 동의 문구의 <b>보유·이용 기간</b>이 이 값이다(제17조 제2항). 경기가 끝나고도
 * 무기한 보이면 동의한 범위를 넘는다.
 *
 * 14일인 것은 경기 뒤의 정산·분쟁(입금 환불 등)에 연락이 필요한 기간을 본 값이다.
 *
 * @param visibleDays 경기일({@code matchAt})로부터 며칠까지 보일지. 기본 14
 */
@ConfigurationProperties(prefix = "kickoff.match")
public record MatchContactProperties(Integer contactVisibleDays) {

    private static final int DEFAULT_VISIBLE_DAYS = 14;

    public MatchContactProperties {
        // 설정을 빠뜨렸을 때 0 으로 떨어지면 수락 직후부터 연락처가 안 보인다 —
        // 기능이 통째로 죽는 쪽이라 기본값을 둔다.
        contactVisibleDays = contactVisibleDays == null ? DEFAULT_VISIBLE_DAYS
                : contactVisibleDays;
    }

    /**
     * 지금 이 경기의 연락처를 상대에게 보여도 되는지.
     *
     * 경계는 <b>"같은 순간까지 보인다"</b>다 — 채팅 보관 기간(§6-1)과 같은 방식이고,
     * {@code matchAt} 의 전송 가능 판정과도 같다. 한 서비스 안에서 경계 규칙이 갈리면
     * 어느 쪽이 맞는지 매번 확인해야 한다.
     */
    public boolean isVisibleAt(OffsetDateTime matchAt, OffsetDateTime now) {
        if (matchAt == null) {
            return false;
        }
        return !now.isAfter(matchAt.plus(Duration.ofDays(contactVisibleDays)));
    }
}
