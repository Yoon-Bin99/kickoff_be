package com.kickoff.be.stadium.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서울 공공서비스예약 체육시설 API 어댑터 (계약서 §8-1, v1.27.0).
 *
 * <b>실패하면 예외를 던진다. 빈 목록을 돌려주지 않는다.</b> 이 구분이 이 클래스의 핵심이다 —
 * 호출자가 "0건"과 "못 가져왔다"를 구별할 수 있어야 마지막 성공 데이터를 지킬 수 있다.
 * 빈 목록으로 뭉개면 공공 API 가 잠깐 죽은 날 구장 목록이 통째로 비고, 그게 정상 동작처럼
 * 보여서 아무도 모른다.
 *
 * <b>이 API 는 HTTPS 를 받지 않는다</b> — {@code openapi.seoul.go.kr:8088} 은 평문 HTTP 뿐이고
 * 인증키가 URL 경로에 실린다. 우리가 고를 수 있는 게 아니라 적어 둔다. 키가 새도 잃는 것은
 * 공개 데이터 조회 쿼터뿐이라 감수한다.
 *
 * <b>에러는 JSON 이 아니라 XML 로 온다.</b> {@code /json/} 으로 불러도 인증키가 틀리면
 * {@code <RESULT><CODE>INFO-100</CODE>...} 가 돌아온다. 그래서 파싱 실패를 그냥 넘기지 않고
 * 본문 앞부분을 로그에 남긴다 — 안 그러면 "키가 틀렸다"가 "응답이 이상하다"로만 보인다.
 */
@Slf4j
@Component
public class SeoulReservationClient {

    /** 열린데이터광장 공통 엔드포인트. HTTPS 는 열려 있지 않다(연결 자체가 안 된다). */
    private static final String BASE_URL = "http://openapi.seoul.go.kr:8088";

    private static final String SERVICE = "ListPublicReservationSport";

    /** 서울 API 가 한 번에 허용하는 최대 건수. */
    private static final int PAGE_SIZE = 1000;

    /**
     * 페이지 상한. 체육시설 전체가 700건 남짓이라 한 장이면 끝나지만, 상대 응답이 이상해져
     * {@code list_total_count} 가 터무니없이 커지는 날 무한히 긁지 않도록 막아 둔다.
     */
    private static final int MAX_PAGES = 10;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final SeoulOpenDataProperties properties;
    private final RestClient restClient;

