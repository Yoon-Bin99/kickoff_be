package com.kickoff.be.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.weather.service.WeatherArea;
import com.kickoff.be.weather.service.WeatherAreas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 글의 region 문자열을 예보 지점으로 바꾸는 규칙 (계약서 §8-3).
 *
 * <b>여기가 틀리면 조용히 틀린다.</b> 엉뚱한 격자를 집으면 에러 없이 옆 동네 날씨가
 * 나가고, 아무도 못 알아본다. region 은 자유 문자열이라 들어오는 모양이 제각각이다.
 */
class WeatherAreasTest {

    private final WeatherAreas areas = new WeatherAreas();

    @Test
    @DisplayName("시/도 + 시군구를 격자로 바꾼다")
    void findsSigungu() {
        WeatherArea area = areas.find("서울 강서구").orElseThrow();

        assertThat(area.sido()).isEqualTo("서울");
        assertThat(area.sigungu()).isEqualTo("강서구");
        assertThat(area.displayName()).isEqualTo("서울 강서구");
        assertThat(area.isProvinceFallback()).isFalse();
    }

    /** 정식 이름·옛 이름이 그대로 들어온다 — 사용자가 친 문자열이다. */
    @Test
    @DisplayName("긴 시/도 이름과 옛 이름도 알아본다")
    void normalizesProvinceNames() {
        assertThat(areas.find("강원특별자치도 춘천시").orElseThrow().displayName())
                .isEqualTo("강원 춘천시");
        assertThat(areas.find("강원도 춘천시").orElseThrow().displayName())
                .isEqualTo("강원 춘천시");
        assertThat(areas.find("서울특별시 강서구").orElseThrow().displayName())
                .isEqualTo("서울 강서구");
    }

    /** "수원시 장안구"처럼 두 토막짜리가 있다 (계약서 §8-3). */
    @Test
    @DisplayName("두 토막짜리 시군구가 한 토막보다 먼저다")
    void twoWordSigunguWins() {
        WeatherArea area = areas.find("경기 수원시 장안구").orElseThrow();

        assertThat(area.sigungu()).isEqualTo("수원시 장안구");
    }

    /**
     * 구까지 적지 않은 글이 있다. 같은 시의 구들은 격자가 사실상 같은 자리다.
     *
     * <b>그래도 이름은 글에 적힌 수준으로 돌려준다</b> (v1.38.3) — 화면에
     * "성남시 분당구 기준"으로 뜨면 글과 다른 구의 예보처럼 읽힌다.
     */
    @Test
    @DisplayName("구 없이 시만 적으면 격자는 그 시의 줄, 이름은 시까지다")
    void cityWithoutDistrictKeepsItsName() {
        WeatherArea area = areas.find("경기 성남시").orElseThrow();

        assertThat(area.displayName()).isEqualTo("경기 성남시");
        assertThat(area.nx()).isEqualTo(areas.find("경기 성남시 분당구").orElseThrow().nx());
        assertThat(area.midTaRegId())
                .isEqualTo(areas.find("경기 성남시 분당구").orElseThrow().midTaRegId());
    }

    @Test
    @DisplayName("시군구를 못 찾으면 시/도 대표 지점이고 area 는 시/도만이다")
    void fallsBackToProvince() {
        WeatherArea area = areas.find("서울 없는구").orElseThrow();

        assertThat(area.isProvinceFallback()).isTrue();
        assertThat(area.displayName()).isEqualTo("서울");
    }

    @Test
    @DisplayName("시/도를 못 알아보면 비어 있다 — 날씨는 null 이 된다")
    void unknownProvinceIsEmpty() {
        assertThat(areas.find("어딘가 저기")).isEmpty();
        assertThat(areas.find("")).isEmpty();
        assertThat(areas.find(null)).isEmpty();
    }

    /** 2026년에 광주·전남이 한 시/도로 합쳐졌다. 글은 여전히 둘로 적힌다. */
    @Test
    @DisplayName("광주와 전남이 각각 자기 이름으로 나온다")
    void gwangjuAndJeonnamKeepTheirNames() {
        assertThat(areas.find("광주 북구").orElseThrow().displayName()).isEqualTo("광주 북구");
        assertThat(areas.find("전남 여수시").orElseThrow().displayName()).isEqualTo("전남 여수시");
    }

    /**
     * 통합 명칭으로 적혀 온 글도 알아본다 — 자치구면 광주, 시·군이면 전남이다.
     * 가를 수 없으면 전남 대표 지점으로 떨어진다.
     */
    @Test
    @DisplayName("통합 명칭도 시군구 이름으로 광주/전남을 가른다")
    void mergedProvinceNameIsSplitBySigungu() {
        assertThat(areas.find("전남광주통합특별시 북구").orElseThrow().displayName())
                .isEqualTo("광주 북구");
        assertThat(areas.find("전남광주통합특별시 여수시").orElseThrow().displayName())
                .isEqualTo("전남 여수시");
        assertThat(areas.find("전남광주통합특별시").orElseThrow().displayName())
                .isEqualTo("전남");
    }

    /**
     * 중기 코드가 비어 있는 줄은 <b>울릉군 하나뿐</b>이다 (weather-area.csv 머리말).
     * 울릉도·독도는 대구·경북과 다른 예보구역인데 공식 표에 그 광역 구역이 없다.
     */
    @Test
    @DisplayName("중기 코드가 비어 있는 줄은 울릉군뿐이다")
    void onlyUlleungLacksMidTermCodes() {
        assertThat(areas.all())
                .filteredOn(area -> !area.hasMidTerm())
                .extracting(WeatherArea::displayName)
                .containsExactly("경북 울릉군");
    }

    /** 강원만 시군별로 영서/영동이 갈린다 (계약서 §8-3). */
    @Test
    @DisplayName("강원은 영서와 영동이 다른 구역이다")
    void gangwonSplitsIntoTwoZones() {
        assertThat(areas.find("강원 춘천시").orElseThrow().midLandRegId())
                .isEqualTo("11D10000");
        assertThat(areas.find("강원 강릉시").orElseThrow().midLandRegId())
                .isEqualTo("11D20000");
    }

    /**
     * <b>중기 코드는 반쪽일 수 없다.</b> 하나만 있으면 날씨는 있는데 기온이 없거나 그
     * 반대가 되어, 어느 쪽인지 화면에서 알 수 없는 응답이 나간다.
     */
    @Test
    @DisplayName("중기 코드는 둘 다 있거나 둘 다 없다")
    void midTermCodesComeInPairs() {
        assertThat(areas.all()).allSatisfy(area ->
                assertThat(area.midLandRegId() == null)
                        .as("%s", area.displayName())
                        .isEqualTo(area.midTaRegId() == null));
    }

    /** 표가 비면 모든 글의 날씨가 조용히 사라진다 — 그 상태로 뜨지 않게 한다. */
    @Test
    @DisplayName("표에 시/도 17개가 모두 있다")
    void everyProvinceHasFallback() {
        assertThat(areas.all())
                .filteredOn(WeatherArea::isProvinceFallback)
                .extracting(WeatherArea::sido)
                .containsExactlyInAnyOrder("서울", "부산", "대구", "인천", "광주", "대전", "울산",
                        "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");
    }
}
