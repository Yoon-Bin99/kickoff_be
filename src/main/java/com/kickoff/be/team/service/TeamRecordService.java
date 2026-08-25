package com.kickoff.be.team.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.team.dto.TeamRecordCreateRequest;
import com.kickoff.be.team.dto.TeamRecordResponse;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.TeamRecord;
import com.kickoff.be.team.repository.TeamRecordRepository;
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

    @Transactional
    public void delete(Long teamId, Long recordId, User user) {
        teamAuthz.requireWriter(teamId, user);
        // 멤버와 같은 이유로 (id, teamId) 로 찾는다 — id 만 보면 남의 팀 기록을 지울 수 있다
        TeamRecord record = teamRecordRepository.findByIdAndTeamId(recordId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        teamRecordRepository.delete(record);
    }
}
