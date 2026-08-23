package com.kickoff.be.common;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 입금 안내. 글 작성 팀이 구장 대여료를 이미 냈고, 매칭이 확정되면 상대 팀이 자기 몫을 보낸다.
 *
 * 계좌를 볼 수 있는 사람은 <b>수락된 신청 팀</b>(입금할 곳)과 <b>글 작성자 본인</b>
 * (자기 계좌 확인·수정)뿐이다. 목록에는 어떤 경우에도 실리지 않으므로,
 * 이 오브젝트를 만드는 경로는 반드시 수신자가 누구인지 확인한 뒤여야 한다.
 */
public record PaymentInfo(
        Integer depositAmount,
        String bankName,
        String accountNumber,
        String accountHolder,
        @JsonProperty("depositPaid") boolean depositPaid
) {
}
