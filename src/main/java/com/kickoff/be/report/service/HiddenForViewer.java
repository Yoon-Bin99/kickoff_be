package com.kickoff.be.report.service;

import com.kickoff.be.report.entity.ReportTargetType;
import com.kickoff.be.report.repository.ReportRepository;
import com.kickoff.be.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보는 사람에게만 가려지는 것 (계약서 §7-2 「신고의 즉시 효과」 1).
 *
 * <b>신고한 사람 화면에서는 바로 빠진다.</b> 운영자 조치를 기다리는 동안 신고까지 한 글이
 * 계속 눈앞에 있으면, 신고가 아무 일도 하지 않은 것처럼 보인다. 다른 사람에게는 그대로
 * 보인다 — 한 사람의 신고로 모두의 화면에서 사라지면 그것이 공격 수단이 된다.
 *
 * 글·리뷰 조회가 신고 표를 직접 조인하지 않도록 여기서 id 만 모아 준다. 조인으로 엮으면
 * 두 도메인이 쿼리 하나에 묶이고, 다음에 조건이 하나 더 붙을 때(차단) 더 엉킨다.
 */
@Component
@RequiredArgsConstructor
public class HiddenForViewer {

    private final ReportRepository reportRepository;

    /** 이 사람이 신고해 둔 모집글. 비로그인이면 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<Long> postIdsFor(User viewer) {
        return targetIdsFor(viewer, ReportTargetType.POST);
    }

    /** 이 사람이 신고해 둔 리뷰. */
    @Transactional(readOnly = true)
    public List<Long> reviewIdsFor(User viewer) {
        return targetIdsFor(viewer, ReportTargetType.REVIEW);
    }

    private List<Long> targetIdsFor(User viewer, ReportTargetType targetType) {
        if (viewer == null) {
            return List.of();
        }
        return reportRepository.findPendingTargetIdsBy(viewer.getId(), targetType);
    }
}
