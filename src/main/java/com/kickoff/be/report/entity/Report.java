package com.kickoff.be.report.entity;

import com.kickoff.be.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 신고 한 건 (계약서 §7-2, v1.35.0).
 *
 * <b>사용자를 연관이 아니라 id 로 들고 있다.</b> 신고자나 신고된 쪽이 탈퇴하면 그 칸을
 * 비워야 하는데(§7-2 「기록의 보관」 — 식별값을 비우고 건 자체는 기간까지 남긴다),
 * 외래키로 묶어 두면 "사용자는 지웠는데 행은 남는" 상태를 만들 수 없다. 팀 id 도 같다.
 *
 * <b>대상의 사본을 함께 남긴다.</b> 운영자가 보는 것은 "신고 시점의 그것"이어야 한다 —
 * 신고 뒤에 글이 고쳐지면 무엇을 두고 신고했는지 알 수 없고, 삭제되면 볼 것이 없다.
 */
@Entity
@Getter
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_target", columnList = "target_type, target_id"),
        @Index(name = "idx_reports_reporter", columnList = "reporter_user_id"),
        @Index(name = "idx_reports_status", columnList = "status")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

    /** 한 계정이 하루에 넣을 수 있는 신고 수 (계약서 §7-2 — 넘으면 429). */
    public static final int DAILY_LIMIT = 10;

    /** 자동 임시 가림이 걸리는 서로 다른 신고자 수 (계약서 §7-2). */
    public static final int AUTO_HIDE_REPORTERS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 탈퇴하면 null 이 된다 (계약서 §7-2 「기록의 보관」). */
    @Column(name = "reporter_user_id")
    private Long reporterUserId;

    /**
     * 신고자가 감독인 팀. 같은 팀의 계정들이 넣은 신고를 <b>1명으로 세기</b> 위해 남긴다
     * (계약서 §7-2 — 한 팀이 셋이 달려들어 상대 글을 가리는 일을 막는다).
     */
    @Column(name = "reporter_team_id")
    private Long reporterTeamId;

    /** 신고된 쪽 계정(게재자). 통지와 이용 제한의 대상이다. 탈퇴하면 null. */
    @Column(name = "reported_user_id")
    private Long reportedUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** {@code CHAT_MESSAGE} 일 때의 방(신청) id. 그 밖에는 null. */
    @Column(name = "request_id")
    private Long requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "rights_holder", length = 10)
    private RightsHolder rightsHolder;

    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    /** 운영자 조치가 아니라 신고 3건으로 가려졌는지 (계약서 §7-2 — 운영자 목록에 표시). */
    @Column(name = "auto_hidden", nullable = false)
    private boolean autoHidden;

    // ── 신고 시점의 대상 사본 (계약서 §7-2 — 운영자 상세)

    /** 글 제목 / 팀 이름. 리뷰·채팅은 null. */
    @Column(name = "target_title", length = 200)
    private String targetTitle;

    /** 글 내용 / 리뷰 내용 / 팀 소개. 채팅은 아래 사본 표에 담는다. */
    @Column(name = "target_body", length = 2000)
    private String targetBody;

    /** 리뷰 별점. 그 밖에는 null. */
    @Column(name = "target_rating")
    private Integer targetRating;

    // ── 처리 결과 (운영자 조치 — 다음 단계에서 채운다)

    @Column(name = "resolution_action", length = 20)
    private String resolutionAction;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    /** 그 조치에 붙은 이용 제한 날수 (계약서 §7-2, v1.37.2). 없으면 null. */
    @Column(name = "resolution_suspend_days")
    private Integer resolutionSuspendDays;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Builder
    private Report(Long reporterUserId, Long reporterTeamId, Long reportedUserId,
                   ReportTargetType targetType, Long targetId, Long requestId,
                   ReportReason reason, RightsHolder rightsHolder, String detail,
                   String targetTitle, String targetBody, Integer targetRating) {
        this.reporterUserId = reporterUserId;
        this.reporterTeamId = reporterTeamId;
        this.reportedUserId = reportedUserId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestId = requestId;
        this.reason = reason;
        this.rightsHolder = rightsHolder;
        this.detail = detail;
        this.targetTitle = targetTitle;
        this.targetBody = targetBody;
        this.targetRating = targetRating;
        this.status = ReportStatus.OPEN;
    }

    /** 신고 3건으로 자동 가림이 걸렸다 (계약서 §7-2). */
    public void markAutoHidden() {
        this.status = ReportStatus.TEMP_HIDDEN;
        this.autoHidden = true;
    }

    /**
     * 운영자가 조치했다 (계약서 §7-2).
     *
     * 임시 가림이면 {@code TEMP_HIDDEN} 으로 남는다 — 30일 안에 다시 정해야 하는
     * 미완의 건이라, CLOSED 로 묶으면 기한이 다가오는 것을 아무도 보지 않는다.
     */
    public void resolve(ReportStatus status, String action, Integer suspendDays, String note,
                        OffsetDateTime resolvedAt) {
        this.status = status;
        this.resolutionAction = action;
        this.resolutionSuspendDays = suspendDays;
        this.resolutionNote = note;
        // 밀리초까지만 남긴다. 윈도우는 100ns 정밀도라 그대로 내보내면 소수점이 7자리가
        // 되고, 다른 응답과 모양이 달라진다 (ErrorResponse 와 같은 이유).
        this.resolvedAt = resolvedAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

    /** 신고자가 탈퇴했다 — 건 자체는 보관 기간까지 남는다 (계약서 §7-2). */
    public void forgetReporter() {
        this.reporterUserId = null;
        this.reporterTeamId = null;
    }

    /** 신고된 쪽이 탈퇴했다. */
    public void forgetReported() {
        this.reportedUserId = null;
    }

    public boolean isPending() {
        return status.isPending();
    }
}
