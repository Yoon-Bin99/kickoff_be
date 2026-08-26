package com.kickoff.be.team.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.repository.MatchRequestRepository;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.team.dto.MatchRecordCreateRequest;
import com.kickoff.be.team.dto.TeamRecordCreateRequest;
import com.kickoff.be.team.dto.TeamRecordResponse;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.TeamRecord;
import com.kickoff.be.team.repository.TeamRecordRepository;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수동 입력 경기 기록 (계약서 §4-1, v1.8.0).
 *
 * 수정이 없고 삭제만 있다. 기록 무결성보다 단순함을 택한 v1 결정이라, 고치려면 지우고
 * 다시 넣는다. 덕분에 스코어가 나중에 바뀌지 않아 전적 요약도 항상 기록과 일치한다.
 */
@Service
@RequiredArgsConstructor
public class TeamRecordService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final TeamRecordRepository teamRecordRepository;
    private final TeamAuthz teamAuthz;
    private final MatchRequestRepository requestRepository;
    private final TeamRepository teamRepository;

    /** 인증 불필요. playedOn DESC, 같은 날이면 id DESC. */
    @Transactional(readOnly = true)
    public PageResponse<TeamRecordResponse> list(Long teamId, Integer page, Integer size) {
        teamAuthz.requireTeam(teamId);
        // 목록 조회와 같은 방식으로 자른다 — 최대치를 넘겨도 400 이 아니라 잘라서 돌려준다
        int pageSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageNumber = page == null || page < 0 ? 0 : page;
        return PageResponse.of(teamRecordRepository
                .findByTeamIdOrdered(teamId, PageRequest.of(pageNumber, pageSize))
                .map(TeamRecordResponse::of));
    }

    @Transactional
    public TeamRecordResponse add(Long teamId, User user, TeamRecordCreateRequest request) {
        Team team = teamAuthz.requireWriter(teamId, user);
        return TeamRecordResponse.of(teamRecordRepository.save(TeamRecord.builder()
                .team(team)
                .playedOn(request.playedOn())
                .opponentName(request.opponentName())
                .ourScore(request.ourScore())
                .opponentScore(request.opponentScore())
                .memo(request.memo())
                .build()));
    }

    /**
     * 매칭에서 기록을 만든다 (계약서 §4-1, v1.10.0).
     *
     * 상대 팀 이름과 경기 날짜는 <b>서버가 매칭에서 유도</b>한다. 클라이언트가 보낸 값을
     * 믿으면 "매칭에서 만든 기록"인데 상대가 엉뚱한 팀일 수 있다. 스코어만 사람이 적는다 —
     * 앱은 결과를 알 방법이 없다.
     *
     * 검사 순서는 리뷰와 같이 <b>권한 → 상태</b>다. 제3자에게는 그 매칭이 수락됐는지,
     * 경기가 끝났는지조차 알려줄 이유가 없어서 403 을 먼저 낸다.
     *
     * 권한이 소유자 전용인 건 §4-2 권한표 그대로다. 관리자는 팀 페이지를 고칠 수 있지만
     * 매칭 당사자로서의 행위(수락·리뷰·전적 기록)는 소유자 몫이다.
     */
    @Transactional
    public TeamRecordResponse addFromMatch(Long requestId, User user,
                                           MatchRecordCreateRequest request) {
        MatchRequest matchRequest = requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        // 팀이 없으면 어느 매칭의 당사자도 될 수 없다 — TEAM_REQUIRED 가 아니라 FORBIDDEN 이다
        // (리뷰가 v1.2.2 에서 정한 것과 같은 판단).
        Team myTeam = teamRepository.findByOwnerId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        Team opponent = counterpartOf(matchRequest, myTeam);

        if (!matchRequest.isAccepted() || !matchRequest.getPost().hasPassed()) {
            throw new BusinessException(ErrorCode.RECORD_NOT_AVAILABLE);
        }
        if (teamRecordRepository.existsByRequest_IdAndTeamId(requestId, myTeam.getId())) {
            throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS);
        }

        MatchPost post = matchRequest.getPost();
        return TeamRecordResponse.of(teamRecordRepository.save(TeamRecord.builder()
                .team(myTeam)
                .playedOn(post.getMatchAt().toLocalDate())
                .opponentName(opponent.getName())
                .ourScore(request.ourScore())
                .opponentScore(request.opponentScore())
                .memo(request.memo())
                .request(matchRequest)
                .build()));
    }

    /**
     * 상대는 요청 본문이 아니라 매칭 관계에서 정해진다 (리뷰의 counterpartOf 와 같다).
     * 글 작성 팀이 쓰면 신청 팀을, 신청 팀이 쓰면 글 작성 팀을 상대로 적는다.
     */
    private Team counterpartOf(MatchRequest request, Team myTeam) {
        MatchPost post = request.getPost();
        if (post.getTeam().getId().equals(myTeam.getId())) {
            return request.getApplicantTeam();
        }
        if (request.getApplicantTeam().getId().equals(myTeam.getId())) {
            return post.getTeam();
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    @Transactional
    public void delete(Long teamId, Long recordId, User user) {
        teamAuthz.requireWriter(teamId, user);
        // 멤버와 같은 이유로 (id, teamId) 로 찾는다 — id 만 보면 남의 팀 기록을 지울 수 있다
        TeamRecord record = teamRecordRepository.findByIdAndTeamId(recordId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        teamRecordRepository.delete(record);
    }
}
