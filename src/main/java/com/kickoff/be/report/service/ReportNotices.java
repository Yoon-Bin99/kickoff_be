package com.kickoff.be.report.service;

import com.kickoff.be.common.Josa;
import com.kickoff.be.report.entity.ReportTargetType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import org.springframework.stereotype.Component;

/**
 * 통지 문구 (계약서 §7-2 — 법무 초안 3-3절, v1.35.0).
 *
 * <b>문구를 코드에 박지 않는다.</b> 이 문장들은 법이 요구하는 통지의 내용이고(정보통신망법
 * 제44조의2 제2항), 고치려면 법무 문서를 먼저 고쳐야 한다. 리소스 파일에 두면 무엇이
 * 나가는지 한눈에 보이고, 코드 수정 없이 맞춰 넣을 수 있다.
 *
 * <b>신고자가 누구인지는 어느 통지에도 들어가지 않는다</b> — 여기 어떤 문구도 신고자를
 * 가리키는 자리를 두지 않는다.
 */
@Component
public class ReportNotices {

    private static final String RESOURCE = "report-notice.properties";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    private final Properties messages;

    public ReportNotices() {
        this.messages = read();
        // 기동에서 멈추는 쪽을 택한다. 문구가 비면 통지가 빈 메시지로 나가는데, 그건
        // 에러 없이 "통지했다"로 기록되고 나중에 통지 의무를 못 지킨 것이 된다.
        for (String key : new String[]{"reporter.acted", "reporter.dismissed",
                "reported.hidden", "reported.auto-hidden", "reported.removed",
                "reported.suspended", "reported.restored"}) {
            if (messages.getProperty(key) == null || messages.getProperty(key).isBlank()) {
                throw new IllegalStateException(RESOURCE + " 에 " + key + " 문구가 없습니다.");
            }
        }
    }

    /** 신고자에게 — 조치함. */
    public String toReporterActed(String actionName) {
        return format("reporter.acted", actionName);
    }

    /** 신고자에게 — 조치하지 않음. */
    public String toReporterDismissed() {
        return format("reporter.dismissed");
    }

    /** 게재자에게 — 운영자의 임시 가림. */
    public String toReportedHidden(ReportTargetType targetType, OffsetDateTime until,
                                   String reasonName) {
        return format("reported.hidden", subject(targetType), DATE.format(until), reasonName);
    }

    /**
     * 게재자에게 — 신고 3건으로 자동 가림. "위반"이라고 단정하지 않는다.
     *
     * <b>기한 날짜를 함께 적는다</b> (법무 권고, v1.36.0). 운영자 조치로 가려졌을 때와
     * 같은 양식이어야 한다 — "최대 30일"만 적으면 언제까지 이의를 낼 수 있는지가
     * 받는 사람에게 날짜로 와닿지 않는다.
     */
    public String toReportedAutoHidden(ReportTargetType targetType, OffsetDateTime until) {
        return format("reported.auto-hidden", targetName(targetType), DATE.format(until));
    }

    public String toReportedRemoved(ReportTargetType targetType, String reasonName) {
        return format("reported.removed", subject(targetType), reasonName);
    }

    public String toReportedSuspended(int days, String reasonName) {
        return format("reported.suspended", String.valueOf(days), reasonName);
    }

    public String toReportedRestored(ReportTargetType targetType) {
        return format("reported.restored", subject(targetType));
    }

    /**
     * 대상 이름에 주격 조사를 붙인다 — "모집글이", "리뷰가", "팀 소개가".
     *
     * 규칙 자체는 {@link Josa} 에 있다 — 매칭 알림 문구도 같은 규칙을 쓰므로(v1.38.3)
     * 두 벌로 두면 한쪽만 고쳐지는 날이 온다.
     */
    private String subject(ReportTargetType targetType) {
        return Josa.subject(targetName(targetType));
    }

    /** 통지에 쓰는 대상 이름. 팀은 "팀 소개"다 — 조치가 닿는 것이 소개뿐이다(§7-2). */
    private String targetName(ReportTargetType targetType) {
        return targetType == ReportTargetType.TEAM ? "팀 소개" : targetType.displayName();
    }

    private String format(String key, Object... args) {
        String template = messages.getProperty(key);
        return args.length == 0 ? template : MessageFormat.format(template, args);
    }

    private Properties read() {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(RESOURCE + " 를 찾을 수 없습니다.");
            }
            Properties loaded = new Properties();
            // UTF-8 로 읽는다 — properties 의 기본은 ISO-8859-1 이라 한글이 깨진다.
            loaded.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return loaded;
        } catch (IOException e) {
            throw new IllegalStateException(RESOURCE + " 를 읽지 못했습니다.", e);
        }
    }
}
