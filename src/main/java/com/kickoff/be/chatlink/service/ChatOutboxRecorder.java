package com.kickoff.be.chatlink.service;

import com.kickoff.be.chatlink.entity.ChatOutbox;
import com.kickoff.be.chatlink.repository.ChatOutboxRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 서버에 알릴 일을 chat_outbox 에 적는다 (계약서 §10).
 *
 * <b>부르는 쪽의 트랜잭션 안에서 돈다</b>({@code MANDATORY}). 이게 이 구조의 전부다 —
 * 취소·탈퇴가 커밋되면 기록도 함께 커밋되고, 롤백되면 기록도 사라진다. 그래서 "취소는
 * 됐는데 채팅 서버는 모르는" 상태도, "채팅 서버만 아는" 상태도 생기지 않는다.
 *
 * {@code MANDATORY} 로 못박은 이유: 트랜잭션 없이 불리면 그 보장이 조용히 사라진다.
 * 새 호출 지점이 생겼을 때 기동이 아니라 <b>그 호출에서</b> 바로 드러나는 편이 낫다.
 *
 * <b>전달 실패는 여기까지 오지 않는다.</b> 보내는 일은 스케줄러가 나중에 따로 하므로,
 * 채팅 서버가 꺼져 있어도 원 동작은 성공한다. 계약서 §10 이 요구한 방향이다.
 */
@Service
@RequiredArgsConstructor
public class ChatOutboxRecorder {

    private final ChatOutboxRepository repository;

    /**
     * 방의 전제가 바뀌었다 (계약서 §10 ROOM_INVALIDATED).
     *
     * 매칭 취소(§6-2)와 수락된 신청이 있는 글의 matchAt 수정이 여기로 온다. 둘 다 방
     * 하나짜리라 쪼갤 일이 없지만, 목록을 받는 모양으로 둔 건 계약서의 칸이
     * {@code requestIds} 복수이기 때문이다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void roomInvalidated(Collection<Long> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (List<Long> chunk : chunks(requestIds)) {
            repository.save(ChatOutbox.roomInvalidated(chunk, now));
        }
    }

    /**
     * 방이 사라졌다 (계약서 §10 PURGE). 회원 탈퇴로 팀이 삭제되는 경로다 (§3-4).
     *
     * <b>신청이 없어도 적는다.</b> 그 팀의 나가기 기록은 신청과 따로 남아 있을 수 있어서,
     * teamIds 만 담긴 PURGE 가 할 일이 있다 — BE 의 탈퇴 처리도 같은 이유로
     * {@code deleteChatLeaves} 를 신청 없는 경우에 따로 부른다.
     *
     * 신청 id 가 많으면 여러 행으로 쪼갠다. 한 행이 넘쳐 insert 가 실패하면 <b>탈퇴가
     * 통째로 롤백된다</b> — 채팅 뒷정리 때문에 사용자가 탈퇴를 못 하는 일은 없어야 한다.
     * {@code teamIds} 는 첫 행에만 싣는다. 모든 행에 반복해도 결과는 같지만(멱등이다),
     * 같은 일을 n 번 시킬 이유가 없다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(Collection<Long> requestIds, Collection<Long> teamIds) {
        List<Long> teams = teamIds == null ? List.of() : List.copyOf(teamIds);
        List<List<Long>> chunks = chunks(requestIds);
        if (chunks.isEmpty() && teams.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (chunks.isEmpty()) {
            repository.save(ChatOutbox.purge(List.of(), teams, now));
            return;
        }
        for (int index = 0; index < chunks.size(); index++) {
            repository.save(ChatOutbox.purge(chunks.get(index),
                    index == 0 ? teams : List.of(), now));
        }
    }

    private static List<List<Long>> chunks(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> all = List.copyOf(ids);
        List<List<Long>> chunks = new ArrayList<>();
        for (int start = 0; start < all.size(); start += ChatOutbox.MAX_IDS_PER_ROW) {
            chunks.add(all.subList(start, Math.min(start + ChatOutbox.MAX_IDS_PER_ROW, all.size())));
        }
        return chunks;
    }
}
