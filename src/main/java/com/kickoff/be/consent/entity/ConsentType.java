package com.kickoff.be.consent.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 받아야 하는 동의 항목 (계약서 §3 「동의」, v1.34.0·v1.34.1).
 *
 * <b>항목마다 따로 받는 것이 이 구조의 전부다</b>(개인정보 보호법 제22조 제1항). 한 번의
 * 체크로 묶으면 무엇에 동의했는지가 기록으로 남지 않는다.
 *
 * {@code key} 는 계약서가 정한 이름이고 요청·응답에 그대로 나간다 — 열거형 이름을 쓰지 않는
 * 이유는 그쪽을 바꾸는 순간 FE 가 조용히 깨지기 때문이다.
 */
public enum ConsentType {

    /** 만 14세 이상입니다 — 필수 확인. */
    AGE14("age14", Scope.EVERYONE),

    /** 이용약관에 동의합니다 — 필수 동의. */
    TERMS("terms", Scope.EVERYONE),

    /**
     * 개인정보 수집·이용 안내를 확인했습니다.
     *
     * <b>"동의"가 아니라 "확인"이다</b>(계약서 v1.34.1) — 가입에 꼭 필요한 정보는 동의 없이
     * 수집하고 알린다(제15조 제1항 제4호, 제22조 제3항). 받는 방식은 같지만 문구가 다르다.
     */
    PRIVACY("privacy", Scope.EVERYONE),

    /**
     * 매칭이 성사되면 상대 팀 감독에게 내 닉네임·휴대전화 번호가 제공되는 것에 동의합니다
     * (제3자 제공, 제17조).
     *
     * <b>감독에게만 받는다.</b> 전화번호가 상대에게 넘어가는 사람은 감독뿐이라, 팀원으로만
     * 활동할 가입자에게 필수로 요구하지 않는다 — 그래서 가입이 아니라 <b>팀을 만들 때</b>
     * 받는다(계약서 v1.34.1).
     */
    CONTACT_SHARING("contactSharing", Scope.TEAM_OWNER);

    /** 누구에게 받는 항목인지. {@code pendingConsents} 판정이 이것으로 갈린다. */
    public enum Scope {
        EVERYONE,
        TEAM_OWNER
    }

    /** 가입 화면과 동의 화면이 함께 받는 세 항목 (계약서 §3). */
    public static final List<ConsentType> SIGNUP_ITEMS = List.of(AGE14, TERMS, PRIVACY);

    private final String key;
    private final Scope scope;

    ConsentType(String key, Scope scope) {
        this.key = key;
        this.scope = scope;
    }

    public String key() {
        return key;
    }

    public boolean isForTeamOwnerOnly() {
        return scope == Scope.TEAM_OWNER;
    }

    public static Optional<ConsentType> ofKey(String key) {
        return Arrays.stream(values()).filter(type -> type.key.equals(key)).findFirst();
    }
}
