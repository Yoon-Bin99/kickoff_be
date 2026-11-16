package com.kickoff.be.report.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.common.Moderation;
import com.kickoff.be.common.ModerationResponse;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.repository.MatchPostRepository;
import com.kickoff.be.report.dto.ReportDetail;
import com.kickoff.be.report.dto.ReportSummary;
import com.kickoff.be.report.dto.ResolveRequest;
import com.kickoff.be.report.entity.Report;
import com.kickoff.be.report.entity.ReportChatMessage;
import com.kickoff.be.report.entity.ReportStatus;
import com.kickoff.be.report.entity.ReportTargetType;
import com.kickoff.be.report.entity.ResolutionAction;
import com.kickoff.be.report.repository.ReportChatMessageRepository;
import com.kickoff.be.report.repository.ReportRepository;
import com.kickoff.be.review.entity.Review;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.support.service.SupportService;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고함 (계약서 §7-2 「운영자 API」, v1.35.0·v1.35.1).
 *
 * <b>조치와 통지는 같은 트랜잭션 안에서 함께 일어난다.</b> 정보통신망법 제44조의2 제2항이
 * 조치 사실을 알리라고 하는데, 통지를 나중에 돌리는 구조로 두면 "가려졌지만 아무도 모르는"
 * 상태가 생긴다 — 그 상태는 조치가 아니라 사고다.
 *
 * 운영자 인증·접속기록은 컨트롤러에서 {@code OperatorGuard} 가 본다.
 */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ReportRepository reportRepository;
    private final ReportChatMessageRepository chatMessageRepository;
    private final MatchPostRepository postRepository;
    private final ReviewRepository reviewRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final SupportService supportService;
    private final ReportNotices notices;

    /** 상태를 주지 않으면 처리 전(OPEN·TEMP_HIDDEN)만 (계약서 §7-2). */
    @Transactional(readOnly = true)
    public PageResponse<ReportSummary> list(ReportStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Report> reports = status == null
                ? reportRepository.findByStatusIn(
                        List.of(ReportStatus.OPEN, ReportStatus.TEMP_HIDDEN), pageable)
                : reportRepository.findByStatus(status, pageable);
        OffsetDateTime now = OffsetDateTime.now();
        return PageResponse.of(reports, report -> ReportSummary.of(report,
                reporterCountOf(report), hiddenUntilOf(report, now)));
    }

    @Transactional(readOnly = true)
    public ReportDetail detail(Long reportId) {
        Report report = find(reportId);
        return toDetail(report);
    }

    /**
     * 조치한다 (계약서 §7-2).
     *
     * <b>같은 대상에 걸린 다른 처리 전 신고도 함께 닫는다.</b> 하나만 닫으면 같은 글에
     * 대한 신고가 목록에 남아 운영자가 같은 판단을 여러 번 하게 된다.
     */
    @Transactional
    public ReportDetail resolve(Long reportId, ResolveRequest request) {
        Report report = find(reportId);
        validate(report, request);

        OffsetDateTime now = OffsetDateTime.now();
        applyToContent(report, request.action(), now);
        Integer suspendDays = suspend(report, request.suspendDays(), now);
        closeSameTarget(report, request, now);
        notify(report, request, suspendDays, now);
        return toDetail(report);
    }

    /** 이용 제한 해제 (계약서 §7-2). */
    @Transactional
    public void clearSuspension(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .clearSuspension();
    }

    // ── 검증

    private void validate(Report report, ResolveRequest request) {
        if (request.action() == ResolutionAction.DISMISS && request.suspendDays() != null) {
            throw invalid("suspendDays", "조치하지 않으면서 계정을 제한할 수는 없습니다.");
        }
        if (request.action() == ResolutionAction.SUSPEND_ONLY && request.suspendDays() == null) {
            throw invalid("suspendDays", "제한 기간이 필요합니다.");
        }
        // 1:1 대화는 가리거나 지우는 대상이 아니다 (계약서 §7-2) — 차단과 이용 제한으로
        // 다룬다. 가린다고 해도 그 방의 두 사람 말고는 볼 사람이 없다.
        if (report.getTargetType() == ReportTargetType.CHAT_MESSAGE
                && (request.action().hidesContent() || request.action().removesContent())) {
            throw invalid("action", "채팅 메시지는 가리거나 삭제할 수 없습니다.");
        }
    }

    // ── 콘텐츠에 조치를 건다

    private void applyToContent(Report report, ResolutionAction action, OffsetDateTime now) {
        switch (report.getTargetType()) {
            case POST -> postRepository.findById(report.getTargetId())
                    .ifPresent(post -> applyToPost(post, action, now));
            case REVIEW -> reviewRepository.findById(report.getTargetId())
                    .ifPresent(review -> applyToReview(review, action, now));
            case TEAM -> teamRepository.findById(report.getTargetId())
                    .ifPresent(team -> applyToTeam(team, action, now));
            case CHAT_MESSAGE -> {
                // 대화는 건드리지 않는다 (위 validate 가 HIDE·REMOVE 를 막는다).
            }
        }
    }

    private void applyToPost(MatchPost post, ResolutionAction action, OffsetDateTime now) {
        if (action.hidesContent()) {
            post.hide(now);
        } else if (action.removesContent()) {
            post.removeByOperator();
        } else {
            // DISMISS·SUSPEND_ONLY — 자동 가림으로 가려져 있었다면 복원한다 (계약서 §7-2).
            post.clearModeration();
        }
    }

    private void applyToReview(Review review, ResolutionAction action, OffsetDateTime now) {
        if (action.hidesContent()) {
            review.hide(now);
        } else if (action.removesContent()) {
            review.removeByOperator();
        } else {
            review.clearModeration();
        }
    }

    private void applyToTeam(Team team, ResolutionAction action, OffsetDateTime now) {
        if (action.hidesContent()) {
            team.hideIntroduction(now);
        } else if (action.removesContent()) {
            team.removeIntroductionByOperator();
        } else {
            team.clearIntroductionModeration();
        }
    }

    /** @return 실제로 제한한 날수 (통지에 쓴다). 제한하지 않았으면 null */
    private Integer suspend(Report report, Integer days, OffsetDateTime now) {
        if (days == null || report.getReportedUserId() == null) {
            return null;
        }
        User reported = userRepository.findById(report.getReportedUserId()).orElse(null);
        if (reported == null) {
            return null;
        }
        reported.suspendUntil(now.plusDays(days));
        return days;
    }

    private void closeSameTarget(Report report, ResolveRequest request, OffsetDateTime now) {
        ReportStatus status = request.action().hidesContent()
                ? ReportStatus.TEMP_HIDDEN
                : ReportStatus.CLOSED;
        for (Report pending : reportRepository.findPendingOf(report.getTargetType(),
                report.getTargetId())) {
            pending.resolve(status, request.action().name(), request.note(), now);
        }
    }

    // ── 통지 (계약서 §7-2 — 법무 초안 3-3절)

    /**
     * 신고자 전원과 게재자에게 알린다.
     *
     * <b>신고자가 누구인지는 어느 통지에도 싣지 않는다.</b> {@code DISMISS} 는 신고자에게만
     * 알리되, 자동으로 가려져 있다가 복원되는 경우에는 게재자에게도 복원을 알린다 —
     * 가려졌다는 통지를 받은 사람에게 끝을 알리지 않으면 영문을 모른 채 남는다.
     */
    private void notify(Report report, ResolveRequest request, Integer suspendDays,
                        OffsetDateTime now) {
        boolean acted = request.action() != ResolutionAction.DISMISS || suspendDays != null;
        String reporterMessage = acted
                ? notices.toReporterActed(actionName(request.action(), suspendDays))
                : notices.toReporterDismissed();
        for (Long reporterId : reporterIdsOf(report)) {
            userRepository.findById(reporterId)
                    .ifPresent(reporter -> supportService.notice(reporter, reporterMessage));
        }

        User reported = report.getReportedUserId() == null ? null
                : userRepository.findById(report.getReportedUserId()).orElse(null);
        if (reported == null) {
            return;
        }
        if (request.action().hidesContent()) {
            supportService.notice(reported, notices.toReportedHidden(report.getTargetType(),
                    now.plusDays(Moderation.HIDE_DAYS), report.getReason().displayName()));
        } else if (request.action().removesContent()) {
            supportService.notice(reported, notices.toReportedRemoved(report.getTargetType(),
                    report.getReason().displayName()));
        } else if (report.isAutoHidden()) {
            // 자동으로 가려져 있다가 풀린 경우다.
            supportService.notice(reported, notices.toReportedRestored(report.getTargetType()));
        }
        if (suspendDays != null) {
            supportService.notice(reported, notices.toReportedSuspended(suspendDays,
                    report.getReason().displayName()));
        }
    }

    private String actionName(ResolutionAction action, Integer suspendDays) {
        if (action == ResolutionAction.DISMISS || action == ResolutionAction.SUSPEND_ONLY) {
            return ResolutionAction.SUSPEND_ONLY.displayName();
        }
        return suspendDays == null
                ? action.displayName()
                : action.displayName() + " · " + ResolutionAction.SUSPEND_ONLY.displayName();
    }

    private List<Long> reporterIdsOf(Report report) {
        return reportRepository.findPendingOf(report.getTargetType(), report.getTargetId())
                .stream()
                .map(Report::getReporterUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    // ── 조회용

    private ReportDetail toDetail(Report report) {
        OffsetDateTime now = OffsetDateTime.now();
        List<ReportChatMessage> copies = report.getTargetType() == ReportTargetType.CHAT_MESSAGE
                ? chatMessageRepository.findByReportIdOrderBySentAtAsc(report.getId())
                : List.of();
        return new ReportDetail(
                ReportSummary.of(report, reporterCountOf(report), hiddenUntilOf(report, now)),
                report.getRightsHolder(),
                report.getDetail(),
                partyOf(report.getReporterUserId(), report.getReporterTeamId()),
                partyOf(report.getReportedUserId(), null),
                new ReportDetail.Target(report.getTargetTitle(), report.getTargetBody(),
                        report.getTargetRating(),
                        report.getTargetType() == ReportTargetType.CHAT_MESSAGE
                                ? report.getTargetId() : null,
                        copies.stream().map(this::toMessage).toList()),
                moderationOf(report, now),
                report.getResolvedAt() == null ? null
                        : new ReportDetail.Resolution(report.getResolutionAction(),
                                report.getResolutionNote(), report.getResolvedAt()));
    }

    private ReportDetail.Message toMessage(ReportChatMessage copy) {
        return new ReportDetail.Message(copy.getMessageId(), copy.isReported(),
                copy.getSenderUserId(), copy.getSenderTeamId(), copy.getMessageType(),
                copy.getContent(), copy.getSentAt());
    }

    /** 탈퇴했으면 null 이다 (계약서 §7-2 — 식별값을 비운다). */
    private ReportDetail.Party partyOf(Long userId, Long teamId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        Team team = teamId == null
                ? teamRepository.findByOwnerId(userId).orElse(null)
                : teamRepository.findById(teamId).orElse(null);
        return new ReportDetail.Party(user.getId(), user.getNickname(),
                team == null ? null : team.getId(), team == null ? null : team.getName());
    }

    /** 같은 팀의 신고는 1명으로 센다 (신고 접수 때와 같은 기준). */
    private long reporterCountOf(Report report) {
        Set<String> keys = new HashSet<>();
        for (Report pending : reportRepository.findPendingOf(report.getTargetType(),
                report.getTargetId())) {
            keys.add(pending.getReporterTeamId() != null
                    ? "t" + pending.getReporterTeamId()
                    : "u" + pending.getReporterUserId());
        }
        return keys.size();
    }

    private OffsetDateTime hiddenUntilOf(Report report, OffsetDateTime now) {
        ModerationResponse moderation = moderationOf(report, now);
        return moderation == null ? null : moderation.until();
    }

    /** 대상에 지금 걸려 있는 조치. 대상이 사라졌으면 null. */
    private ModerationResponse moderationOf(Report report, OffsetDateTime now) {
        Moderation moderation = switch (report.getTargetType()) {
            case POST -> postRepository.findById(report.getTargetId())
                    .map(MatchPost::getModeration).orElse(null);
            case REVIEW -> reviewRepository.findById(report.getTargetId())
                    .map(Review::getModeration).orElse(null);
            case TEAM -> teamRepository.findById(report.getTargetId())
                    .map(Team::getIntroductionModeration).orElse(null);
            case CHAT_MESSAGE -> null;
        };
        return ModerationResponse.of(moderation, now);
    }

    private Report find(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }

    private BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED,
                List.of(new ErrorResponse.FieldError(field, message)));
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
