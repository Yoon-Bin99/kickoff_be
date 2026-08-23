package com.kickoff.be.common;

import com.kickoff.be.user.User;

/**
 * 매칭이 수락된 두 팀에게만 공개되는 연락처. 그 외에는 항상 null 로 내려간다.
 */
public record ContactInfo(String nickname, String phone) {

    public static ContactInfo from(User user) {
        return new ContactInfo(user.getNickname(), user.getPhone());
    }
}
