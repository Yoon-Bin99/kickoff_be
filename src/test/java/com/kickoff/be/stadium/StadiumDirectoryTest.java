package com.kickoff.be.stadium;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.nullValue;

import com.kickoff.be.stadium.entity.Stadium;
import com.kickoff.be.stadium.entity.StadiumSource;
import com.kickoff.be.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 구장 목록 (계약서 §8-1, v1.27.0).
 */
class StadiumDirectoryTest extends IntegrationTestSupport {

    @Test
    @DisplayName("인증 없이 조회된다 — 가입 전에도 구장을 본다")
    void isPublic() throws Exception {
        seoul("월곡 인조잔디구장", "서울 성북구");

        mockMvc.perform(get("/api/stadiums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].stadiumId").isNumber())
                .andExpect(jsonPath("$.content[0].name").value("월곡 인조잔디구장"));
    }

    @Test
    @DisplayName("응답이 계약서 필드를 그대로 낸다 — SEOUL_PUBLIC 만 접수 상태·이용 기간을 갖는다")
    void responseShape() throws Exception {
        seoul("월곡 인조잔디구장", "서울 성북구");
        manual("탄천변축구장A", "경기 성남시");

        mockMvc.perform(get("/api/stadiums").param("region", "경기"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].source").value("MANUAL"))
                .andExpect(jsonPath("$.content[0].reservationUrl").exists())
                .andExpect(jsonPath("$.content[0].acceptStatus").value(nullValue()))
                // 키는 남고 값만 null 이다 — 계약서 §2 가 null 필드도 키를 유지하길 요구한다
                // (spring.jackson.default-property-inclusion: always).
                .andExpect(jsonPath("$.content[0].usePeriod").value(nullValue()));

        mockMvc.perform(get("/api/stadiums").param("region", "서울"))
                .andExpect(jsonPath("$.content[0].source").value("SEOUL_PUBLIC"))
                .andExpect(jsonPath("$.content[0].acceptStatus").value("접수중"))
                .andExpect(jsonPath("$.content[0].usePeriod").value("2026-09-01 ~ 2026-12-31"));
    }

    /**
     * 시 단위 프리픽스 매칭 (계약서 §8-1) — "서울"이면 서울 전체가 나와야 한다.
     *
     * 함께 확인하는 것: 프리픽스라서 <b>"중구"로는 "서울 중구"가 안 나온다.</b> 부분 일치로
     * 만들면 "중구"가 서울과 인천의 중구를 한꺼번에 끌어와, 시로 좁히려는 사용자를 방해한다.
     */
    @Test
    @DisplayName("지역은 앞에서부터 맞춘다 — \"서울\"이면 서울 전체")
    void regionIsPrefixMatch() throws Exception {
        seoul("월곡 인조잔디구장", "서울 성북구");
        seoul("남산 축구장", "서울 중구");
        manual("인천 중구 풋살장", "인천 중구");

        mockMvc.perform(get("/api/stadiums").param("region", "서울"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/stadiums").param("region", "서울 중구"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("남산 축구장"));

        mockMvc.perform(get("/api/stadiums").param("region", "중구"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("정렬은 지역 오름차순 → 이름 오름차순")
    void sortsByRegionThenName() throws Exception {
        seoul("하늘 축구장", "서울 강서구");
        seoul("가람 축구장", "서울 강서구");
        seoul("월곡 인조잔디구장", "서울 성북구");

        mockMvc.perform(get("/api/stadiums"))
                .andExpect(jsonPath("$.content[0].name").value("가람 축구장"))
                .andExpect(jsonPath("$.content[1].name").value("하늘 축구장"))
                .andExpect(jsonPath("$.content[2].name").value("월곡 인조잔디구장"));
    }

    /**
     * 서버 콜레이션과 무관하게 같은 순서여야 한다 (계약서 §8-1).
     *
     * <b>v1.27.0 배포에서 실제로 깨졌던 자리다.</b> 운영(glibc en_US.utf8)이 이 다섯 행을
     * 4,2,1,3,5 로 돌려줬다 — 인천 서구가 맨 앞, 인천 연수구가 맨 뒤라 경기 구장 셋을
     * 사이에 두고 갈라졌다. 그럴 수 없어 보이지만 glibc 에서는 실제로
     * {@code '인천 서구' < '경기 성남시' < '인천 연수구'} 다. 첫 글자가 순서를 결정하지
     * 않는다 ({@code SortKeyFunctionContributor} 에 전말을 적었다).
     *
     * 실제 운영 시드(V20) 그대로 쓴다 — 이 값들이어야 문제가 드러난다.
     * 임의의 한글로 바꾸면 우연히 통과해 테스트가 조용히 쓸모없어진다.
     */
    @Test
    @DisplayName("서버 콜레이션과 무관하게 정렬된다 — v1.27.0 회귀")
    void sortsIndependentlyOfServerCollation() throws Exception {
        manual("인조잔디구장(성남종합운동장)", "경기 성남시");
        manual("황송공원인조잔디구장", "경기 성남시");
        manual("탄천변축구장A", "경기 성남시");
        manual("공촌유수지 체육시설 축구장", "인천 서구");
        manual("연수체육공원 풋살장A", "인천 연수구");

        mockMvc.perform(get("/api/stadiums").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("인조잔디구장(성남종합운동장)"))
                .andExpect(jsonPath("$.content[1].name").value("탄천변축구장A"))
                .andExpect(jsonPath("$.content[2].name").value("황송공원인조잔디구장"))
                .andExpect(jsonPath("$.content[3].name").value("공촌유수지 체육시설 축구장"))
                .andExpect(jsonPath("$.content[4].name").value("연수체육공원 풋살장A"));
    }

    @Test
    @DisplayName("이름 부분 일치로 찾는다")
    void keywordMatchesName() throws Exception {
        seoul("월곡 인조잔디구장", "서울 성북구");
        seoul("남산 축구장", "서울 중구");

        mockMvc.perform(get("/api/stadiums").param("keyword", "인조잔디"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("월곡 인조잔디구장"));
    }

    /**
     * LIKE 와일드카드는 리터럴이다 (계약서 §5 와 같은 규칙, {@code LikeEscape}).
     *
     * 이게 빠지면 에러가 아니라 <b>결과가 너무 많이</b> 나온다 — "%" 하나로 전체 목록이
     * 나가고, 검색창에 "%" 를 친 사람은 자기가 본 게 검색 결과라고 믿는다.
     */
    @Test
    @DisplayName("검색어의 %·_ 는 와일드카드가 아니라 글자다")
    void keywordEscapesWildcards() throws Exception {
        seoul("월곡 인조잔디구장", "서울 성북구");
        seoul("50% 할인구장", "서울 중구");

        mockMvc.perform(get("/api/stadiums").param("keyword", "%"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("50% 할인구장"));

        mockMvc.perform(get("/api/stadiums").param("keyword", "_"))
                .andExpect(jsonPath("$.totalElements").value(0));

        // 이스케이프 문자 자신도 글자로 다뤄야 한다 — 먼저 처리하지 않으면 패턴이 망가진다.
        mockMvc.perform(get("/api/stadiums").param("keyword", "!"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** 지역 필터에도 이스케이프가 걸려야 한다 — 프리픽스라 티가 덜 날 뿐이다. */
    @Test
    @DisplayName("지역 필터의 %도 와일드카드가 아니다")
    void regionEscapesWildcards() throws Exception {
        seoul("월곡 인조잔디구장", "서울 성북구");

        mockMvc.perform(get("/api/stadiums").param("region", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("빈 문자열은 필터를 안 건 것과 같다")
    void blankFilterIsNoFilter() throws Exception {
        seoul("월곡 인조잔디구장", "서울 성북구");
        manual("탄천변축구장A", "경기 성남시");

        mockMvc.perform(get("/api/stadiums").param("region", "").param("keyword", ""))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("페이징 — 기본 20, 최대 50")
    void paging() throws Exception {
        for (int i = 0; i < 3; i++) {
            seoul("구장" + i, "서울 강서구");
        }

        mockMvc.perform(get("/api/stadiums").param("page", "0").param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/stadiums").param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));

        mockMvc.perform(get("/api/stadiums"))
                .andExpect(jsonPath("$.size").value(20));

        // 50 을 넘겨 달라고 해도 50 까지만. 안 막으면 size=100000 한 방으로 전체를 긁어 간다.
        mockMvc.perform(get("/api/stadiums").param("size", "500"))
                .andExpect(jsonPath("$.size").value(50));
    }

    private Stadium seoul(String name, String region) {
        return stadiumRepository.save(Stadium.builder()
                .externalId("SVC-" + name)
                .name(name)
                .region(region)
                .reservationUrl("https://yeyak.seoul.go.kr/web/reservation/selectReservView.do"
                        + "?rsv_svc_id=SVC-" + name)
                .source(StadiumSource.SEOUL_PUBLIC)
                .acceptStatus("접수중")
                .usePeriod("2026-09-01 ~ 2026-12-31")
                .build());
    }

    private Stadium manual(String name, String region) {
        return stadiumRepository.save(Stadium.builder()
                .name(name)
                .region(region)
                .address(region + " 어딘가")
                .reservationUrl("https://res.isdc.co.kr/facilityList.do?facType=28")
                .source(StadiumSource.MANUAL)
                .build());
    }
}
