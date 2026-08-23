package com.kickoff.be.post.dto;

import com.kickoff.be.post.FieldType;
import com.kickoff.be.post.MatchPost;
import com.kickoff.be.post.PostStatus;
import com.kickoff.be.team.SkillLevel;
import com.kickoff.be.team.dto.TeamSummary;
import java.time.OffsetDateTime;

public record PostSummary(
        Long id,
        String title,
        OffsetDateTime matchAt,
        String location,
        String region,
        FieldType fieldType,
        SkillLevel preferredSkillLevel,
        Integer costPerTeam,
        PostStatus status,
        long requestCount,
        TeamSummary team,
        OffsetDateTime createdAt
) {

    public static PostSummary of(MatchPost post, long requestCount) {
        return new PostSummary(
                post.getId(),
                post.getTitle(),
                post.getMatchAt(),
                post.getLocation(),
                post.getRegion(),
                post.getFieldType(),
                post.getPreferredSkillLevel(),
                post.getCostPerTeam(),
                post.getStatus(),
                requestCount,
                TeamSummary.from(post.getTeam()),
                post.getCreatedAt()
        );
    }
}
