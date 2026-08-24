package com.kickoff.be.matchrequest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.common.ContactInfo;
import com.kickoff.be.common.PaymentInfo;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.entity.RequestStatus;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.team.dto.TeamSummary;
import java.time.OffsetDateTime;

public record RequestResponse(
        Long id,
        Long postId,
        String postTitle,
        PostStatus postStatus,
        OffsetDateTime matchAt,
        TeamSummary applicantTeam,
        TeamSummary postTeam,
        String message,
        RequestStatus status,
        ContactInfo contact,
        PaymentInfo payment,
        @JsonProperty("depositPaid") boolean depositPaid,
        @JsonProperty("myReviewWritten") boolean myReviewWritten,
        OffsetDateTime createdAt
) {

    /**
     * myReviewWritten 은 <b>요청자의 팀</b> 기준이다 (계약서 §6). 신청 하나에 대해 양 팀이
     * 서로 다른 값을 보므로 엔티티에서 끌어낼 수 없고, 호출자가 보는 이의 팀으로 계산해 넘긴다.
     */
    public static RequestResponse of(MatchRequest request, Long viewerId,
                                     boolean myReviewWritten) {
        MatchPost post = request.getPost();
        return new RequestResponse(
                request.getId(),
                post.getId(),
                post.getTitle(),
                post.getStatus(),
                post.getMatchAt(),
                TeamSummary.from(request.getApplicantTeam()),
                // 매칭의 양 팀이 응답에 모두 담긴다 (계약서 §6, v1.2.1). post.team 은
                // RequestResponse 를 만드는 모든 조회가 이미 fetch 해 오므로 추가 쿼리가 없다.
                TeamSummary.from(post.getTeam()),
                request.getMessage(),
                request.getStatus(),
                contactFor(request, viewerId),
                paymentFor(request, viewerId),
                request.isDepositPaid(),
                myReviewWritten,
                request.getCreatedAt()
        );
    }

    /**
     * 수락된 신청에 한해, 매칭된 두 팀에게만 상대 연락처를 준다.
     * 글 작성자가 보면 신청 팀 연락처, 신청 팀이 보면 글 작성 팀 연락처.
     */
    private static ContactInfo contactFor(MatchRequest request, Long viewerId) {
        if (viewerId == null || !request.isAccepted()) {
            return null;
        }
        MatchPost post = request.getPost();
        if (post.isWrittenBy(viewerId)) {
            return ContactInfo.from(request.getApplicantTeam().getOwner());
        }
        if (request.getApplicantTeam().isOwnedBy(viewerId)) {
            return ContactInfo.from(post.getTeam().getOwner());
        }
        return null;
    }

    /**
     * 계좌는 <b>돈을 보낼 쪽</b>, 즉 수락된 신청 팀에게만 보인다.
     * 작성자는 payment 가 null 이고 depositPaid 로 입금 확인 상태만 본다 (계약서 §6).
     */
    private static PaymentInfo paymentFor(MatchRequest request, Long viewerId) {
        if (viewerId == null || !request.isAccepted()) {
            return null;
        }
        if (!request.getApplicantTeam().isOwnedBy(viewerId)) {
            return null;
        }
        MatchPost post = request.getPost();
        if (!post.hasDepositAccount()) {
            return null;
        }
        return new PaymentInfo(post.getDepositAmount(), post.getBankName(),
                post.getAccountNumber(), post.getAccountHolder(), request.isDepositPaid());
    }
}
