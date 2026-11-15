package com.kickoff.be.chatlink.dto;

import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.entity.RequestStatus;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.team.dto.TeamSummary;
import com.kickoff.be.team.entity.Team;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 채팅 서버가 방 하나를 그리는 데 필요한 전부 (계약서 §10).
 *
 * <b>판정은 하지 않는다.</b> 권한(두 팀 주장인가)·상태(ACCEPTED 인가)는 채팅 서버가
 * 이 값을 보고 정하고, 그래서 {@code rooms/{id}} 는 <b>상태와 무관하게</b> 돌려준다.
 * 여기서 걸러 버리면 채팅 서버가 §6-1 의 검사 순서(권한 먼저)를 지킬 수 없다 — 403 이어야
 * 할 자리에 404 가 나가 제3자에게 매칭 상태가 새어 나간다.
 *
 * {@code updatedAt} 은 신청의 마지막 상태 변경이다. 방 목록 정렬의 보조값으로 쓰인다 —
 * 메시지가 하나도 없는 방끼리의 순서가 이 값으로 정해진다(§6-1).
 */
public record RoomContext(
        Long requestId,
        RequestStatus status,
        Long postId,
        String postTitle,
        OffsetDateTime matchAt,
        OffsetDateTime updatedAt,
        Party postTeam,
        Party applicantTeam
) {

    /**
     * 방의 한쪽. {@code ownerUserId} 가 따로 있는 이유는 채팅 서버가 <b>권한 판정에</b>
     * 그 값만 쓰기 때문이다 — TeamSummary 에는 주장이 누구인지가 없다(§2, 목록 카드용이라
     * 일부러 owner 를 싣지 않는다).
     */
    public record Party(Long ownerUserId, TeamSummary team) {
    }

    public static RoomContext of(MatchRequest request, Map<Long, ReviewStats> stats) {
        MatchPost post = request.getPost();
        return new RoomContext(
                request.getId(),
                request.getStatus(),
                post.getId(),
                post.getTitle(),
                post.getMatchAt(),
                request.getUpdatedAt(),
                partyOf(post.getTeam(), stats),
                partyOf(request.getApplicantTeam(), stats));
    }

    private static Party partyOf(Team team, Map<Long, ReviewStats> stats) {
        return new Party(team.getOwner().getId(),
                TeamSummary.of(team, stats.getOrDefault(team.getId(), ReviewStats.EMPTY)));
    }
}
