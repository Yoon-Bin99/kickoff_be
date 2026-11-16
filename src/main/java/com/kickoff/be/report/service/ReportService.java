package com.kickoff.be.report.service;

import com.kickoff.be.chatlink.client.ReportContextClient;
import com.kickoff.be.chatlink.dto.ReportContext;
import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.repository.MatchPostRepository;
import com.kickoff.be.report.dto.ReportCreateRequest;
import com.kickoff.be.report.dto.ReportCreatedResponse;
import com.kickoff.be.report.entity.Report;
import com.kickoff.be.report.entity.ReportChatMessage;
import com.kickoff.be.report.entity.ReportReason;
import com.kickoff.be.report.entity.ReportTargetType;
import com.kickoff.be.report.repository.ReportChatMessageRepository;
import com.kickoff.be.report.repository.ReportRepository;
import com.kickoff.be.review.entity.Review;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.team.service.TeamAuthz;
import com.kickoff.be.user.entity.User;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고를 받는다 (계약서 §7-2 「신고」, v1.35.0).
 *
 * <b>신고는 접수되는 순간 두 가지를 한다.</b> 신고한 사람 화면에서 그 대상이 바로 빠지고
 * (조회 쪽에서 거른다), 처리 전 신고가 서로 다른 신고자 셋이 되면 운영자 확인 전에도
 * 모두에게 가린다. 전자는 "신고했는데 계속 보인다"를 없애고, 후자는 명백한 것이 하루
 * 넘게 방치되는 것을 막는다.
 *
 * <b>대상의 사본을 함께 남긴다.</b> 운영자가 보는 것은 신고 시점의 그것이어야 한다 —
 * 신고 뒤 글이 고쳐지거나 지워지면 무엇을 두고 신고했는지 알 수 없다.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 하루 제한은 <b>지난 24시간</b> 기준이다 (아래 javadoc 참고). */
    private static final Duration RATE_WINDOW = Duration.ofDays(1);

    private final ReportRepository reportRepository;
    private final ReportChatMessageRepository chatMessageRepository;
    private final MatchPostRepository postRepository;
    private final ReviewRepository reviewRepository;
    private final TeamRepository teamRepository;
    private final com.kickoff.be.team.repository.TeamMemberRepository teamMemberRepository;
    private final TeamAuthz teamAuthz;
    private final ReportContextClient reportContextClient;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReportCreatedResponse create(User reporter, ReportCreateRequest request) {
        validateShape(request);
        requireUnderDailyLimit(reporter);
        requireNotDuplicated(reporter, request);

        Target target = resolve(reporter, request);
        Report report = reportRepository.save(Report.builder()
                .reporterUserId(reporter.getId())
                .reporterTeamId(target.reporterTeamId())
                .reportedUserId(target.reportedUserId())
                .targetType(request.targetType())
                .targetId(request.targetId())
                .requestId(request.requestId())
                .reason(request.reason())
                .rightsHolder(request.rightsHolder())
                .detail(request.detail())
                .targetTitle(target.title())
                .targetBody(target.body())
                .targetRating(target.rating())
                .build());
        target.chatContext().ifPresent(context -> saveChatCopy(report, context,
                request.targetId()));

        autoHideIfEnoughReporters(request.targetType(), request.targetId());
        eventPublisher.publishEvent(ReportReceivedEvent.of(report));
        return new ReportCreatedResponse(report.getId());
    }

    // ── 요청 자체의 규칙 (계약서 §7-2)

    /**
     * 어노테이션으로 표현할 수 없는 짝 규칙들.
     *
     * <b>사유와 대상이 어긋난 신고를 받지 않는 이유</b>: 리뷰에 "경기 불참"이 붙은 신고가
     * 쌓이면 운영자가 무엇을 봐야 할지 알 수 없다. FE 가 대상에 맞는 사유만 보이므로
     * 정상 경로에서는 걸리지 않고, 걸리는 것은 구버전 앱이거나 직접 부르는 호출이다.
     */
    private void validateShape(ReportCreateRequest request) {
        if (!request.reason().appliesTo(request.targetType())) {
            throw invalid("reason", "이 대상에는 선택할 수 없는 사유입니다.");
        }
        if (request.reason() == ReportReason.DEFAMATION && request.rightsHolder() == null) {
            // 본인·자기 팀이면 법정 삭제 요청으로 다뤄야 한다 (제44조의2) — 누구의
            // 권리인지 모르면 그 갈림을 할 수 없다.
            throw invalid("rightsHolder", "누구의 권리가 침해됐는지 선택해 주세요.");
        }
        if (request.targetType() != ReportTargetType.CHAT_MESSAGE) {
            return;
        }
        if (request.requestId() == null) {
            throw invalid("requestId", "어느 대화의 메시지인지가 필요합니다.");
        }
        if (!Boolean.TRUE.equals(request.chatDisclosureAgreed())) {
            // 1:1 대화를 운영자가 보게 되는 일이다. 신고 버튼을 눌렀다는 사실로
            // 갈음하지 않는다.
            throw invalid("chatDisclosureAgreed",
                    "신고한 메시지와 앞뒤 대화가 운영자에게 전달되는 것에 동의해 주세요.");
        }
    }

    /**
     * 하루 10건 (계약서 §7-2).
     *
     * <b>달력의 하루가 아니라 지난 24시간이다.</b> 자정 기준이면 23시 59분에 열 건,
     * 1분 뒤에 또 열 건을 넣을 수 있다 — 막으려던 일이 그대로 일어난다.
     */
    private void requireUnderDailyLimit(User reporter) {
        long recent = reportRepository.countSince(reporter.getId(),
                OffsetDateTime.now().minus(RATE_WINDOW));
        if (recent >= Report.DAILY_LIMIT) {
            throw new BusinessException(ErrorCode.REPORT_RATE_LIMITED);
        }
    }

    private void requireNotDuplicated(User reporter, ReportCreateRequest request) {
        if (reportRepository.existsPendingBy(reporter.getId(), request.targetType(),
                request.targetId())) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_EXISTS);
        }
    }

    // ── 대상 해석과 사본

    /**
     * 대상을 찾아 사본을 뜨고, 자기 것인지 본다.
     *
     * <b>자기 것은 신고할 수 없다</b>(계약서 §7-2). 내 팀의 글을 내가 신고하는 길이 열리면
     * 자기 글을 스스로 가리는 수단이 되고, 그 수를 세어 자동 가림까지 걸 수 있다.
     */
    private Target resolve(User reporter, ReportCreateRequest request) {
        Long reporterTeamId = reporterTeamIdOf(reporter);
        return switch (request.targetType()) {
            case POST -> resolvePost(reporter, request, reporterTeamId);
            case REVIEW -> resolveReview(reporter, request, reporterTeamId);
            case TEAM -> resolveTeam(reporter, request, reporterTeamId);
            case CHAT_MESSAGE -> resolveChatMessage(reporter, request, reporterTeamId);
        };
    }

    private Target resolvePost(User reporter, ReportCreateRequest request, Long reporterTeamId) {
        MatchPost post = postRepository.findWithTeamAndOwnerById(request.targetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        requireNotMine(post.getTeam(), reporter);
        return new Target(reporterTeamId, post.getTeam().getOwner().getId(),
                post.getTitle(), post.getContent(), null, java.util.Optional.empty());
    }

    private Target resolveReview(User reporter, ReportCreateRequest request,
                                 Long reporterTeamId) {
        Review review = reviewRepository.findById(request.targetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        requireNotMine(review.getReviewerTeam(), reporter);
        return new Target(reporterTeamId, review.getReviewerTeam().getOwner().getId(),
                null, review.getComment(), review.getRating(), java.util.Optional.empty());
    }

    private Target resolveTeam(User reporter, ReportCreateRequest request, Long reporterTeamId) {
        Team team = teamRepository.findById(request.targetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
        requireNotMine(team, reporter);
        return new Target(reporterTeamId, team.getOwner().getId(),
                team.getName(), team.getIntroduction(), null, java.util.Optional.empty());
    }

    /**
     * 채팅 메시지는 <b>채팅 서버가 판정한다</b> (계약서 §10 의 검사 순서) — 그 방의
     * 당사자인지, 신고자가 나가기 전의 메시지인지, 시스템 메시지나 자기 팀이 보낸
     * 메시지는 아닌지. 여기서 다시 판정하면 두 서버의 규칙이 어긋나는 날이 온다.
     */
    private Target resolveChatMessage(User reporter, ReportCreateRequest request,
                                      Long reporterTeamId) {
        ReportContext context = reportContextClient.fetch(request.requestId(),
                request.targetId(), reporter.getId());
        if (context == null || context.reportedMessage() == null) {
            throw new BusinessException(ErrorCode.CHAT_UPSTREAM_UNAVAILABLE);
        }
        return new Target(reporterTeamId, context.reportedMessage().senderUserId(),
                null, context.reportedMessage().content(), null,
                java.util.Optional.of(context));
    }

    /**
     * 신고자가 "속한" 팀 (계약서 §7-2 — 같은 팀의 신고는 1명으로 센다).
     *
     * <b>감독만이 아니라 팀원도 포함한다.</b> 감독만 보면 한 팀의 팀원 셋이 달려들어
     * 상대 글을 가릴 수 있다 — 막으려던 바로 그 일이다.
     *
     * 여러 팀에 속한 계정은 <b>소유한 팀, 없으면 가장 먼저 들어간 팀</b> 하나로 센다.
     * 제품상 한 사람이 여러 팀에서 뛰는 일이 드물고, 집합이 겹치는지까지 보려면 신고
     * 한 건마다 모든 신고자의 소속을 끌어와야 한다.
     */
    private Long reporterTeamIdOf(User reporter) {
        return teamRepository.findByOwnerId(reporter.getId())
                .map(Team::getId)
                .orElseGet(() -> teamMemberRepository.findByUser_IdOrderByIdAsc(reporter.getId())
                        .stream()
                        .findFirst()
                        .map(member -> member.getTeam().getId())
                        .orElse(null));
    }

    private void requireNotMine(Team team, User reporter) {
        if (teamAuthz.isMemberOfAnyKind(team, reporter.getId())) {
            throw invalid("targetId", "자기 팀의 것은 신고할 수 없습니다.");
        }
    }

    private void saveChatCopy(Report report, ReportContext context, Long reportedMessageId) {
        List<ReportChatMessage> copies = context.messages().stream()
                .map(message -> ReportChatMessage.builder()
                        .reportId(report.getId())
                        .messageId(message.id())
                        .reported(message.id().equals(reportedMessageId))
                        .senderUserId(message.senderUserId())
                        .senderTeamId(message.senderTeamId())
                        .messageType(message.type())
                        .content(message.content())
                        .sentAt(message.createdAt())
                        .build())
                .toList();
        chatMessageRepository.saveAll(copies);
    }

    // ── 자동 임시 가림 (계약서 §7-2 「신고의 즉시 효과」 2)

    /**
     * 처리 전 신고가 서로 다른 신고자 셋이면 운영자 확인 전까지 가린다.
     *
     * <b>같은 팀의 신고는 한 명으로 센다.</b> 그러지 않으면 한 팀의 감독·관리자·팀원 셋이
     * 달려들어 상대 팀 글을 30일 가릴 수 있다 — 신고가 공격 수단이 된다.
     *
     * 팀·채팅은 자동으로 가리지 않는다({@link ReportTargetType#isAutoHidable}).
     */
    private void autoHideIfEnoughReporters(ReportTargetType targetType, Long targetId) {
        if (!targetType.isAutoHidable()) {
            return;
        }
        List<Report> pending = reportRepository.findPendingOf(targetType, targetId);
        if (distinctReporters(pending) < Report.AUTO_HIDE_REPORTERS) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (targetType == ReportTargetType.POST) {
            postRepository.findById(targetId).ifPresent(post -> post.hide(now));
        } else {
            reviewRepository.findById(targetId).ifPresent(review -> review.hide(now));
        }
        pending.forEach(Report::markAutoHidden);
    }

    /** 팀이 있으면 팀 하나로, 없으면 계정 하나로 센다. */
    private long distinctReporters(List<Report> pending) {
        Set<String> keys = new HashSet<>();
        for (Report report : pending) {
            keys.add(report.getReporterTeamId() != null
                    ? "t" + report.getReporterTeamId()
                    : "u" + report.getReporterUserId());
        }
        return keys.size();
    }

    private BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED,
                List.of(new ErrorResponse.FieldError(field, message)));
    }

    /**
     * 대상에서 뽑아낸 것들.
     *
     * @param reporterTeamId 신고자가 감독인 팀 (같은 팀의 신고를 1명으로 세기 위해)
     * @param reportedUserId 신고된 쪽 계정 — 통지와 이용 제한의 대상
     */
    private record Target(Long reporterTeamId, Long reportedUserId, String title, String body,
                          Integer rating, java.util.Optional<ReportContext> chatContext) {
    }
}