    public SeoulReservationClient(SeoulOpenDataProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * 체육시설 예약 서비스를 전부 가져온다.
     *
     * 소분류 필터를 <b>서버가 아니라 여기서</b> 건다. 서울 API 의 필터는 경로에 위치로 넣는
     * 방식이라 자리 하나만 어긋나도 조용히 0건이 되는데, 전체가 700건 남짓이라 다 받아서
     * 걸러도 한 번의 호출이다. 확실한 쪽을 택한다.
     *
     * @throws SeoulReservationException 네트워크·응답 형식·인증 등 어떤 이유로든 완전히
     *                                   받아 오지 못했을 때
     */
    public List<SeoulReservationRow> fetchAll() {
        if (!properties.isConfigured()) {
            throw new SeoulReservationException("SEOUL_OPENDATA_KEY 가 없다");
        }
        List<SeoulReservationRow> all = new ArrayList<>();
        int start = 1;
        for (int page = 0; page < MAX_PAGES; page++) {
            List<SeoulReservationRow> fetched = fetchPage(start, start + PAGE_SIZE - 1);
            all.addAll(fetched);
            // 한 장을 다 못 채웠으면 마지막 장이다. total 을 따로 믿지 않는 이유는, total 과
            // 실제 행 수가 어긋나는 날 루프가 끝나지 않기 때문이다.
            if (fetched.size() < PAGE_SIZE) {
                break;
            }
            start += PAGE_SIZE;
        }
        return all;
    }

    private List<SeoulReservationRow> fetchPage(int start, int end) {
        String body;
        try {
            // 키가 경로에 들어가므로 URI 변수로 넘긴다. 문자열로 이어 붙이면 RestClient 가
            // 템플릿으로 보고 한 번 더 인코딩한다 (카카오 장소 검색에서 겪은 것과 같은 함정).
            body = restClient.get()
                    .uri(BASE_URL + "/{key}/json/{service}/{start}/{end}/",
                            properties.key(), SERVICE, start, end)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new SeoulReservationException("서울 공공서비스예약 API 호출 실패 — " + e.getMessage());
        }
        return parse(body);
    }

    /**
     * 응답 한 장을 행 목록으로 옮긴다.
     *
     * 소분류가 <b>축구장·풋살장인 것만</b> 남긴다(계약서 §8-1). 정확히 일치가 아니라 포함으로
     * 보는 이유는, 공공 API 의 소분류 문구가 언제 "인조잔디축구장" 같은 형태로 바뀔지 우리가
     * 정할 수 없어서다 — 넓게 잡아 테니스장이 섞이는 것보다 좁게 잡아 축구장이 통째로
     * 사라지는 쪽이 훨씬 늦게 발견된다.
     */
    @SuppressWarnings("unchecked")
    static List<SeoulReservationRow> parse(String body) {
        if (body == null || body.isBlank()) {
            throw new SeoulReservationException("응답이 비어 있다");
        }
        Map<String, Object> root;
        try {
            root = MAPPER.readValue(body, Map.class);
        } catch (RuntimeException e) {
            // 인증키 오류 등은 JSON 이 아니라 XML 로 온다. 앞부분을 남겨야 원인이 보인다.
            throw new SeoulReservationException("응답을 JSON 으로 읽지 못했다 — " + head(body));
        }
        if (!(root.get(SERVICE) instanceof Map<?, ?> service)) {
            // {"RESULT":{"CODE":"INFO-200",...}} 처럼 서비스 키 없이 결과만 오는 경우다.
            throw new SeoulReservationException("응답에 " + SERVICE + " 가 없다 — " + head(body));
        }
        requireOk((Map<String, Object>) service);
        if (!(service.get("row") instanceof List<?> rows)) {
            throw new SeoulReservationException("응답에 row 가 없다 — " + head(body));
        }
        return rows.stream()
                .filter(Map.class::isInstance)
                .map(row -> toRow((Map<String, Object>) row))
                .filter(SeoulReservationClient::isSoccer)
                .toList();
    }

    private static void requireOk(Map<String, Object> service) {
        if (!(service.get("RESULT") instanceof Map<?, ?> result)) {
            return;
        }
        Object code = result.get("CODE");
        if (code != null && !"INFO-000".equals(code)) {
            throw new SeoulReservationException(
                    "서울 API 가 " + code + " 를 돌려줬다 — " + result.get("MESSAGE"));
        }
    }

    private static boolean isSoccer(SeoulReservationRow row) {
        String min = row.minClassName();
        return min != null && (min.contains("축구장") || min.contains("풋살장"));
    }

    private static SeoulReservationRow toRow(Map<String, Object> row) {
        return new SeoulReservationRow(
                text(row.get("SVCID")),
                text(row.get("SVCNM")),
                text(row.get("AREANM")),
                text(row.get("MINCLASSNM")),
                text(row.get("SVCSTATNM")),
                text(row.get("SVCURL")),
                text(row.get("SVCOPNBGNDT")),
                text(row.get("SVCOPNENDDT")));
    }

    /** 공공 API 는 빈 값을 null 이 아니라 빈 문자열이나 공백으로 준다(USETGTINFO 가 그랬다). */
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).strip();
        return s.isEmpty() ? null : s;
    }

    private static String head(String body) {
        return body.length() <= 200 ? body : body.substring(0, 200) + "…";
    }
}
