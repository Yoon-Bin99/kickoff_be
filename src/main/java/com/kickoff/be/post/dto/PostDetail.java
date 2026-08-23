package com.kickoff.be.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.common.ContactInfo;
import com.kickoff.be.matchrequest.RequestStatus;
import com.kickoff.be.post.FieldType;
import com.kickoff.be.post.MatchPost;
import com.kickoff.be.post.PostStatus;
import com.kickoff.be.team.SkillLevel;
import com.kickoff.be.team.dto.TeamResponse;
import java.time.OffsetDateTime;

public record PostDetail(
        Long id,
        String title,
        String content,
        OffsetDateTime matchAt,
        String location,
        String region,
        FieldType fieldType,
        SkillLevel preferredSkillLevel,
        Integer costPerTeam,
        PostStatus status,
        int viewCount,
        long requestCount,
        TeamResponse team,
        @JsonProperty("isAuthor") boolean isAuthor,
        RequestStatus myRequestStatus,
        ContactInfo contact,
        OffsetDateTime createdAt
) {

    public static PostDetail of(MatchPost post, long requestCount, Long viewerId,
                                RequestStatus myRequestStatus, ContactInfo contact) {
        return new PostDetail(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getMatchAt(),
                post.getLocation(),
                post.getRegion(),
                post.getFieldType(),
                post.getPreferredSkillLevel(),
                post.getCostPerTeam(),
                post.getStatus(),
                post.getViewCount(),
                requestCount,
                TeamResponse.of(post.getTeam(), viewerId),
                post.isWrittenBy(viewerId),
                myRequestStatus,
                contact,
                post.getCreatedAt()
        );
    }
}
