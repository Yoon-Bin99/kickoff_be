package com.kickoff.be.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.consent.entity.ConsentType;
import com.kickoff.be.oauth.entity.AuthProvider;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import java.util.List;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        String phone,
        /** 주요 활동 지역 (v1.6.0). null 이면 전국 — FE 홈 목록이 지역 필터 없이 뜬다. */
        String activityRegion,
        @JsonProperty("hasTeam") boolean hasTeam,
        Long teamId,
        List<AuthProvider> authProviders,
        /**
         * 가입 화면 세 항목을 모두 마친 가장 최근 시각 (계약서 §2, v1.25.0 / 출처 v1.34.0).
         *
         * <b>표시용이다.</b> 동의 화면을 띄울지는 {@code consentRequired} 로만 정한다 —
         * 전에는 FE 가 이 값을 현행 시행일과 비교했는데, 시행일을 FE 가 들고 있어야 해서
         * 문서를 개정하는 날 앱이 업데이트되지 않으면 아무도 다시 동의하지 않았다.
         *
         * 셋 중 하나라도 기록이 없으면 null 이다(소셜 가입 직후 등).
         */
        OffsetDateTime termsAgreedAt,

        /**
         * 지금 동의 화면을 거쳐야 하는지 (계약서 §2, v1.34.0). <b>서버가 판정한다.</b>
         *
         * FE 는 이 값이 false 가 <b>아니면</b>(true·없음·해석 실패) 동의 화면을 띄운다 —
         * 그래서 서버는 언제나 명시적으로 채워 보낸다.
         */
        @JsonProperty("consentRequired") boolean consentRequired,

        /**
         * 지금 받아야 하는 항목의 키 목록 (계약서 §2, v1.34.1). 비어 있으면
         * {@code consentRequired} 는 false 다.
         *
         * 어느 항목을 보일지 FE 가 추측하지 않게 하는 값이다 — 팀이 없는 계정에는
         * {@code contactSharing} 이 들어오지 않는다.
         */
        List<String> pendingConsents
) {

    /**
     * phone 은 소셜 가입 직후 null 일 수 있고, authProviders 는 이메일 가입만 했으면 빈 배열이다
     * (계약서 §2). 연동 목록과 동의 판정은 별도 조회라 호출자가 넘긴다 — DTO 가 리포지터리를
     * 잡지 않는다.
     *
     * @param pendingConsents 지금 받아야 하는 항목 (계약서 §3). 비어 있으면
     *                        {@code consentRequired} 는 false 가 된다 — 두 값을 따로
     *                        받지 않는 이유는 어긋날 수 있는 자리를 만들지 않으려는 것이다
     */
    public static UserResponse of(User user, Team team, List<AuthProvider> authProviders,
                                  OffsetDateTime termsAgreedAt,
                                  List<ConsentType> pendingConsents) {
        List<ConsentType> pending = pendingConsents == null ? List.of() : pendingConsents;
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPhone(),
                user.getActivityRegion(),
                team != null,
                team == null ? null : team.getId(),
                authProviders == null ? List.of() : authProviders,
                termsAgreedAt,
                !pending.isEmpty(),
                pending.stream().map(ConsentType::key).toList()
        );
    }
}
