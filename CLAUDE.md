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
- 인증: JWT access(1시간) + refresh(30일) 두 토큰, jjwt 0.12.6.
  refresh 는 서버에 해시로 저장돼 로그아웃·재로그인으로 즉시 끊을 수 있다 (v1.7.0~).
  만료 값은 `application.yaml` 의 `jwt.access-expiration`/`refresh-expiration`
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

## 패키지 구조 (사용자 확정 — 도메인 안을 레이어별 하위 패키지로)

`com.kickoff.be` 아래 도메인별로 나누고, **각 도메인 안은 레이어별 하위 패키지**로
정리한다. 도메인 패키지 바로 아래에 클래스를 두지 말 것.

```
com.kickoff.be
├── common      공통 응답/에러 (ErrorCode, BusinessException, ApiExceptionHandler, ErrorResponse,
│               PageResponse, BaseTimeEntity, ContactInfo, PaymentInfo, Patchable ...) — 평면 유지
├── config      SecurityConfig, CorsConfig, WebConfig, AsyncConfig, JpaAuditingConfig,
│               DataInitializer — 평면 유지
├── auth        인증 (특성상 entity 없음)
│   ├── controller / service / dto
│   └── jwt     인증 인프라 — access·refresh 토큰·필터·@LoginUser·리졸버, 인증/인가 실패 응답까지
├── user            entity(User) / repository / service / controller / dto
├── team            entity(Team, TeamMember, TeamAdmin, TeamJoinRequest, TeamRecord,
│                   SkillLevel, AgeGroup, Position, TeamRole, JoinStatus, MatchResult) / ...
├── post            entity(MatchPost, PostStatus, TimeSlot) / ...
├── matchrequest    entity(MatchRequest, RequestStatus) / ...
│                   (PostRequestCount 같은 조회 프로젝션은 repository에)
├── review          팀 평가 (계약서 §7)
├── chat            매칭 성사 후 1:1 채팅
├── support         고객센터 — FAQ·AI 상담·운영자 연결 (§7-1)
├── oauth           카카오·네이버 소셜 로그인 (§3-1)
├── verification    전화번호 문자 인증 (§3-2)
├── passwordreset   비밀번호 재설정 (§3-3)
├── place           카카오 장소 검색 프록시 (§5-1)
├── legal           약관·개인정보처리방침 정적 서빙
├── push            Expo 푸시 (§8)          — client / dto / service
├── sms             문자 발송 어댑터          — client
└── email           메일 발송 어댑터          — client
```

도메인이 열여덟이라 전부 펼치지 않았다. **레이어별 하위 패키지 원칙은 모든 도메인에
똑같이 적용**된다 — 위에서 `/ ...` 로 줄인 자리도 `entity / repository / service /
controller / dto` 구성이다. 외부 호출을 감싸는 도메인(push·sms·email·place·oauth·support)은
`client` 하위 패키지에 어댑터를 둔다.

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

개발 프로파일에서 `DataInitializer`가 팀 6개와 모집글 34개를 넣는다. 신청·리뷰·팀 명단·
전적·관리자·가입신청까지 함께 들어가서, 계정마다 보이는 화면이 다르다(어느 계정이 어떤
화면에 좋은지는 README 의 표 참고 — 시드를 바꾸면 그 표도 같이 고칠 것).

FE가 목록/필터 화면을 바로 붙여볼 수 있어야 하므로, 지역·실력수준·날짜·시간대가
골고루 섞이게 만들 것. 비밀번호는 전부 `pass1234`로 통일.

구장 유형(`FieldType`)은 v1.19.0 에서 **폐지**됐다 — 11대11 전용 제품으로 정해졌다.
시드에도 없고 엔티티에도 없다.
