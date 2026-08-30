package com.kickoff.be.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.common.ContactInfo;
import com.kickoff.be.common.PaymentInfo;
import com.kickoff.be.matchrequest.entity.RequestStatus;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.team.dto.RecordSummary;
import com.kickoff.be.team.dto.TeamResponse;
import com.kickoff.be.team.entity.TeamRole;
import com.kickoff.be.team.entity.SkillLevel;
import java.time.OffsetDateTime;

/**
 * 글 상세. 계좌는 평문 필드로 노출하지 않고 payment 안에 담아 내려가며,
 * 수락된 신청 팀과 작성자 본인에게만 채워진다 (계약서 §5).
 */
public record PostDetail(
        Long id,
        String title,
        String content,
        OffsetDateTime matchAt,
        String location,
        String region,
        SkillLevel preferredSkillLevel,
        Integer rentalFee,
        Integer depositAmount,
        Double latitude,
        Double longitude,
        PostStatus status,
        int viewCount,
        long requestCount,
        TeamResponse team,
        @JsonProperty("isAuthor") boolean isAuthor,
        RequestStatus myRequestStatus,
        ContactInfo contact,
        PaymentInfo payment,
        OffsetDateTime createdAt
) {

    public static PostDetail of(MatchPost post, long requestCount, Long viewerId,
                                RequestStatus myRequestStatus, ContactInfo contact,
                                PaymentInfo payment, ReviewStats teamReviewStats,
                                TeamRole teamRole, RecordSummary teamRecordSummary) {
        return new PostDetail(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getMatchAt(),
                post.getLocation(),
                post.getRegion(),
                post.getPreferredSkillLevel(),
                post.getRentalFee(),
                post.getDepositAmount(),
                post.getLatitude(),
                post.getLongitude(),
                post.getStatus(),
                post.getViewCount(),
                requestCount,
                // 글 상세의 팀 카드에는 가입 신청 상태를 싣지 않는다. 그 화면에서 가입
                // 버튼을 누르는 흐름이 없어서, 조회를 한 번 더 할 이유가 없다 (계약서 §4-3).
                TeamResponse.of(post.getTeam(), viewerId, teamReviewStats,
                        teamRole, teamRecordSummary, null),
                post.isWrittenBy(viewerId),
                myRequestStatus,
                contact,
                payment,
                post.getCreatedAt()
        );
    }
}
