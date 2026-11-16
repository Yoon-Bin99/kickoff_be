package com.kickoff.be.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.common.ContactInfo;
import com.kickoff.be.common.ModerationResponse;
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
        /** 경기장 주소 (계약서 §5, v1.29.0). 직접 입력한 글·옛 글은 null. */
        String locationAddress,
        String region,
        SkillLevel preferredSkillLevel,
        Integer rentalFee,
        Integer depositAmount,

        /**
         * 사용자가 고른 장소의 카카오 장소 ID (계약서 §5-1, v1.37.0). 없으면 null.
         *
         * <b>목록({@code PostSummary})에는 싣지 않는다</b> — 목록은 지도를 그리지 않는다.
         * 지도는 보는 사람의 기기가 이 값과 {@code placeQuery} 로 다시 조회해 그린다.
         */
        String kakaoPlaceId,

        /** 그 장소를 찾을 때 사용자가 친 검색어 (계약서 §5-1). */
        String placeQuery,

        PostStatus status,
        /**
         * 운영 조치 (계약서 §7-2, v1.35.0). 조치가 없으면 null.
         *
         * 가려진 글의 상세는 <b>작성자에게만</b> 열리므로(그 밖에는 410), 이 칸이 채워진
         * 응답을 받는 사람은 작성자뿐이다.
         */
        ModerationResponse moderation,
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
                                TeamRole teamRole, RecordSummary teamRecordSummary,
                                boolean teamBlockedByMe) {
        return new PostDetail(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getMatchAt(),
                post.getLocation(),
                post.getLocationAddress(),
                post.getRegion(),
                post.getPreferredSkillLevel(),
                post.getRentalFee(),
                post.getDepositAmount(),
                post.getKakaoPlaceId(),
                post.getPlaceQuery(),
                post.getStatus(),
                ModerationResponse.of(post.getModeration()),
                post.getViewCount(),
                requestCount,
                // 글 상세의 팀 카드에는 가입 신청 상태를 싣지 않는다. 그 화면에서 가입
                // 버튼을 누르는 흐름이 없어서, 조회를 한 번 더 할 이유가 없다 (계약서 §4-3).
                TeamResponse.of(post.getTeam(), viewerId, teamReviewStats,
                        teamRole, teamRecordSummary, null, teamBlockedByMe),
                post.isWrittenBy(viewerId),
                myRequestStatus,
                contact,
                payment,
                post.getCreatedAt()
        );
    }
}
