package com.kickoff.be.team.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.team.dto.SquadItemRequest;
import com.kickoff.be.team.dto.SquadResponse;
import com.kickoff.be.team.dto.SquadSaveRequest;
import com.kickoff.be.team.dto.SquadSummary;
import com.kickoff.be.team.entity.Formation;
import com.kickoff.be.team.entity.Squad;
import com.kickoff.be.team.entity.SquadSlot;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.TeamMember;
import com.kickoff.be.team.repository.SquadRepository;
import com.kickoff.be.team.repository.TeamMemberRepository;
import com.kickoff.be.user.entity.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스쿼드 메이커 (계약서 §4-4, v1.28.0).
 *
 * 권한 경계는 {@link TeamAuthz} 가 쥔다 — 읽기는 소속 전원, 쓰기는 OWNER·ADMIN 으로
 * §4-2 권한표의 "팀원 명단·경기 기록 쓰기"와 같은 줄이다. 여기서 역할을 직접 비교하지
 * 않는 이유는 권한표가 바뀔 때 고칠 자리를 한 곳으로 모으기 위해서다.
 *
 * <b>서버는 자리 좌표를 모른다.</b> 포메이션에서 아는 것은 "자리가 몇 개인가"뿐이고,
 * 경기장 어디인지는 FE 가 고정 테이블로 가진다(§4-4). 이미지도 서버가 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SquadService {

    /** 한 팀 최대 개수 (계약서 §4-4). */
    private static final int MAX_SQUADS_PER_TEAM = 30;

    private final SquadRepository squadRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamAuthz teamAuthz;

    @Transactional(readOnly = true)
    public List<SquadSummary> list(Long teamId, User viewer) {
        teamAuthz.requireMember(teamId, viewer);
        return squadRepository.findByTeamIdOrderByUpdatedAtDescIdDesc(teamId).stream()
                .map(SquadSummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public SquadResponse get(Long teamId, Long squadId, User viewer) {
        teamAuthz.requireMember(teamId, viewer);
        return SquadResponse.of(requireSquad(teamId, squadId));
    }

    @Transactional
    public SquadResponse create(Long teamId, User user, SquadSaveRequest request) {
        Team team = teamAuthz.requireWriter(teamId, user);
        // 개수는 만들 때만 본다. 수정은 개수를 늘리지 않으므로, 상한에 걸린 팀이 기존
        // 스쿼드를 고치지도 못하게 되면 "지워 주세요" 안내가 막다른 길이 된다.
        if (squadRepository.countByTeamId(teamId) >= MAX_SQUADS_PER_TEAM) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                    new ErrorResponse.FieldError("squads", "오래된 스쿼드를 지워 주세요")));
        }
        Squad squad = Squad.of(team, request.title(), request.formation());
        squad.addItems(buildItems(teamId, squad, request));
        return SquadResponse.of(squadRepository.save(squad));
    }

    /**
     * 전체 교체 (계약서 §4-4). 제목·포메이션·자리를 통째로 갈아치운다.
     *
     * 포메이션이 바뀌면 자리 개수가 바뀌므로 부분 수정이 성립하지 않는다 — 섞어서 받으면
     * "자리 개수 = 총원"이 깨진 채 저장될 길이 생긴다.
     */
    @Transactional
    public SquadResponse update(Long teamId, Long squadId, User user, SquadSaveRequest request) {
        teamAuthz.requireWriter(teamId, user);
        Squad squad = requireSquad(teamId, squadId);

        // 검증을 먼저 끝낸다. 지우고 나서 400 을 내도 트랜잭션이 되돌리긴 하지만,
        // 같은 트랜잭션 안에서 지운 뒤에 던지면 아래 flush 가 헛일이 된다.
        List<SquadSlot> items = buildItems(teamId, squad, request);

        squad.retitle(request.title(), request.formation());
        squad.clearItems();
        // 삭제를 먼저 내보낸다. 한 flush 안에 두면 하이버네이트가 삽입을 먼저 보내
        // 같은 자리 번호가 겹쳐 유니크 제약에 걸린다 (Squad.clearItems 주석 참고).
        squadRepository.flush();
        squad.addItems(items);
        return SquadResponse.of(squad);
    }

    @Transactional
    public void delete(Long teamId, Long squadId, User user) {
        teamAuthz.requireWriter(teamId, user);
        squadRepository.delete(requireSquad(teamId, squadId));
    }

    /**
     * <b>teamId 로 함께 찾는다.</b> squadId 만으로 찾으면 남의 팀 스쿼드를 자기 팀 주소에
     * 끼워 읽거나 지울 수 있다 — 권한 검사는 teamId 에 대해 통과했으니 그대로 넘어간다.
     */
    private Squad requireSquad(Long teamId, Long squadId) {
        return squadRepository.findWithItemsByIdAndTeamId(squadId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SQUAD_NOT_FOUND));
    }

    /**
     * 본문을 자리 목록으로 바꾼다. 검증이 전부 여기 모여 있다 (계약서 §4-4).
     *
     * 검증 순서가 의미를 갖는다. 개수를 먼저 보고, 그다음 팀원 소속, 마지막이 중복이다 —
     * 개수가 틀린 상태에서 자리별 오류를 돌려주면 사용자가 받는 메시지가 엉뚱해진다
     * (자리 11개를 기대하는데 9개를 보냈으면, 9개 안의 중복보다 개수가 먼저 문제다).
     */
    private List<SquadSlot> buildItems(Long teamId, Squad squad, SquadSaveRequest request) {
        Formation formation = request.formation();
        List<SquadItemRequest> slots = request.slots();
        List<SquadItemRequest> bench = request.benchOrEmpty();

        if (slots.size() != formation.totalPlayers()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                    new ErrorResponse.FieldError("slots",
                            "%s 는 자리가 %d개여야 합니다. (받은 개수: %d)"
                                    .formatted(formation.name(), formation.totalPlayers(),
                                            slots.size()))));
        }

        Map<Long, TeamMember> members = loadMembers(teamId, slots, bench);

        List<SquadSlot> items = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (int i = 0; i < slots.size(); i++) {
            items.add(toItem(squad, slots.get(i), i, false, members, used, "slots"));
        }
        for (int i = 0; i < bench.size(); i++) {
            items.add(toItem(squad, bench.get(i), i, true, members, used, "bench"));
        }
        return items;
    }

    /**
     * 본문에 나온 memberId 를 한 번에 읽고 <b>이 팀 소속인지</b> 확인한다.
     *
     * 건마다 조회하면 자리 수만큼 쿼리가 나간다. 그리고 없는 id 와 남의 팀 id 를 같은
     * 오류로 다룬다 — 사용자에게는 똑같이 "이 팀의 팀원이 아닙니다"이고, 구분해서
     * 알려 주면 남의 팀 명단에 어떤 id 가 있는지 떠볼 수 있는 통로가 된다.
     */
    private Map<Long, TeamMember> loadMembers(Long teamId, List<SquadItemRequest> slots,
                                              List<SquadItemRequest> bench) {
        Set<Long> ids = new HashSet<>();
        slots.forEach(item -> addId(ids, item));
        bench.forEach(item -> addId(ids, item));
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, TeamMember> found = new HashMap<>();
        for (TeamMember member : teamMemberRepository.findAllById(ids)) {
            if (member.getTeam().getId().equals(teamId)) {
                found.put(member.getId(), member);
            }
        }
        return found;
    }

    private void addId(Set<Long> ids, SquadItemRequest item) {
        if (item.memberId() != null) {
            ids.add(item.memberId());
        }
    }

    /**
     * 항목 하나. {@code used} 에 이미 있는 memberId 면 중복이다.
     *
     * 중복 오류의 field 가 <b>두 번째로 나온 자리</b>를 가리킨다 (계약서 §4-4 의
     * {@code slots[i].memberId} / {@code bench[i].memberId} 형식). 첫 번째를 가리키면
     * 화면에서 "여긴 제대로 넣었는데 왜"가 된다 — 사용자가 고쳐야 하는 쪽은 나중 것이다.
     */
    private SquadSlot toItem(Squad squad, SquadItemRequest item, int index, boolean bench,
                             Map<Long, TeamMember> members, Set<Long> used, String field) {
        if (item.isEmptySlot()) {
            return bench ? SquadSlot.benched(squad, index, null, null)
                    : SquadSlot.starter(squad, index, null, null);
        }
        Long memberId = item.memberId();
        if (memberId == null) {
            // 게스트 — 이름만 있다. 길이는 빈 검증이 이미 봤다.
            return bench ? SquadSlot.benched(squad, index, null, item.name().strip())
                    : SquadSlot.starter(squad, index, null, item.name().strip());
        }
        TeamMember member = members.get(memberId);
        if (member == null) {
            throw fieldError(field, index, "이 팀의 팀원이 아닙니다");
        }
        if (!used.add(memberId)) {
            throw fieldError(field, index, "같은 팀원을 두 번 넣을 수 없습니다");
        }
        // 이름을 함께 적어 둔다 — 이 팀원이 나중에 명단에서 지워졌을 때 쓸 스냅샷이다.
        // 명단에 남아 있는 동안은 이 값을 쓰지 않고 현재 이름을 보여 준다.
        return bench ? SquadSlot.benched(squad, index, member, member.getName())
                : SquadSlot.starter(squad, index, member, member.getName());
    }

    private BusinessException fieldError(String field, int index, String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                new ErrorResponse.FieldError("%s[%d].memberId".formatted(field, index), message)));
    }
}
