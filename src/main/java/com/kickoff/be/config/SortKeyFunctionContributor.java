package com.kickoff.be.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.type.StandardBasicTypes;

/**
 * 한글 정렬을 <b>서버 로케일과 무관하게</b> 만드는 HQL 함수 {@code sortkey(x)} 를 등록한다.
 *
 * <b>왜 필요한가 — v1.27.0 배포에서 실제로 당한 일이다.</b> 구장 목록이 운영에서만 순서가
 * 엉켰다. "인천 서구"가 맨 앞, "인천 연수구"가 맨 뒤로 가서 경기 구장 셋을 사이에 두고
 * 갈라졌다. 같은 지역 두 행이 갈라지는 건 불가능해 보이지만 실제로 그랬다.
 *
 * 원인은 <b>libc 별 콜레이션 차이</b>다. 운영 DB 는 glibc 의 {@code en_US.utf8} 인데,
 * glibc 는 다단계 가중치로 비교하고 한글은 en_US 규칙에 정의돼 있지 않아 상위 레벨에서
 * 무시될 수 있다. 그러면 <b>첫 글자가 순서를 결정하지 않는다.</b> 반면 H2 는 자바 문자열
 * 비교(코드포인트)이고, 테스트에 쓰던 alpine 이미지는 musl libc 라 역시 사실상 코드포인트
 * 순서였다. 그래서 H2 도 PostgreSQL 테스트도 전부 통과하는데 운영만 틀렸다.
 *
 * <b>해법은 콜레이션을 못박는 것</b>이다. {@code "C"} 는 바이트(=코드포인트) 순서인데,
 * 한글 음절 U+AC00~U+D7A3 은 유니코드상 가나다순으로 배열돼 있어 <b>"C" 가 곧 올바른
 * 한국어 순서</b>다. 게다가 환경이 달라도 값이 같다 — 이 기능에서 원하는 건 정확히 그것이다.
 *
 * <b>왜 함수로 감쌌는가.</b> H2 는 {@code COLLATE} 구문 자체를 모른다(ORDER BY 에서도
 * 컬럼 정의에서도 구문 오류다). 쿼리에 직접 적으면 개발·테스트가 통째로 깨진다. 그래서
 * 방언을 보고 PostgreSQL 일 때만 {@code collate "C"} 를 붙이고, 그 외에는 값을 그대로
 * 둔다 — H2 의 기본 정렬이 이미 코드포인트 순서라 결과가 같다.
 *
 * <b>쓰는 곳</b>: 한글 텍스트로 정렬하는 쿼리 전부. 지금은 두 군데다 —
 * {@code StadiumRepository}(지역·이름)와 {@code TeamMemberRepository}(팀원 이름).
 * id·시각·열거형으로 정렬하는 나머지 쿼리는 콜레이션을 타지 않으므로 손대지 않았다.
 * 한글 컬럼으로 정렬하는 쿼리를 새로 쓸 때는 이걸 씌울 것.
 */
public class SortKeyFunctionContributor implements FunctionContributor {

    /** HQL 에서 부르는 이름. 하이버네이트 내장 함수와 겹치지 않는 이름이어야 한다. */
    public static final String FUNCTION_NAME = "sortkey";

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        // 인자를 그대로 두는 것이 기본이다. 콜레이션을 아는 방언에서만 못박는다 —
        // 모르는 방언에 억지로 붙이면 기동조차 못 한다.
        String pattern = functionContributions.getDialect() instanceof PostgreSQLDialect
                ? "?1 collate \"C\""
                : "?1";
        functionContributions.getFunctionRegistry().registerPattern(
                FUNCTION_NAME,
                pattern,
                functionContributions.getTypeConfiguration()
                        .getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.STRING));
    }
}
