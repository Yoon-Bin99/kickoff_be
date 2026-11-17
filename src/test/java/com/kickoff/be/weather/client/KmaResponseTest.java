package com.kickoff.be.weather.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.weather.client.dto.MidLandForecast;
import com.kickoff.be.weather.client.dto.MidTemperature;
import com.kickoff.be.weather.client.dto.ShortTermForecast;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 기상청 응답 해석 (계약서 §8-3).
 *
 * <b>이 픽스처는 아직 실제 응답으로 확인하지 않았다.</b> 인증키가 로컬에 없어 실호출을
 * 하지 못했고, 활용가이드 첨부는 로그인해야 받을 수 있다. 항목 이름(category·fcstDate·
 * fcstValue, wf3Am·rnSt3Am, taMin3·taMax3)은 공공데이터포털 서비스 설명과 응답 구조에
 * 맞춰 적은 것이라, <b>키가 들어오면 실제 응답으로 한 번 대조해야 한다</b>.
 *
 * 그래도 테스트를 먼저 두는 이유는, 항목 이름이 하나 바뀌면 에러 없이 빈 예보가 되는
 * 자리이기 때문이다 — 지금 모양을 못박아 둬야 바뀐 것을 알아챌 수 있다.
 */
class KmaResponseTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final OffsetDateTime BASE =
            OffsetDateTime.of(2026, 11, 17, 17, 0, 0, 0, KST);

    @Test
    @DisplayName("단기 — 항목 네 개가 한 칸으로 모인다")
    void shortTermItemsCollapseIntoSlots() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(slot("20261118", "0700", "TMP", "3"));
        items.add(slot("20261118", "0700", "SKY", "1"));
        items.add(slot("20261118", "0700", "PTY", "0"));
        items.add(slot("20261118", "0700", "POP", "20"));
        // 쓰지 않는 항목도 섞여 온다 (습도·풍속 등) — 조용히 지나가야 한다.
        items.add(slot("20261118", "0700", "REH", "55"));

        ShortTermForecast forecast = KmaHttpClient.parseShortTerm(items, BASE).orElseThrow();

        assertThat(forecast.announcedAt()).isEqualTo(BASE);
        assertThat(forecast.slots()).hasSize(1);
        assertThat(forecast.slots().get(LocalDateTime.of(2026, 11, 18, 7, 0)))
                .satisfies(slot -> {
                    assertThat(slot.temperature()).isEqualTo(3);
                    assertThat(slot.sky()).isEqualTo(1);
                    assertThat(slot.pty()).isZero();
                    assertThat(slot.precipitationProbability()).isEqualTo(20);
                });
    }

    /** 강수량은 "강수없음"처럼 숫자가 아닌 값으로 온다. 그 줄 때문에 터지면 안 된다. */
    @Test
    @DisplayName("단기 — 숫자가 아닌 값은 그 줄만 버린다")
    void shortTermIgnoresNonNumericValues() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(slot("20261118", "0700", "PCP", "강수없음"));
        items.add(slot("20261118", "0700", "TMP", "3"));

        ShortTermForecast forecast = KmaHttpClient.parseShortTerm(items, BASE).orElseThrow();

        assertThat(forecast.slots().get(LocalDateTime.of(2026, 11, 18, 7, 0)).temperature())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("단기 — 줄이 없으면 보관하지 않는다")
    void shortTermEmpty() {
        assertThat(KmaHttpClient.parseShortTerm(List.of(), BASE)).isEmpty();
        assertThat(KmaHttpClient.parseShortTerm(null, BASE)).isEmpty();
    }

    /** 3~7일은 오전·오후가 따로, 8~10일은 하루 하나다 (계약서 §8-3). */
    @Test
    @DisplayName("중기육상 — 날짜별로 풀고 8일 뒤부터는 하루 하나다")
    void midLandSplitsByDay() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("regId", "11B00000");
        item.put("wf3Am", "맑음");
        item.put("wf3Pm", "구름많음");
        item.put("rnSt3Am", "10");
        item.put("rnSt3Pm", "20");
        item.put("wf8", "흐리고 비");
        item.put("rnSt8", "70");

        MidLandForecast forecast =
                KmaHttpClient.parseMidLand(List.of(item), BASE).orElseThrow();

        assertThat(forecast.announcedAt()).isEqualTo(BASE);
        assertThat(forecast.days().get(LocalDate.of(2026, 11, 20))).satisfies(day -> {
            assertThat(day.amText()).isEqualTo("맑음");
            assertThat(day.pmText()).isEqualTo("구름많음");
            assertThat(day.amPop()).isEqualTo(10);
            assertThat(day.pmPop()).isEqualTo(20);
        });
        assertThat(forecast.days().get(LocalDate.of(2026, 11, 25))).satisfies(day -> {
            assertThat(day.amText()).as("하루짜리는 오전·오후가 같다").isEqualTo("흐리고 비");
            assertThat(day.pmText()).isEqualTo("흐리고 비");
            assertThat(day.pmPop()).isEqualTo(70);
        });
    }

    @Test
    @DisplayName("중기기온 — 최저·최고를 날짜별로 푼다")
    void midTemperature() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("regId", "11B10101");
        item.put("taMin3", "2");
        item.put("taMax3", "9");
        item.put("taMin10", "-3");

        MidTemperature forecast =
                KmaHttpClient.parseMidTemperature(List.of(item), BASE).orElseThrow();

        assertThat(forecast.days().get(LocalDate.of(2026, 11, 20)))
                .satisfies(day -> {
                    assertThat(day.min()).isEqualTo(2);
                    assertThat(day.max()).isEqualTo(9);
                });
        assertThat(forecast.days().get(LocalDate.of(2026, 11, 27)))
                .as("한쪽만 와도 온 것만 채운다")
                .satisfies(day -> {
                    assertThat(day.min()).isEqualTo(-3);
                    assertThat(day.max()).isNull();
                });
    }

    /** 중기예보 API 는 개편이 예고돼 있다 (계약서 §8-3). 형식이 바뀌면 빈 값이어야 한다. */
    @Test
    @DisplayName("중기 — 아는 항목이 하나도 없으면 보관하지 않는다")
    void midTermWithUnknownShape() {
        Map<String, Object> item = Map.of("regId", "11B00000", "somethingNew", "맑음");

        assertThat(KmaHttpClient.parseMidLand(List.of(item), BASE)).isEmpty();
        assertThat(KmaHttpClient.parseMidTemperature(List.of(item), BASE)).isEmpty();
    }

    private static Map<String, Object> slot(String date, String time,
                                            String category, String value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("baseDate", "20261117");
        item.put("baseTime", "1700");
        item.put("category", category);
        item.put("fcstDate", date);
        item.put("fcstTime", time);
        item.put("fcstValue", value);
        item.put("nx", "58");
        item.put("ny", "126");
        return item;
    }
}
