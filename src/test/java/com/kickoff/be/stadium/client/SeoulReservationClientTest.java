package com.kickoff.be.stadium.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 서울 공공서비스예약 응답 파싱 (계약서 §8-1, v1.27.0).
 *
 * <b>이 테스트가 증명하는 것과 증명하지 못하는 것을 구분해 둔다.</b>
 *
 * 필드 이름(SVCID·SVCNM·AREANM·SVCSTATNM·SVCURL·MINCLASSNM·SVCOPNBGNDT·SVCOPNENDDT)과
 * 값의 생김새(AREANM 이 "성동구"처럼 구 이름만 온다, 날짜에 " 00:00:00.0" 이 붙는다,
 * 빈 값이 null 이 아니라 빈 문자열로 온다)는 <b>실제 응답에서 확인한 것</b>이다 —
 * {@code openapi.seoul.go.kr:8088} 의 {@code ListPublicReservationSport} 를 샘플키로 불러
 * 받은 응답이다. 응답에 <b>주소 필드가 없다</b>는 것도 그 응답으로 확인했다.
 *
 * 다만 픽스처의 각 행은 그 응답을 그대로 붙인 것이 아니라, <b>같은 형식에 맞춰 손으로
 * 쓴 것</b>이다(샘플키가 테니스장만 돌려줘서 축구장·풋살장 행이 없었다). 그래서 이 테스트는
 * "우리 파서가 이 형식을 제대로 읽는다"를 증명하지, "서울이 정말 이 값을 준다"를 증명하지는
 * 않는다. 인증키가 생기면 실제 축구장 응답 한 장을 받아 이 픽스처를 갈아 끼울 것.
 */
class SeoulReservationClientTest {

    @Test
    @DisplayName("축구장·풋살장만 남기고 나머지 소분류는 버린다")
    void keepsOnlySoccerAndFutsal() throws IOException {
        List<SeoulReservationRow> rows = SeoulReservationClient.parse(fixture());

        assertThat(rows).extracting(SeoulReservationRow::minClassName)
                .containsExactly("축구장", "풋살장", "축구장");
        // 테니스장은 걸러졌다 — 같은 API 가 체육시설 전체를 주므로 여기서 안 거르면
        // 구장 목록에 테니스장이 섞인다.
        assertThat(rows).extracting(SeoulReservationRow::svcId)
                .doesNotContain("S260101000000000004");
    }

    @Test
    @DisplayName("필드를 계약서가 쓰는 자리로 옮긴다")
    void mapsFields() throws IOException {
        SeoulReservationRow row = SeoulReservationClient.parse(fixture()).get(0);

        assertThat(row.svcId()).isEqualTo("S260101000000000001");
        assertThat(row.svcName()).isEqualTo("월곡 인조잔디구장(평일)-2026년");
        // AREANM 은 구 이름만 온다. "서울"을 붙이는 것은 파서가 아니라 StadiumFields 몫이다.
        assertThat(row.areaName()).isEqualTo("성북구");
        assertThat(row.statusName()).isEqualTo("접수중");
        assertThat(row.url()).endsWith("rsv_svc_id=S260101000000000001");
        assertThat(row.openBegin()).isEqualTo("2026-09-01 00:00:00.0");
    }

    @Test
    @DisplayName("빈 문자열은 null 로 본다 — 공공 API 는 없는 값을 빈 문자열로 준다")
    void blankBecomesNull() throws IOException {
        SeoulReservationRow row = SeoulReservationClient.parse(fixture()).get(2);

        assertThat(row.url()).isNull();
        assertThat(row.openBegin()).isNull();
        assertThat(row.openEnd()).isNull();
    }

    /**
     * 인증키가 틀리면 {@code /json/} 으로 불러도 XML 이 온다 — 실제로 확인한 동작이다.
     * 여기서 조용히 빈 목록을 돌려주면 동기화가 "0건 성공"으로 보이고, 그다음 단계가
     * 멀쩡한 데이터를 지운다. 반드시 예외여야 한다.
     */
    @Test
    @DisplayName("인증키 오류 XML 응답은 예외다 (빈 목록이 아니다)")
    void xmlErrorBodyThrows() {
        String xml = "<RESULT><CODE>INFO-100</CODE>"
                + "<MESSAGE><![CDATA[인증키가 유효하지 않습니다.]]></MESSAGE></RESULT>";

        assertThatThrownBy(() -> SeoulReservationClient.parse(xml))
                .isInstanceOf(SeoulReservationException.class);
    }

    @Test
    @DisplayName("데이터 없음(INFO-200)도 예외다 — 성공한 0건과 구별해야 한다")
    void noDataResultThrows() {
        String body = "{\"RESULT\":{\"CODE\":\"INFO-200\",\"MESSAGE\":\"해당하는 데이터가 없습니다.\"}}";

        assertThatThrownBy(() -> SeoulReservationClient.parse(body))
                .isInstanceOf(SeoulReservationException.class);
    }

    @Test
    @DisplayName("빈 응답은 예외다")
    void emptyBodyThrows() {
        assertThatThrownBy(() -> SeoulReservationClient.parse(""))
                .isInstanceOf(SeoulReservationException.class);
        assertThatThrownBy(() -> SeoulReservationClient.parse(null))
                .isInstanceOf(SeoulReservationException.class);
    }

    private String fixture() throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("fixtures/seoul-reservation-sport.json")) {
            assertThat(in).as("픽스처가 있어야 한다").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
