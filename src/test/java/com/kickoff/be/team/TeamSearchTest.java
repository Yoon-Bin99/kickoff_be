package com.kickoff.be.team;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 팀 검색 — GET /api/teams (계약서 §4, v1.14.0).
 *
 * 검색은 <b>틀려도 에러가 안 나는</b> 기능이다. 못 찾으면 빈 목록이 오고, 순서가 엉키면
 * 그냥 다른 순서로 나온다. 그래서 "검색해 봤더니 나오더라"로는 검증이 안 되고, 정규화가
 * 필요한 조합과 순서 규칙을 하나씩 못박아야 한다.
 *
 * 특히 정렬은 사람이 눈으로 보면 늘 그럴듯해 보인다 — "마포"로 찾았는데 "서울마포클럽"이
 * "마포 유나이티드"보다 위에 있어도 목록은 멀쩡해 보인다. 계약이 전방일치를 먼저 두라고
 * 정한 이유가 그거라, 그룹 경계를 실제 데이터로 확인한다.
 */
class TeamSearchTest extends IntegrationTestSupport {

    // ── 정규화 매칭 (v1.14.0)

    @Test
    @DisplayName("띄어쓰기가 달라도 찾힌다 — 양쪽에서 공백을 지우고 맞춘다")
    void whitespaceIsIgnored() throws Exception {
        team("인천 스트라이커즈", "인천 남동구");

        // 붙여 쓴 검색어로 띄어 쓴 팀을 찾는다
        search("인천스트라이커즈")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("인천 스트라이커즈"));

        // 반대 방향도 된다 — 검색어에 없던 공백이 팀 이름에 있는 경우
        search("인천 스트라이커즈").andExpect(jsonPath("$.content", hasSize(1)));
        // 가운데 공백이 여러 개여도, 앞뒤가 지저분해도 같다
        search("  인천   스트라이커즈 ").andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("대소문자를 무시한다 — 영문 팀명에서 실제로 갈리는 지점")
    void caseIsIgnored() throws Exception {
        team("FC서울", "서울 중구");

        search("fc서울").andExpect(jsonPath("$.content", hasSize(1)));
        search("FC서울").andExpect(jsonPath("$.content", hasSize(1)));
        search("Fc서울").andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("공백과 대소문자가 한꺼번에 달라도 찾힌다")
    void whitespaceAndCaseTogether() throws Exception {
        team("FC 새벽 united", "서울 강서구");

        search("fc새벽UNITED").andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("없는 이름은 빈 목록 — 오타 유사도는 범위 밖이다 (§9)")
    void noMatchIsEmpty() throws Exception {
        team("마포 유나이티드", "서울 마포구");

        // "마프"는 한 글자 차이지만 편집거리 검색은 계약 범위 밖이라 안 찾히는 게 맞다
        search("마프").andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ── 정확도 정렬 (v1.14.0)

    @Test
    @DisplayName("전방일치가 포함보다 먼저 — 그룹 안에서는 생성일 DESC")
    void prefixMatchesComeFirst() throws Exception {
        team("마포 유나이티드", "서울 마포구");   // 전방일치, 가장 오래됨
        team("서울 마포 클럽", "서울 마포구");   // 포함
        team("마포FC", "서울 마포구");           // 전방일치, 가장 최근

        // 전방일치 둘이 먼저(그 안에서 최근 순), 그다음 포함.
        // 정렬이 빠지면 생성일 DESC 만 남아 서울 마포 클럽이 가운데로 온다
        search("마포")
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[*].name",
                        contains("마포FC", "마포 유나이티드", "서울 마포 클럽")));
    }

    @Test
    @DisplayName("전방일치 판정도 정규화 뒤에 한다 — 팀 이름 앞의 공백에 걸리지 않는다")
    void prefixIsJudgedAfterNormalising() throws Exception {
        // 전방일치인 쪽을 일부러 <b>먼저</b> 만든다. 반대로 두면 생성일 DESC 만으로도
        // 기대한 순서가 나와서, 그룹 정렬이 통째로 빠져도 이 테스트가 통과한다
        team("마 포 클 럽", "서울 마포구");
        team("서울 마포 클럽", "서울 마포구");

        // "마 포 클 럽"은 정규화하면 "마포클럽"이라 전방일치다. 정규화 전 문자열로
        // 판정하면 "마 포..."는 "마포"로 시작하지 않아 뒤로 밀린다
        search("마포")
                .andExpect(jsonPath("$.content[*].name",
                        contains("마 포 클 럽", "서울 마포 클럽")));
    }

    @Test
    @DisplayName("keyword 가 없으면 예전 그대로 — 생성일 DESC")
    void withoutKeywordOrderIsUnchanged() throws Exception {
        team("첫 번째 팀", "서울 강서구");
        team("두 번째 팀", "서울 마포구");
        team("세 번째 팀", "서울 송파구");

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name",
                        contains("세 번째 팀", "두 번째 팀", "첫 번째 팀")));
    }

    // ── 기존 동작 회귀 (v1.11.0)

    @Test
    @DisplayName("region 필터와 빈 keyword 는 예전 그대로다")
    void regionFilterStillWorks() throws Exception {
        team("마포 유나이티드", "서울 마포구");
        team("고양 새벽", "경기 고양시");

        search("").andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/teams").param("region", "서울"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("마포 유나이티드"));

        // keyword 와 region 은 AND 다
        mockMvc.perform(get("/api/teams").param("keyword", "마포").param("region", "경기"))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("검색에도 페이징이 그대로 걸린다 — 정렬이 페이지 경계를 넘어 유지된다")
    void pagingStillApplies() throws Exception {
        team("마포 유나이티드", "서울 마포구");
        team("서울 마포 클럽", "서울 마포구");
        team("마포FC", "서울 마포구");

        // 첫 페이지는 전방일치 중 최근 것 하나
        mockMvc.perform(get("/api/teams").param("keyword", "마포")
                        .param("page", "0").param("size", "1"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].name").value("마포FC"));
        // 마지막 페이지는 포함 그룹 — 그룹 순서가 페이지를 넘어서도 유지돼야 한다
        mockMvc.perform(get("/api/teams").param("keyword", "마포")
                        .param("page", "2").param("size", "1"))
                .andExpect(jsonPath("$.content[0].name").value("서울 마포 클럽"));
    }

    // ── 헬퍼

    private int seq;

    private Team team(String name, String region) {
        seq++;
        return createTeam(createUser("owner" + seq + "@example.com", "주장" + seq,
                String.format("010-7777-%04d", seq)), name, region);
    }

    private ResultActions search(String keyword) throws Exception {
        return mockMvc.perform(get("/api/teams").param("keyword", keyword));
    }
}
