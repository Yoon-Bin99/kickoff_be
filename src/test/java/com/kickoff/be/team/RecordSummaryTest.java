package com.kickoff.be.team;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.dto.RecordSummary;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 전적 요약이 <b>기록 0건인 팀</b>에서도 안전한지 (계약서 §4-1).
 *
 * 이 테스트가 생긴 이유는 실제로 당한 고장 때문이다. RecordSummary 에 3인자 생성자가 둘
 * 있던 시절, JPQL 의 {@code select new ...} 가 어느 쪽을 쓸지는 Hibernate 가 골랐고 그
 * 선택이 JVM 실행마다 달랐다. 원시형 쪽이 뽑힌 실행에서는 기록 0건 팀의 null 이 그대로
 * 넘어가 팀 페이지와 글 상세가 통째로 500 이 됐다.
 *
 * 무서운 건 <b>코드를 바꾸지 않아도 재시작만으로 뒤집힌다</b>는 점이었다. 같은 커밋에서
 * 전체 테스트가 한 번은 53개 실패, 다음 실행에서는 전부 통과했다. 그래서 통합 테스트로는
 * 잡히지 않고 — 운이 좋으면 통과한다 — 구조 자체를 못 박아야 한다.
 */
class RecordSummaryTest extends IntegrationTestSupport {

    private Team team;

    @BeforeEach
    void setUpTeam() {
        User owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        team = createTeam(owner, "FC 새벽", "서울 강서구");
    }

    @Test
    @DisplayName("생성자가 정확히 하나다 — Hibernate 가 고를 여지를 남기지 않는다")
    void hasExactlyOneConstructor() {
        // 하나 늘어나는 순간 JPQL 의 생성자 선택이 다시 실행 의존이 된다. 컴파일은 되고
        // 테스트도 대부분 통과하므로, 여기서 막지 않으면 배포 뒤 재시작에서 드러난다.
        assertThat(RecordSummary.class.getDeclaredConstructors())
                .as("RecordSummary 의 생성자는 하나여야 한다")
                .hasSize(1);
    }

    @Test
    @DisplayName("기록이 0건이면 0/0/0 — 집계 sum 이 null 로 오는 경로")
    void emptyTeamSummarisesToZero() {
        RecordSummary summary = teamRecordRepository.summaryOf(team.getId());

        assertThat(summary.wins()).isZero();
        assertThat(summary.draws()).isZero();
        assertThat(summary.losses()).isZero();
    }

    @Test
    @DisplayName("null 이 들어와도 0 으로 바뀐다 — 계약이 약속한 값은 0 이지 null 이 아니다")
    void nullsBecomeZero() {
        RecordSummary summary = new RecordSummary(null, null, null);

        assertThat(summary.wins()).isZero();
        assertThat(summary.draws()).isZero();
        assertThat(summary.losses()).isZero();
        assertThat(RecordSummary.EMPTY).isEqualTo(summary);
    }
}
