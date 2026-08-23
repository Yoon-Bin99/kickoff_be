# kickoff_be — 조기축구 팀 매칭 앱 백엔드

## 이 저장소의 역할

조기축구 팀 매칭 앱 "Kickoff"의 REST API 서버. 프론트엔드는 옆 저장소
`../kickoff_fe` (Expo / React Native)가 담당하며, 이 저장소는 API만 책임진다.

## 반드시 먼저 읽을 것

**`docs/api-contract.md`** (저장소 내 사본, 원본은 ../docs/api-contract.md — supervisor가 변경 시 양쪽에 동기화한다) — FE/BE 공통 API 계약. 엔드포인트, DTO 필드명,
에러 코드, 열거형 값이 전부 여기 정의돼 있다. 이 문서가 단일 진실 공급원이다.

계약과 다르게 구현하면 FE가 깨진다. 계약을 바꿔야 할 이유를 발견하면
**임의로 바꾸지 말고 supervisor 세션에 보고**할 것. supervisor가 문서를 고치고
양쪽에 전파한다.

## 기술 스택 (확정, 임의 변경 금지)

- Spring Boot 4.1.1 / Java 17 / Gradle
- Spring Web MVC, Spring Data JPA, Spring Security, Validation
- 인증: JWT access token 단일 (만료 7일), jjwt 0.12.6
- DB: 로컬 개발 H2, 배포 PostgreSQL

Boot 4.x는 스타터 이름이 3.x와 다르다. `spring-boot-starter-web`이 아니라
**`spring-boot-starter-webmvc`**이고, 테스트 스타터도 `spring-boot-starter-webmvc-test`
처럼 모듈별로 쪼개져 있다. 이미 `build.gradle`에 맞게 들어가 있으니 3.x 관례로
되돌리지 말 것.

### Boot 4 / Spring 7에서 3.x 관례가 깨지는 지점

구현하면서 실제로 걸렸던 것들이다. 3.x 예제를 그대로 옮기면 컴파일이나 기동이 깨진다.

- **Jackson 3**: 웹 계층이 `tools.jackson.databind`를 쓴다(2.x의 `com.fasterxml.jackson.databind`가
  아니다). 날짜 플래그가 `SerializationFeature`/`DeserializationFeature`에서
  `DateTimeFeature`로 옮겨가서, `spring.jackson.serialization.write-dates-as-timestamps`
  같은 기존 키를 쓰면 **기동 자체가 실패**한다. `spring.jackson.datatype.datetime.*` 아래로 쓸 것.
  어노테이션(`@JsonProperty` 등)은 여전히 `com.fasterxml.jackson.annotation` 2.x 그대로다.
- **Spring Security 7**: `AntPathRequestMatcher`가 제거됐다. `requestMatchers("/h2-console/**")`
  처럼 문자열 패턴을 그대로 넘기면 된다.
- **레코드의 boolean 필드**: `isMine`/`isAuthor`/`hasTeam`처럼 `is`로 시작하는 필드는 직렬화 이름이
  `mine`으로 깎일 수 있어 `@JsonProperty("isMine")`을 명시했다. 계약서 필드명이라 지울 것.

## 패키지 구조

`com.kickoff.be` 아래 도메인별로 나눈다.

```
com.kickoff.be
├── common      공통 응답/에러 (ErrorCode, BusinessException, ApiExceptionHandler, PageResponse, BaseTimeEntity)
├── config      SecurityConfig, CorsConfig, JpaAuditingConfig, DataInitializer
├── auth        JwtTokenProvider, JwtAuthenticationFilter, AuthController/Service, @LoginUser
├── user        User 엔티티, Repository
├── team        Team 엔티티, Repository, Service, Controller, DTO
├── post        MatchPost 엔티티 + 열거형, Repository, Service, Controller, DTO
└── matchrequest  MatchRequest 엔티티, Repository, Service, Controller, DTO
```

## 작업 규칙

- 엔티티는 `@Setter` 금지. 상태 변경은 의도가 드러나는 메서드로 (`post.markMatched()`)
- 연관관계는 전부 `LAZY`. 목록 조회는 N+1 나지 않게 fetch join 또는 `@EntityGraph`
- 컨트롤러는 엔티티를 직접 반환하지 않는다. 항상 DTO로 변환
- 서비스 레이어에 `@Transactional`. 조회는 `readOnly = true`
- 예외는 `BusinessException(ErrorCode.XXX)`로 던지고 `@RestControllerAdvice`가 계약서
  형식으로 변환
- 시각 필드는 `OffsetDateTime` 사용 (계약서가 오프셋 포함 ISO-8601을 요구)
- 커밋·푸시 방침(사용자 확정): 기능 하나가 완성될 때마다 커밋하고 origin main으로 푸시한다. supervisor의 작업 지시에 커밋 단위가 명시되면 그에 따른다

## 로컬 실행

```bash
./gradlew bootRun
```

CORS는 Expo 개발 서버(`http://localhost:8081`, `exp://` 오리진)를 허용해야 한다.
개발 프로파일에서는 `allowedOriginPatterns("*")`로 열어두고 운영에서 좁힌다.

Android 에뮬레이터는 호스트를 `10.0.2.2`로 보므로, FE가 그 주소로 붙어도
동작하게 `0.0.0.0` 바인딩(기본값)을 유지할 것.

## 시드 데이터

개발 프로파일에서 `DataInitializer`가 팀 5~6개와 모집글 10개 남짓을 넣는다.
FE가 목록/필터 화면을 바로 붙여볼 수 있어야 하므로, 지역·구장유형·실력수준이
골고루 섞이게 만들 것. 비밀번호는 전부 `pass1234`로 통일.
