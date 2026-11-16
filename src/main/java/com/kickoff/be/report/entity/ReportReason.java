package com.kickoff.be.report.entity;

import java.util.Set;

/**
 * 신고 사유 (계약서 §1·§7-2, v1.35.0).
 *
 * <b>대상마다 고를 수 있는 사유가 다르다.</b> 리뷰에 "경기 불참"을 신고할 수는 없다 —
 * 리뷰는 경기가 끝난 뒤의 글이고, 불참은 팀의 행동이다. 사유와 대상이 어긋난 신고가
 * 쌓이면 운영자가 무엇을 봐야 할지 알 수 없게 되므로 서버가 막는다.
 *
 * 표시명은 운영자에게 가는 푸시에 쓴다 — 사용자 화면의 문구는 FE 가 가진다.
 */
public enum ReportReason {

    ABUSE("욕설·비방·괴롭힘", all()),
    DEFAMATION("명예훼손·사생활 침해", all()),
    PERSONAL_INFO("개인정보 노출", all()),
    FALSE_INFO("허위 정보·사칭", all()),
    SPAM("스팸·광고·부적절한 내용", all()),
    /** 리뷰에는 쓸 수 없다 — 금전 피해는 글·팀·대화에서 생긴다. */
    FRAUD("사기·금전 피해", Set.of(ReportTargetType.POST, ReportTargetType.TEAM,
            ReportTargetType.CHAT_MESSAGE)),
    /** 팀의 행동이라 글·리뷰에는 쓸 수 없다. */
    NO_SHOW("경기 불참·약속 불이행", Set.of(ReportTargetType.TEAM,
            ReportTargetType.CHAT_MESSAGE));

    private final String displayName;
    private final Set<ReportTargetType> targets;

    ReportReason(String displayName, Set<ReportTargetType> targets) {
        this.displayName = displayName;
        this.targets = targets;
    }

    private static Set<ReportTargetType> all() {
        return Set.of(ReportTargetType.values());
    }

    public boolean appliesTo(ReportTargetType targetType) {
        return targets.contains(targetType);
    }

    public String displayName() {
        return displayName;
    }
}
