package com.kickoff.be.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.common.ContactInfo;
import com.kickoff.be.common.PaymentInfo;
import com.kickoff.be.matchrequest.RequestStatus;
import com.kickoff.be.post.FieldType;
import com.kickoff.be.post.MatchPost;
import com.kickoff.be.post.PostStatus;
import com.kickoff.be.team.SkillLevel;
import com.kickoff.be.team.dto.TeamResponse;
import java.time.OffsetDateTime;

/**
 * 글 상세. 계좌 정보는 평문 필드로 노출하지 않고, 수락된 신청 팀에게만
 * payment 안에 담아 내려간다. 작성자가 봐도 payment 는 null 이다 (계약서 §5).
 */
public record PostDetail(
        Long id,
        String title,
        String content,
        OffsetDateTime matchAt,
        String location,
        String region,
        FieldType fieldType,
        SkillLevel preferredSkillLevel,
        Integer rentalFee,
        Integer depositAmount,
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
                                PaymentInfo payment) {
        return new PostDetail(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getMatchAt(),
                post.getLocation(),
                post.getRegion(),
                post.getFieldType(),
                post.getPreferredSkillLevel(),
                post.getRentalFee(),
                post.getDepositAmount(),
                post.getStatus(),
                post.getViewCount(),
                requestCount,
                TeamResponse.of(post.getTeam(), viewerId),
                post.isWrittenBy(viewerId),
                myRequestStatus,
                contact,
                payment,
                post.getCreatedAt()
        );
    }
}
