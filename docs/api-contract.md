# Kickoff API 계약 v1 (현재 v1.11.0)

조기축구 팀 매칭 앱. 이 문서가 FE/BE 사이의 **단일 진실 공급원**이다.
변경이 필요하면 임의로 고치지 말고 supervisor에게 보고할 것.

> v1.11.0 (2026-08-26): 팀 소속 — §4-3 신설: 가입 신청/수락/거절/취소/탈퇴, MEMBER 역할,
> 명단-계정 통합(`TeamMember.userId`), 팀 검색 `GET /api/teams`, 팀 가입 푸시 3종,
> `myRole`·`/users/me/teams`에 MEMBER 추가, 에러 4종. 팀원-계정 연결을 범위 밖에서 제거.
>
> v1.10.0 (2026-08-25): 고도화 — ① 매칭→전적 연동: `POST /api/requests/{id}/record`,
> `RequestResponse.myRecordWritten`, `TeamRecord.requestId`, 에러 2종. 전적 자동 연동을
> 범위 밖에서 제거(스코어는 수동, 연결은 자동). ② `TeamSummary`에
> `averageRating`·`reviewCount` 추가 — 홈 카드 별점 (v1.2.0의 제외 결정 번복, 사용자 승인).
>
> v1.9.1 (2026-08-25): `GET /api/users/me/teams` 신설 — 내가 소유·관리하는 팀 목록
> (마이 탭 진입점용. 관리자로 임명된 사람이 그 팀을 되찾아갈 길이 없던 문제).
>
> v1.9.0 (2026-08-25): 팀 관리자 — §4-2 신설. 소유자(생성자)가 다른 회원을 팀
> 관리자로 임명(이메일 지정, 최대 5명). 관리자는 **팀 페이지 수정**(팀 정보 PATCH,
> 명단, 기록) 가능. 매칭·리뷰 권한은 소유자 전용 유지. `TeamResponse`에 `myRole`.
>
> v1.8.0 (2026-08-25): 팀 페이지 — §4-1 신설: 팀원 명단(TeamMember CRUD), 경기 기록
> (TeamRecord 수동 입력 + 전적 요약), 팀 프로필 확장(foundedYear·teamColor·formation).
> Position 열거형, 에러 3종. 수동 전적을 범위 밖 목록에서 제거(자동 연동은 여전히 밖).
>
> v1.7.0 (2026-08-25): refresh token — access 1시간 + refresh 30일(로테이션). 로그인류
> 응답에 `refreshToken` 추가, `POST /api/auth/refresh`·`POST /api/auth/logout` 신설,
> `INVALID_REFRESH_TOKEN` 추가. 범위 밖 목록에서 refresh token 제거.
>
> v1.6.0 (2026-08-25): 주요 활동 지역 — `User`에 `activityRegion`(nullable) 추가.
> signup 요청 optional, `UserResponse` 응답, `PATCH /api/users/me`로 수정·지우기(v1.5.1 규칙).
> 홈 기본 필터링은 FE 동작(기존 `region` 쿼리 재사용)이라 BE 목록 API 변경 없음.
>
> v1.5.1 (2026-08-25): PATCH 지우기 규칙 일반화 — 명시적 `null`로 optional 필드를
> 지울 수 있다 (§5 참고). 대상: 모집글 `depositAmount`+계좌 3필드(원자적),
> `preferredSkillLevel`, `rentalFee`, 팀 `homeGround`, `introduction`.
> "필드 없음 = 유지, 명시적 null = 지움"이 PATCH 전반의 공통 규칙이 된다.
>
> v1.5.0 (2026-08-25): 지도 — 모집글에 `latitude`/`longitude`(nullable) 추가,
> 장소 검색 프록시 `GET /api/places/search` 신설(§5-1). 지도를 범위 밖 목록에서 제거.
>
> v1.4.0 (2026-08-24): 푸시 알림 — §8 신설(기존 "범위 밖"은 §9로), Expo Push 기반.
> `PUT /api/users/me/push-token` 신설, 알림 이벤트 4종. 푸시를 범위 밖 목록에서 제거.
>
> v1.3.4 (2026-08-24): 자동 연동 폐지(사용자 결정 번복) — 소셜 로그인은 제공자 불문
> **항상 별개 계정**. 이메일 자동 연동 규칙 삭제, 소셜 계정은 `email` 항상 `null`,
> `EMAIL_CONSENT_REQUIRED` 미사용(코드만 예약). 네이버 이메일 동의도 불필요해짐.
>
> v1.3.3 (2026-08-24): authorize 실패의 전달 규칙 — redirect 검증을 먼저 하고, 통과한
> 뒤의 실패(UNSUPPORTED_PROVIDER 등)는 JSON이 아니라 `{redirect}?error=<코드>`로 302.
> 인앱 브라우저로 여는 API라 JSON 응답은 FE에 전달되지 않기 때문.
>
> v1.3.2 (2026-08-24): 카카오는 이메일 없이 가입 허용 — 카카오 콘솔의 이메일 권한이
> 비즈 앱 전용이라(사용자 확인) 신규 가입 시 `email: null` 허용. 자동 연동(규칙 2)은
> 이메일을 주는 제공자(네이버)에서만 동작. `UserResponse.email` nullable로 변경.
>
> v1.3.1 (2026-08-24): OAuth 예외 명문화 — 허용 목록 밖 redirect는 302 없이 400,
> state 불명 콜백은 401 JSON, 이메일 미제공 검사는 연동 이력 없을 때만. signup 예시 갱신.
>
> v1.3.0 (2026-08-24): 소셜 로그인(OAuth) — §3에 `/api/auth/oauth/*` 신설(1차 KAKAO·NAVER,
> GOOGLE·APPLE은 예약), `PATCH /api/users/me` 신설, `UserResponse`에 `phone`·`authProviders`,
> `AuthProvider` 열거형, 에러 코드 4종. 소셜 로그인을 v1 범위 밖 목록에서 제거.
>
> v1.2.2 (2026-08-24): 리뷰 규정 명확화 — 복수 조건 위반 시 403 우선, 팀 없는 사용자는
> 403(`TEAM_REQUIRED` 아님), 리뷰 목록 동률 정렬 2차 키 `id` DESC.
>
> v1.2.1 (2026-08-24): `RequestResponse`에 `postTeam`(글 작성 팀 `TeamSummary`) 추가 —
> 보낸 신청 목록이 "누구에게 신청했는지"를 표시할 수 없던 갭 해소.
>
> v1.2.0 (2026-08-24): 리뷰·평점 추가 — §7 신설, `TeamResponse`에 `reviewCount`/`averageRating`,
> `RequestResponse`에 `myReviewWritten`, 에러 코드 `REVIEW_*` 2종. 리뷰를 v1 범위 밖 목록에서 제거.

- BE: `kickoff_be` (Spring Boot 4.1.1 / Java 17 / Gradle / JPA)
- FE: `kickoff_fe` (Expo SDK 57 / React Native / expo-router / TypeScript)

## 0. 공통 규약

- Base URL: `http://localhost:8080`
- 모든 API는 `/api` 프리픽스
- 요청/응답 모두 `application/json; charset=UTF-8`
- 인증: `Authorization: Bearer <accessToken>` 헤더
- 날짜/시각: ISO-8601 오프셋 포함 문자열 (`2026-08-30T07:00:00+09:00`)
- 필드 네이밍: camelCase
- ID 타입: `number` (JPA `Long`)

### 에러 응답 (모든 4xx/5xx 공통)

```json
{
  "status": 404,
  "code": "TEAM_NOT_FOUND",
  "message": "존재하지 않는 팀입니다.",
  "timestamp": "2026-08-23T18:40:12+09:00",
  "fieldErrors": [
    { "field": "name", "message": "팀 이름은 필수입니다." }
  ]
}
```

`fieldErrors`는 검증 실패(400 `VALIDATION_FAILED`)일 때만 채워지고, 그 외에는 `[]`.

### 에러 코드

| code | status | 상황 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 요청 본문 검증 실패 |
| `UNAUTHORIZED` | 401 | 토큰 없음/만료/위조 |
| `FORBIDDEN` | 403 | 권한 없음 (남의 글 수정 등) |
| `EMAIL_ALREADY_EXISTS` | 409 | 회원가입 이메일 중복 |
| `LOGIN_FAILED` | 401 | 이메일/비밀번호 불일치 |
| `USER_NOT_FOUND` | 404 | |
| `TEAM_NOT_FOUND` | 404 | |
| `TEAM_ALREADY_EXISTS` | 409 | 이미 팀을 가진 사용자가 또 생성 |
| `TEAM_REQUIRED` | 400 | 팀 없이 글 작성/신청 시도 |
| `POST_NOT_FOUND` | 404 | |
| `POST_NOT_OPEN` | 409 | 마감/매칭완료 글에 신청 |
| `SELF_REQUEST_NOT_ALLOWED` | 400 | 자기 팀 글에 신청 |
| `DUPLICATE_REQUEST` | 409 | 같은 글에 중복 신청 |
| `REQUEST_NOT_FOUND` | 404 | |
| `REQUEST_NOT_PENDING` | 409 | 이미 처리된 신청을 또 처리 |
| `REQUEST_NOT_ACCEPTED` | 409 | 수락되지 않은 신청에 입금 확인 시도 |
| `REVIEW_NOT_AVAILABLE` | 409 | 리뷰 불가 상태 (매칭 미성사 또는 경기 전) |
| `REVIEW_ALREADY_EXISTS` | 409 | 같은 매칭에 이미 리뷰 작성함 |
| `UNSUPPORTED_PROVIDER` | 400 | 비활성/미지원 소셜 제공자 |
| `OAUTH_FAILED` | 401 | 제공자 인증 실패 (콜백 리다이렉트의 `error`로 전달) |
| `EMAIL_CONSENT_REQUIRED` | 400 | (v1.3.4부터 미사용, 예약) |
| `PHONE_REQUIRED` | 400 | 전화번호 없는 사용자가 팀 생성 시도 |
| `PLACE_SEARCH_FAILED` | 502 | 장소 검색 외부 API 실패 |
| `INVALID_REFRESH_TOKEN` | 401 | refresh token 만료·무효·로테이션됨 (재로그인 필요) |
| `TEAM_MEMBER_LIMIT` | 400 | 팀원 30명 초과 |
| `MEMBER_NOT_FOUND` | 404 | |
| `RECORD_NOT_FOUND` | 404 | |
| `ALREADY_TEAM_ADMIN` | 409 | 이미 관리자거나 소유자 본인을 임명 시도 |
| `JOIN_ALREADY_REQUESTED` | 409 | 이미 대기 중인 가입 신청 있음 |
| `ALREADY_TEAM_MEMBER` | 409 | 이미 팀 소속 (OWNER·ADMIN·MEMBER) |
| `JOIN_NOT_FOUND` | 404 | |
| `JOIN_NOT_PENDING` | 409 | 이미 처리된 가입 신청 |
| `RECORD_NOT_AVAILABLE` | 409 | 기록 불가 상태 (매칭 미성사 또는 경기 전) |
| `RECORD_ALREADY_EXISTS` | 409 | 같은 매칭에 이미 전적 기록함 |
| `TEAM_ADMIN_LIMIT` | 400 | 팀 관리자 5명 초과 |
| `ADMIN_NOT_FOUND` | 404 | |
| `NOT_FOUND` | 404 | 매핑되지 않은 경로/리소스 (FE는 "요청한 페이지를 찾을 수 없습니다") |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 (FE는 "잠시 후 다시 시도해주세요"로 표시) |

참고 사항:
- `timestamp`는 밀리초 정밀도까지만 내려간다 (`…16.384+09:00`)
- 응답의 nullable 규칙: 요청에서 optional인 필드는 응답에서도 `null`일 수 있다.
  `costPerTeam`(null이면 FE는 "협의" 표시), `homeGround`, `introduction`, `preferredSkillLevel`, `logoUrl`이 해당.
  BE는 optional 필드를 빈 문자열이 아니라 `null`로 내려보낸다.
- 만료/위조 토큰으로 **인증 불필요** 엔드포인트를 호출하면 401이 아니라 비로그인으로 간주하고 정상 응답한다. 401은 인증 필요 엔드포인트에서만 발생
- 존재하지 않는 경로는 Security 기본 동작으로 404가 아닌 401이 나갈 수 있다 (FE는 경로 오타를 401로 오인하지 말 것)

## 1. 열거형

FE는 아래 값을 그대로 서버에 보내고, 화면 표시는 FE가 한글로 매핑한다.

```
SkillLevel    BEGINNER | AMATEUR | INTERMEDIATE | ADVANCED
              입문      초급       중급           상급

AgeGroup      TEENS | TWENTIES | THIRTIES | FORTIES | FIFTIES_PLUS | MIXED
              10대    20대       30대       40대      50대 이상       혼합

FieldType     FUTSAL | SOCCER_6 | SOCCER_9 | SOCCER_11
              풋살     6인제      9인제      11인제

PostStatus    OPEN | MATCHED | CLOSED
              모집중  매칭완료   마감

RequestStatus PENDING | ACCEPTED | REJECTED | CANCELED
              대기중    수락됨     거절됨     취소됨

Position      GK | DF | MF | FW
              골키퍼  수비   미드필더  공격

AuthProvider  KAKAO | NAVER | GOOGLE | APPLE
              (v1.3에서는 KAKAO·NAVER만 활성. GOOGLE·APPLE은 값만 예약 —
               비활성 제공자 요청은 400 UNSUPPORTED_PROVIDER)
```

## 2. 공통 응답 오브젝트

### UserResponse
```json
{ "id": 1, "email": "kim@example.com", "nickname": "김주장", "phone": "010-1234-5678",
  "hasTeam": true, "teamId": 3, "authProviders": [] }
```

v1.3.0 추가 필드:
- `email`: v1.3.2부터 nullable — **소셜 가입 계정은 항상 `null`** (v1.3.4).
  FE는 이메일 표시 자리에 `null`이면 연동 제공자 표기("카카오 계정" 등)로 대체
- `phone`: 소셜 가입 사용자는 처음에 `null`일 수 있다 (이메일 가입은 항상 있음).
  FE는 `phone`이 `null`이면 전화번호 입력을 유도한다 (팀 생성이 막히므로 — §4 참고)
- `authProviders`: 이 계정에 연동된 소셜 제공자 목록 (`AuthProvider[]`).
  이메일/비번으로만 가입했으면 `[]`
- `activityRegion` (v1.6.0): 주요 활동 지역, nullable. 시/도 단위 문자열 (`"서울"`,
  `"경기"` 등 — FE가 고정 목록에서 선택시키고 BE는 최대 20자 문자열로만 검증).
  홈 목록의 기본 `region` 필터로 쓰인다 (FE 동작). `null`이면 전국.
  소셜 가입자는 항상 `null`로 시작 → FE가 설정 유도. `PATCH /api/users/me`로
  수정하며 v1.5.1 규칙대로 명시적 `null`로 지울 수 있다 (전국으로 복귀)

### TeamSummary (목록/카드에 박히는 축약형)
```json
{ "id": 3, "name": "FC 새벽", "region": "서울 강서구", "skillLevel": "INTERMEDIATE",
  "ageGroup": "THIRTIES", "memberCount": 18, "logoUrl": null,
  "averageRating": 4.5, "reviewCount": 4 }
```

### TeamResponse
```json
{ "id": 3, "name": "FC 새벽", "region": "서울 강서구", "homeGround": "강서구민운동장",
  "skillLevel": "INTERMEDIATE", "ageGroup": "THIRTIES", "memberCount": 18,
  "introduction": "매주 토요일 오전 7시에 모입니다.", "logoUrl": null,
  "ownerNickname": "김주장", "isMine": false, "createdAt": "2026-08-01T10:00:00+09:00",
  "reviewCount": 4, "averageRating": 4.5 }
```

`reviewCount`(받은 리뷰 수), `averageRating`(1~5 평균, **소수 첫째 자리 반올림**. 리뷰 없으면
`null` — FE는 "평가 없음" 표시). v1.2.0 추가. **v1.10.0부터 `TeamSummary`에도 들어간다**
(홈 카드 별점 — v1.2.0의 제외 결정을 사용자 승인으로 번복. BE는 목록 조회에서 팀별
집계를 배치로 실어 N+1을 피할 것).

### PageResponse&lt;T&gt;
```json
{ "content": [], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7, "last": false }
```

## 3. 인증

### POST /api/auth/signup — 인증 불필요

요청

```json
{ "email": "kim@example.com", "password": "pass1234", "nickname": "김주장", "phone": "010-1234-5678" }
```

- `email` 이메일 형식 필수 / `password` 8~64자 필수 / `nickname` 2~20자 필수 / `phone` `010-0000-0000` 형식 필수
- `activityRegion` optional (v1.6.0, 최대 20자) — FE 가입 화면은 선택을 권하지만 건너뛸 수 있다

201 응답

```json
{ "accessToken": "eyJhbGci...", "user": { "id": 1, "email": "kim@example.com", "nickname": "김주장",
  "phone": "010-1234-5678", "hasTeam": false, "teamId": null, "authProviders": [] } }
```

### POST /api/auth/login — 인증 불필요

요청 `{ "email": "...", "password": "..." }` → 200, signup과 동일한 형태

### GET /api/auth/me — 인증 필요

200 → `UserResponse`

### 토큰 정책 (v1.7.0)

- **access token 1시간 / refresh token 30일.** 로그인·가입·소셜·refresh 응답이 항상
  두 토큰을 함께 준다: `{ "accessToken": "...", "refreshToken": "...", "user": {...} }`
  (refresh 응답에는 `user` 없음 — 아래 참고)
- **로테이션**: refresh를 쓸 때마다 새 refresh가 발급되고 이전 것은 즉시 무효.
  사용자당 활성 refresh token은 **1개** (push token과 같은 단일 기기 정책 —
  새 로그인이 이전 기기의 refresh를 무효화한다)
- 서버는 refresh token을 해시로 저장한다 (유출 대비 원문 비보관)

### POST /api/auth/refresh — 인증 불필요 (v1.7.0)

`{ "refreshToken": "..." }` → 200 `{ "accessToken": "...", "refreshToken": "..." }`
(새 쌍. user는 없다 — FE는 필요하면 /auth/me로).
만료·무효·로테이션된 토큰이면 401 `INVALID_REFRESH_TOKEN` — FE는 이때만 로그인
화면으로 보낸다 (access 만료 401과 구분).

### POST /api/auth/logout — 인증 필요 (v1.7.0)

서버의 refresh token 폐기. 204. FE는 이어서 로컬 토큰·push token(null)을 정리한다.
멱등 — 이미 폐기됐어도 204.
**이미 발급된 access token은 로그아웃 후에도 만료(최대 1시간)까지 유효하다** — 즉시
차단하려면 매 요청 DB 조회가 필요해 access를 1시간으로 짧게 잡는 것으로 절충했다.

**FE 자동 로그인 규칙**: 앱 시작 시 저장된 access가 유효하면 그대로, 만료면 refresh로
새 쌍을 받아 조용히 로그인 유지. API 401 시 refresh 1회 시도 후 원 요청 재시도,
refresh도 401이면 그때만 로그아웃 처리. 소셜 콜백 redirect 쿼리에도
`refreshToken`이 추가된다 (`?token=...&refreshToken=...&isNewUser=...`).

### PATCH /api/users/me — 인증 필요 (v1.3.0)

`{ "nickname": "김주장", "phone": "010-1234-5678", "activityRegion": "서울" }` — 전부 optional,
형식은 signup과 동일. 200 → `UserResponse`. 소셜 가입 후 전화번호·활동 지역 보완이 주 용도.
`activityRegion`은 v1.5.1 지우기 규칙 적용 (명시적 `null` → 전국).
`nickname`·`phone`은 지울 수 없는 필드 — 명시적 `null`은 400 (조용히 무시하지 않는다).

## 3-1. 소셜 로그인 (OAuth, v1.3.0)

리다이렉트 방식이다. FE는 SDK 없이 브라우저(WebBrowser)로 BE의 authorize URL을 열고,
BE가 제공자와의 교환을 전부 처리한 뒤 앱으로 되돌아온다. 클라이언트 시크릿은 BE에만 있다.

```
FE ─open→ GET /api/auth/oauth/{provider}/authorize?redirect=<앱 복귀 URL>
BE ─302→ 제공자 로그인/동의 화면
제공자 ─302→ GET /api/auth/oauth/{provider}/callback?code=...&state=...
BE: code 교환 → 프로필 조회 → 계정 결정 → JWT 발급
BE ─302→ {redirect}?token=<accessToken>&isNewUser=true|false   (실패 시 {redirect}?error=<에러코드>)
FE: token 저장 → GET /api/auth/me 로 UserResponse 취득
```

### GET /api/auth/oauth/{provider}/authorize — 인증 불필요

- `provider` 경로값: `kakao` | `naver` (소문자)
- **검사 순서** (v1.3.3): ① `redirect` 허용 목록 검증 — 실패 시 400 JSON (아래).
  ② 그 밖의 모든 실패(비활성 제공자 등)는 이 API를 인앱 브라우저가 열기 때문에 JSON이
  FE에 닿지 않는다 — 검증된 `redirect`로 `{redirect}?error=UNSUPPORTED_PROVIDER` 302 한다
- `redirect` 쿼리 필수: 완료 후 돌아갈 URL. **BE의 허용 목록**과 대조해 통과한 것만 쓴다
  (open redirect 방지, **프리픽스 매칭**). dev 허용 목록: `exp://*`, `kickoff://*`, `http://localhost:*`
- 허용 목록 밖 redirect는 **302 하지 않고** 400 `VALIDATION_FAILED`
  (`fieldErrors[0].field = "redirect"`) — 신뢰하지 않는 주소로 되돌려 보내지 않는다 (v1.3.1)
- `state`는 BE가 생성·검증한다 (CSRF 방지, 일회용). FE는 신경 쓰지 않는다

### GET /api/auth/oauth/{provider}/callback — 제공자 전용

FE가 직접 호출하지 않는다. 성공/실패 모두 `redirect`로 302 한다 (JSON 응답 아님).
**예외** (v1.3.1): `state`를 찾을 수 없거나 이미 사용된 콜백은 복귀 주소를 알 수 없으므로
302가 아니라 401 `OAUTH_FAILED` JSON으로 응답한다.

**계정 결정 규칙** (v1.3.4, 사용자 확정: 소셜은 항상 별개 계정):
1. `(provider, providerUserId)` 연동 이력이 있으면 → 그 계정으로 로그인
2. 없으면 → **무조건 신규 계정 생성.** 이메일 기반 자동 연동은 하지 않는다 —
   같은 사람이 이메일 가입·카카오·네이버로 각각 들어오면 세 개의 별개 계정이 된다.
   `nickname`은 제공자 프로필에서, 중복이면 뒤에 숫자를 붙여 유일하게 만든다.
   `phone`은 `null`, 비밀번호 없음, **`email`도 항상 `null`** (제공자가 이메일을 줘도
   저장하지 않는다 — 식별에 쓰지 않으므로 수집하지 않는 것이 원칙이고, 이메일 가입
   계정과의 유니크 충돌도 원천 차단된다)

이 규칙 덕에 제공자의 이메일 동의항목 설정은 카카오·네이버 모두 **불필요**하다.
`EMAIL_CONSENT_REQUIRED`는 미사용 (코드만 예약 — 연동 기능이 생기는 v2에서 재사용).

**`email: null` 계정의 규칙**: 이메일/비번 로그인 불가(비밀번호도 없음).
그 외 기능은 동일. `UserResponse.email`은 nullable (§2 참고).

**비밀번호 없는 소셜 계정** 이 이메일/비번 로그인을 시도하면 401 `LOGIN_FAILED`
(전용 코드를 만들지 않는다 — 계정 존재 여부 노출 방지. FE 문구도 기존 그대로).

**토큰을 쿼리로 전달하는 것은 dev 한정 허용.** 운영 배포 단계에서 일회용 코드 교환
방식으로 강화한다 (v2 항목).

### 팀 생성 규칙 변경 (§4 연동)

`phone`이 `null`인 사용자가 팀을 만들면 400 `PHONE_REQUIRED`. 매칭 성사 시 `contact`로
전화번호가 공개되는 구조라, 팀 대표는 전화번호가 있어야 한다. FE는 이 코드를 받으면
전화번호 입력 화면으로 유도한다.

## 4. 팀

### POST /api/teams — 인증 필요

한 사용자는 팀을 **하나만** 소유한다. 이미 있으면 409 `TEAM_ALREADY_EXISTS`.

```json
{ "name": "FC 새벽", "region": "서울 강서구", "homeGround": "강서구민운동장",
  "skillLevel": "INTERMEDIATE", "ageGroup": "THIRTIES", "memberCount": 18,
  "introduction": "매주 토요일 오전 7시에 모입니다." }
```

- `name` 2~30자 필수 / `region` 필수 / `skillLevel`, `ageGroup` 필수 / `memberCount` 1~100 필수 / `introduction` 최대 1000자
- 201 → `TeamResponse`

### GET /api/teams/me — 인증 필요

200 → `TeamResponse`, 팀 없으면 404 `TEAM_NOT_FOUND`

### GET /api/teams — 인증 불필요 (v1.11.0, 팀 찾기)

쿼리 (전부 optional): `keyword`(팀 이름 부분 일치), `region`(부분 일치),
`page`(기본 0), `size`(기본 20, 최대 50).
200 → `PageResponse<TeamSummary>`, 생성일 DESC (동률 id DESC).

### GET /api/teams/{teamId} — 인증 불필요

200 → `TeamResponse`

### PATCH /api/teams/{teamId} — 인증 필요, 소유자만

POST와 같은 필드, 전부 optional. 200 → `TeamResponse`
- v1.5.1: `homeGround`, `introduction`은 명시적 `null`로 지울 수 있다 (§5의 PATCH
  지우기 규칙 참고). 필수 필드는 `null` 불가

## 4-1. 팀 페이지 (v1.8.0)

### 팀 프로필 확장

`POST/PATCH /api/teams` 요청과 `TeamResponse`에 추가 (전부 optional/nullable,
PATCH 지우기는 v1.5.1 규칙):

```json
{ "foundedYear": 2020, "teamColor": "#1B7F4B", "formation": "4-4-2" }
```

- `foundedYear` 1900~현재년도 / `teamColor` `#RRGGBB` 형식 / `formation` 최대 10자 자유 문자열
- `TeamResponse`에는 전적 요약도 함께 나간다 (아래 기록에서 집계):
  `"recordSummary": { "wins": 7, "draws": 2, "losses": 3 }` — 기록 0건이면 전부 0

### 팀원 명단 (TeamMember)

팀당 최대 **30명**. 명단은 실제 앱 계정이 아니라 **주장이 입력하는 정보**다.

```json
{ "id": 5, "name": "김철수", "position": "MF", "backNumber": 8 }
```

- `name` 1~20자 필수 / `position` Position enum, nullable / `backNumber` 0~99, nullable
- `GET /api/teams/{teamId}/members` — 인증 불필요. 200 → `TeamMember[]`
  (등번호 오름차순, null 등번호는 뒤에 이름순)
- `POST /api/teams/{teamId}/members` — 소유자만. 201 → `TeamMember`.
  30명 초과 시 400 `TEAM_MEMBER_LIMIT`
- `PATCH /api/teams/{teamId}/members/{memberId}` — 소유자만. 전부 optional,
  position·backNumber는 명시적 null로 지우기 가능(v1.5.1 규칙). 200 → `TeamMember`
- `DELETE /api/teams/{teamId}/members/{memberId}` — 소유자만. 204
- 없는 멤버 404 `MEMBER_NOT_FOUND`, 남의 팀 403 `FORBIDDEN`

### 경기 기록 (TeamRecord)

주장이 **수동 입력**하는 전적. 매칭 시스템과의 자동 연동은 범위 밖(v2).

```json
{ "id": 3, "playedOn": "2026-08-17", "opponentName": "FC 새벽",
  "ourScore": 3, "opponentScore": 1, "result": "WIN", "memo": "후반 역전승" }
```

- `playedOn` 과거~오늘 날짜 필수 / `opponentName` 1~30자 필수 /
  `ourScore`·`opponentScore` 0~99 필수 / `memo` 최대 200자 optional
- `result`는 서버가 스코어로 계산해 내려준다: `WIN | DRAW | LOSS`
- `GET /api/teams/{teamId}/records` — 인증 불필요. `page`/`size`(기본 20, 최대 50).
  200 → `PageResponse<TeamRecord>`, `playedOn` DESC (동률 id DESC)
- `POST /api/teams/{teamId}/records` — 소유자만. 201 → `TeamRecord`
- `DELETE /api/teams/{teamId}/records/{recordId}` — 소유자만. 204. 없는 기록 404 `RECORD_NOT_FOUND`
- 수정은 없다 — 잘못 넣었으면 지우고 다시 (기록 무결성보다 단순함 우선, v1 결정)

### 매칭에서 기록 만들기 (v1.10.0)

`TeamRecord` 응답에 `requestId`(nullable) 추가 — 매칭에서 생성된 기록은 원 매칭을 가리키고,
수동 입력 기록은 `null`.

**POST /api/requests/{requestId}/record — 인증 필요, 매칭 당사자 팀만 (v1.9 권한표상 OWNER)**

```json
{ "ourScore": 3, "opponentScore": 1, "memo": "후반 역전승" }
```

- 상대팀명·경기일은 서버가 매칭에서 채운다 (`opponentName` = 상대 팀 이름,
  `playedOn` = `matchAt`의 날짜). 리뷰(§7)와 같은 패턴 — 양 팀이 각자 자기 관점으로 기록
- 조건: `status` ACCEPTED + `matchAt` 과거 — 아니면 409 `RECORD_NOT_AVAILABLE`.
  내 팀이 이 매칭으로 이미 기록했으면 409 `RECORD_ALREADY_EXISTS`. 제3자 403
- 201 → `TeamRecord` (requestId 채워짐)
- `RequestResponse`에 `myRecordWritten`(boolean) 추가 — `myReviewWritten`과 같은 규칙.
  FE는 `ACCEPTED && matchAt < now && !myRecordWritten`일 때 "전적 기록하기" 버튼 노출
- 매칭 기록도 일반 기록과 같은 목록·요약에 합산된다. 삭제는 기존 DELETE로 (삭제하면
  `myRecordWritten`이 다시 false — 재기록 가능)

## 4-2. 팀 관리자 (v1.9.0)

역할 3단계: **OWNER**(팀 생성자, 유일) > **ADMIN**(소유자가 임명) > 일반.

**권한표** (이 표가 정본 — 다른 절의 "소유자만" 표기 중 팀 페이지 관련은 v1.9.0부터
"소유자·관리자"로 읽는다):

| 행위 | OWNER | ADMIN |
|---|---|---|
| 팀 정보 PATCH (기본+확장 필드) | ✓ | ✓ |
| 팀원 명단·경기 기록 쓰기 (§4-1) | ✓ | ✓ |
| 관리자 임명·해제 | ✓ | ✗ |
| 모집글 작성·수정, 신청, 수락/거절, 입금 확인, 리뷰 | ✓ | ✗ |

- `TeamResponse`에 추가: `"myRole": "OWNER" | "ADMIN" | null` (비로그인·무관계면 null.
  기존 `isMine`은 `myRole === "OWNER"`와 동치로 유지 — 하위호환)
- ADMIN이 소유자 전용 행위를 하면 403 `FORBIDDEN` (전용 코드 없음)
- 한 사용자가 여러 팀의 ADMIN일 수 있다. 자기 팀 소유와도 무관 (팀 없는 사용자도
  ADMIN이 될 수 있다 — 팀 페이지 수정에는 `hasTeam`이 필요 없다)

### GET /api/teams/{teamId}/admins — 소유자·관리자만

200 → `[{ "userId": 4, "nickname": "최총무", "grantedAt": "..." }]` (임명순)

### POST /api/teams/{teamId}/admins — 소유자만

`{ "email": "chongmu@example.com" }` → 201, 위 항목 형태.
- 대상 사용자 없음 404 `USER_NOT_FOUND` / 이미 관리자(또는 소유자 본인) 409
  `ALREADY_TEAM_ADMIN` / 5명 초과 400 `TEAM_ADMIN_LIMIT`
- 상대 동의 절차는 없다 (초대 수락 흐름은 v2)

### DELETE /api/teams/{teamId}/admins/{userId} — 소유자만

204. 해당 사용자가 관리자가 아니면 404 `ADMIN_NOT_FOUND`.

### GET /api/users/me/teams — 인증 필요 (v1.9.1)

내가 소유하거나 관리하는 팀 목록. 마이 탭의 팀 진입점용.

200 → `[{ "team": "<TeamSummary>", "role": "OWNER" }, { "team": "<TeamSummary>", "role": "ADMIN" }]`
(OWNER 먼저, ADMIN은 임명순). 아무것도 없으면 `[]` (에러 아님).

## 4-3. 팀 소속·가입 (v1.11.0)

역할이 확장된다: **OWNER > ADMIN > MEMBER > 일반.** `TeamResponse.myRole`과
`GET /api/users/me/teams`의 `role`에 `MEMBER`가 추가된다 (목록 순서: OWNER → ADMIN
임명순 → MEMBER 가입순). `TeamResponse`에 **`myJoinStatus`: `"PENDING" | null`** 도
추가된다 — 내 PENDING 가입 신청 여부 (소속·비로그인·이력 없음·거절/취소됨은 전부
`null`. 거절 상태를 따로 담지 않는 이유: 재신청이 허용되므로 "신청 가능"과 구분할
필요가 없다). FE 분기: `myRole` 있으면 소속 표시, 없고 `myJoinStatus` PENDING이면
"신청됨+취소", 둘 다 null이면 "가입 신청". MEMBER는 조회·소속 표시만 갖는다 — 팀 페이지 수정 권한 없음,
매칭·리뷰 권한 없음 (v1.9 권한표 유지).

**명단-계정 통합**: `TeamMember`에 `userId`(nullable)가 추가된다. 가입 승인 시 명단에
항목이 자동 생성되고(`name` = 가입자 닉네임, `userId` 연결, position·backNumber null —
주장·관리자가 나중에 채움), 수기 명단은 `userId: null` 그대로다. **계정 연결 항목의
삭제 = 강퇴** (멤버십도 함께 끝난다. 기존 DELETE members API 재사용). 계정 연결 항목의
name은 PATCH로 못 바꾼다 — 닉네임을 따른다 (400 `VALIDATION_FAILED`).

### POST /api/teams/{teamId}/join — 인증 필요

`{ "message": "매주 토요일 나갈 수 있습니다" }` (최대 200자, optional) → 201
`{ "id": 5, "teamId": 3, "status": "PENDING", "message": "...", "createdAt": "..." }`
- 이미 PENDING 신청 있으면 409 `JOIN_ALREADY_REQUESTED` / 이미 소속(OWNER·ADMIN·MEMBER)이면
  409 `ALREADY_TEAM_MEMBER` / 거절·취소 이력이 있으면 재신청 허용 (매칭 신청과 동일 규칙)

### GET /api/teams/{teamId}/join-requests — 소유자·관리자만

PENDING 목록. 200 → `[{ "id": 5, "applicant": { "userId": 9, "nickname": "박멤버" },
"message": "...", "createdAt": "..." }]` (오래된 순 — 먼저 온 신청부터 처리).

### POST /api/teams/{teamId}/join-requests/{joinId}/accept — 소유자·관리자만

수락 → 신청자가 MEMBER가 되고 명단에 자동 등재. 200.
- 명단 30명 초과면 400 `TEAM_MEMBER_LIMIT` (수락 실패, 신청은 PENDING 유지)
- PENDING 아니면 409 `JOIN_NOT_PENDING` / 없으면 404 `JOIN_NOT_FOUND`

### POST /api/teams/{teamId}/join-requests/{joinId}/reject — 소유자·관리자만 → 200

### DELETE /api/teams/{teamId}/join — 인증 필요 (신청자 본인)

내 PENDING 신청 취소 → 204. PENDING 없으면 404 `JOIN_NOT_FOUND`.

### DELETE /api/teams/{teamId}/membership — 인증 필요 (MEMBER 본인)

탈퇴 → 204 (명단 항목도 함께 삭제). MEMBER가 아니면 404 `JOIN_NOT_FOUND` 재사용 대신
403 `FORBIDDEN`. OWNER·ADMIN은 이 API로 못 나간다 (403 — 관리자는 해제 후, 소유자는 v1
범위 밖).

### 푸시 알림 추가 (§8 확장)

| 이벤트 | 수신자 | title / body |
|---|---|---|
| `JOIN_REQUEST_RECEIVED` | 팀 OWNER | "가입 신청" / "{닉네임}님이 {팀}에 가입 신청했습니다" |
| `JOIN_ACCEPTED` | 신청자 | "가입 승인!" / "{팀}의 멤버가 됐습니다" |
| `JOIN_REJECTED` | 신청자 | "가입 불발" / "{팀} 가입 신청이 거절됐습니다" |

data: `{ "type": "...", "teamId": N }`. 탭 시 `JOIN_REQUEST_RECEIVED` → 해당 팀 페이지,
나머지 → 팀 페이지. best-effort 규칙 동일.

## 5. 모집글

### GET /api/posts — 인증 불필요

쿼리 파라미터 (전부 optional)

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `region` | string | 부분 일치 (`서울` → `서울 강서구` 매칭) |
| `fieldType` | FieldType | |
| `skillLevel` | SkillLevel | 모집글이 원하는 상대 수준 |
| `status` | PostStatus | 기본값 `OPEN` |
| `keyword` | string | 제목+내용 부분 일치 |
| `page` | number | 기본 0 |
| `size` | number | 기본 20, 최대 50 |

정렬은 `matchAt` 오름차순(가까운 경기 먼저) 고정.

**지난 경기 규칙**: `matchAt`이 현재보다 과거인 글은 이 목록에서 **제외**한다
(status 필터와 무관 — 지난 경기는 매칭 대상이 아니다). `GET /api/posts/me`는
과거 글도 전부 보여준다 (내 기록). 지난 경기의 상세 조회는 가능하지만
신청은 409 `POST_NOT_OPEN`으로 거절한다 (status가 OPEN이어도).

200 → `PageResponse<PostSummary>`

```json
{ "id": 12, "title": "토요일 아침 풋살 상대 구합니다", "matchAt": "2026-08-30T07:00:00+09:00",
  "location": "강서구민운동장 A구장", "region": "서울 강서구", "fieldType": "FUTSAL",
  "preferredSkillLevel": "INTERMEDIATE", "rentalFee": 100000, "depositAmount": 50000,
  "status": "OPEN", "requestCount": 3, "team": "<TeamSummary>",
  "createdAt": "2026-08-23T09:00:00+09:00" }
```

비용 필드 의미 (v1.1에서 `costPerTeam` 대체):
- `rentalFee`: 글 작성 팀이 낸 **총 구장 대여료** (optional, 정보 표시용)
- `depositAmount`: **매칭 확정 시 상대 팀이 작성 팀에게 보낼 금액** (optional, 0 이상. null이면 "협의")

### POST /api/posts — 인증 필요, 팀 보유 필수

```json
{ "title": "토요일 아침 풋살 상대 구합니다", "content": "6인제로 2시간 뛸 팀 찾습니다. 매너 중요.",
  "matchAt": "2026-08-30T07:00:00+09:00", "location": "강서구민운동장 A구장",
  "region": "서울 강서구", "fieldType": "FUTSAL", "preferredSkillLevel": "INTERMEDIATE",
  "rentalFee": 100000, "depositAmount": 50000,
  "bankName": "카카오뱅크", "accountNumber": "3333-01-1234567", "accountHolder": "김주장" }
```

- `title` 2~60자 필수 / `content` 최대 2000자 필수 / `matchAt` 미래 시각 필수 / `location`, `region` 필수 / `fieldType` 필수 / `preferredSkillLevel` optional(null이면 무관) / `rentalFee`, `depositAmount` 0 이상 optional
- `bankName`(최대 20자), `accountNumber`(최대 30자), `accountHolder`(최대 20자): **입금받을 계좌** — 전부 optional이지만 `depositAmount`를 넣으면 세 필드 모두 필수 (400 `VALIDATION_FAILED`)
- 계좌 3필드는 **절대 목록/상세에 공개되지 않는다.** 오직 수락된 신청 팀에게만 `payment` 오브젝트로 내려간다 (아래 참고)
- 팀 없으면 400 `TEAM_REQUIRED`
- 201 → `PostDetail`

### GET /api/posts/{postId} — 인증 불필요 (토큰 있으면 추가 필드 채움)

```json
{ "id": 12, "title": "...", "content": "...", "matchAt": "2026-08-30T07:00:00+09:00",
  "location": "강서구민운동장 A구장", "region": "서울 강서구", "fieldType": "FUTSAL",
  "preferredSkillLevel": "INTERMEDIATE", "rentalFee": 100000, "depositAmount": 50000,
  "status": "OPEN", "viewCount": 42, "requestCount": 3, "team": "<TeamResponse>",
  "isAuthor": false, "myRequestStatus": null, "contact": null, "payment": null,
  "createdAt": "2026-08-23T09:00:00+09:00" }
```

- `isAuthor`: 내 팀이 쓴 글인지. 비로그인이면 `false`
- `myRequestStatus`: 내 팀이 이 글에 보낸 신청 상태(`RequestStatus`). 신청 안 했거나 비로그인이면 `null`
- `contact`: **매칭 수락된 두 팀에게만** 공개. 그 외 전부 `null`

```json
{ "nickname": "김주장", "phone": "010-1234-5678" }
```

- 글 작성자가 볼 때 → 수락한 상대 팀의 연락처
- 신청 팀이 볼 때 → 글 작성 팀의 연락처

- `payment`: 입금 안내 오브젝트. **두 경우에만** 채워진다 — ① 수락된 신청 팀이 볼 때
  (입금해야 할 정보), ② **글 작성자 본인**이 볼 때 (자기가 등록한 계좌 확인·수정용).
  비로그인, 제3자, 수락 전 신청 팀은 전부 `null`

```json
{ "depositAmount": 50000, "bankName": "카카오뱅크", "accountNumber": "3333-01-1234567",
  "accountHolder": "김주장", "depositPaid": false }
```

`depositPaid`는 글 작성자가 입금 확인을 눌렀는지 여부. 수락된 신청이 없으면 `false`.
PATCH의 `depositAmount` 검증("있으면 계좌 3필드 필수")은 요청 본문이 아니라
**병합된 결과** 기준 — 이미 계좌가 저장돼 있으면 금액만 보내도 된다.

### PATCH /api/posts/{postId} — 인증 필요, 작성자만

POST와 같은 필드 + `status`, 전부 optional. 200 → `PostDetail`

**PATCH 지우기 규칙 (v1.5.1)**: 모든 PATCH에서 **필드 없음 = 기존 유지, 명시적
`null` = 지움**이다 (좌표의 §5-1 규칙을 일반화). 지울 수 있는 필드:
- `depositAmount`를 `null`로 → 입금액·계좌 3필드가 **함께** 지워진다 (금액 없는 계좌는
  의미가 없고, 남으면 "무료 경기인데 입금 안내가 붙은" 어긋남이 생긴다). 계좌 3필드만
  개별로 `null` 보내는 것은 400 `VALIDATION_FAILED` — 계좌를 지우려면 `depositAmount`를
  지워라 (병합 결과 기준 "금액 있으면 계좌 필수" 규칙과 일관)
- `preferredSkillLevel` `null` → 실력 무관으로
- `rentalFee` `null` → 대여료 미정으로
- 필수 필드(`title`, `content`, `matchAt`, `location`, `region`, `fieldType`)와
  **`status`**는 `null` 불가 — 400 `VALIDATION_FAILED` (status는 optional로 보낼 수는
  있지만 지울 수는 없는 값)
- 계좌 필드의 빈 문자열(`""`)은 `null`과 동일 취급하지 않는다 — **POST·PATCH 모두**
  검증 실패로 거절 (POST에서 `""`를 허용하면 PATCH로 고칠 수 없는 글이 생긴다).
  지우기는 오직 `null`로

### DELETE /api/posts/{postId} — 인증 필요, 작성자만 → 204

### GET /api/posts/me — 인증 필요

내 팀이 작성한 글 목록. 200 → `PageResponse<PostSummary>`
- 정렬: `createdAt` DESC (방금 쓴 글이 위로 — 홈 목록과 다름)
- 팀이 없으면 에러가 아니라 **빈 페이지** 반환

### requestCount 정의 (PostSummary/PostDetail 공통)

살아 있는 신청(`PENDING` + `ACCEPTED`)만 센다. 취소·거절된 신청은 제외.

## 5-1. 장소 검색·좌표 (v1.5.0)

### 모집글 좌표 필드

`PostSummary`·`PostDetail` 응답과 `POST/PATCH /api/posts` 요청에 추가:

```json
{ "latitude": 37.5586, "longitude": 126.8351 }
```

- 둘 다 **nullable** — 좌표 없이도 글 등록 가능 (직접 입력 장소). 기존 글은 전부 `null`
- 검증: 둘 중 **하나만 보내면** 400 `VALIDATION_FAILED` (쌍으로만). 위도 -90~90, 경도 -180~180 (경계값 유효)
- **PATCH도 요청 본문 기준으로 쌍 검증한다** (계좌 필드의 "병합 결과 기준"과 다름 —
  위도만 바꾸는 요청을 허용하면 이전 경도와 짝지어져 조용히 틀린 지점이 생긴다.
  좌표는 응답으로 되읽을 수 있으므로 수정 시 항상 쌍으로 보낼 것). 좌표를 안 보내는
  PATCH는 기존 좌표를 유지한다 (지우려면 명시적으로 둘 다 null)
- FE는 좌표가 있는 글에만 상세에서 지도를 그린다. `null`이면 지도 영역 자체를 숨김

### GET /api/places/search — 인증 필요 (v1.5.0)

카카오 로컬 키워드 검색의 **BE 프록시.** 검색 API 키를 앱 번들에 노출하지 않기 위해
BE가 대행한다. FE는 글 작성/수정의 장소 입력에서 이걸 호출한다.

쿼리: `query` (필수, 1~100자), `size` (기본 10, 최대 15)

200 응답:

```json
{ "places": [
  { "name": "강서구민운동장", "address": "서울 강서구 화곡동 980-16",
    "roadAddress": "서울 강서구 남부순환로 172", "latitude": 37.5586, "longitude": 126.8351 }
] }
```

- 결과 없으면 `{ "places": [] }` (에러 아님)
- `roadAddress`는 nullable
- 카카오 API 실패 시 502 `PLACE_SEARCH_FAILED` (FE는 "장소 검색에 실패했습니다.
  직접 입력해 주세요"로 안내하고 직접 입력 경로를 열어둔다)
- BE 환경변수: **`KAKAO_MAP_REST_KEY`** — 로그인용 `KAKAO_CLIENT_ID`와 **별개의 키**다
  (v1.5.0 개정: 지도는 카카오맵이 활성화된 별도 카카오 앱의 키를 쓴다. kickoff 앱에서
  카카오맵을 새로 켜면 결제수단 등록이 필요해 사용자가 기존 앱을 재사용하기로 결정).
  FE의 지도 JS SDK 키도 같은 별도 앱의 JavaScript 키를 쓴다

## 6. 매칭 신청

### RequestResponse

```json
{ "id": 7, "postId": 12, "postTitle": "토요일 아침 풋살 상대 구합니다",
  "postStatus": "OPEN", "matchAt": "2026-08-30T07:00:00+09:00",
  "applicantTeam": "<TeamSummary>", "postTeam": "<TeamSummary>",
  "message": "저희도 강서구라 가깝습니다!",
  "status": "PENDING", "contact": null, "payment": null, "depositPaid": false,
  "myReviewWritten": false,
  "createdAt": "2026-08-23T11:00:00+09:00" }
```

`postTeam`(v1.2.1): **글 작성 팀**의 `TeamSummary`. 보낸 신청 목록에서 "누구에게
신청했는지", 리뷰 쓰기에서 "누구를 평가하는지"를 표시하는 데 쓴다. 받은/보낸 어느
관점에서든 항상 채워진다 (applicantTeam과 함께 매칭의 양 팀이 응답에 모두 담긴다).

`myReviewWritten`(v1.2.0): **요청자의 팀**이 이 매칭에 리뷰를 이미 썼는지. FE는
`status === 'ACCEPTED' && matchAt < now && !myReviewWritten`일 때 "리뷰 쓰기" 버튼을 노출한다.

`contact`는 `status`가 `ACCEPTED`일 때만 채워진다. 규칙은 PostDetail과 동일.
`postStatus`는 신청이 걸린 글의 현재 상태 — FE가 받은/보낸 신청 목록에서 글 상태
뱃지를 그리는 데 쓴다 (별도 글 조회 없이).
`payment`는 **신청 팀이 볼 때 + ACCEPTED일 때만** 채워진다 (§5의 payment와 동일 형태).
글 작성자가 볼 때는 `payment: null`이고 `depositPaid`로 입금 확인 상태만 본다.

### POST /api/requests/{requestId}/confirm-deposit — 인증 필요, 글 작성자만

상대 팀의 입금을 확인했다고 표시한다. `depositPaid` → `true`.
- 신청이 `ACCEPTED` 상태가 아니면 409 `REQUEST_NOT_ACCEPTED`
- 200 → `RequestResponse`
- 입금 확인 후에도 글/신청 상태는 변하지 않는다 (기록용 플래그)

### POST /api/posts/{postId}/requests — 인증 필요, 팀 보유 필수

`{ "message": "저희도 강서구라 가깝습니다!" }` (최대 500자, optional)

거절 케이스: 팀 없음 400 `TEAM_REQUIRED` / 내 글 400 `SELF_REQUEST_NOT_ALLOWED` / 글이 OPEN 아님 409 `POST_NOT_OPEN` / 이미 PENDING·ACCEPTED 신청 있음 409 `DUPLICATE_REQUEST`
(CANCELED·REJECTED 이력이 있으면 재신청 허용)

201 → `RequestResponse`

### GET /api/posts/{postId}/requests — 인증 필요, 글 작성자만

받은 신청 목록. 200 → `RequestResponse[]` (PENDING 먼저, 그다음 최신순)

### GET /api/requests/received — 인증 필요

내 팀이 작성한 **모든** 글에 온 신청을 한 번에 반환. FE 매칭관리 탭이 글마다
`GET /api/posts/{postId}/requests`를 반복 호출하지 않도록 하기 위한 집계 엔드포인트.

200 → `RequestResponse[]` (PENDING 먼저, 그다음 최신순). FE가 `postId`로 그룹핑한다.
팀이 없으면 **빈 배열** (에러 아님 — 조회성 API는 팀 미보유를 에러로 취급하지 않는다.
`/sent`, `/posts/me`와 동일 규칙).

### GET /api/requests/sent — 인증 필요

내 팀이 보낸 신청 목록. 200 → `RequestResponse[]` (최신순). 팀이 없으면 빈 배열.

### POST /api/requests/{requestId}/accept — 인증 필요, 글 작성자만

- 해당 신청 `ACCEPTED`
- 글 `status` → `MATCHED`
- **같은 글의 다른 PENDING 신청은 전부 자동 `REJECTED`**
- 이 시점부터 양쪽에 `contact` 공개
- 200 → `RequestResponse`
- 이미 처리된 신청이면 409 `REQUEST_NOT_PENDING`

### POST /api/requests/{requestId}/reject — 인증 필요, 글 작성자만

200 → `RequestResponse` (status `REJECTED`). 글 상태는 그대로 `OPEN`

### DELETE /api/requests/{requestId} — 인증 필요, 신청한 팀만

PENDING 신청 취소 → `CANCELED`. 204. PENDING이 아니면 409 `REQUEST_NOT_PENDING`.

### myRequestStatus 규칙 (PostDetail)

재신청 이력이 있으면 **가장 최근** 신청의 상태를 반환한다.

## 7. 리뷰·평점 (v1.2.0)

경기가 끝난 매칭(수락된 신청)에 대해 **양 팀이 서로 한 번씩** 상대 팀을 평가한다.
리뷰 단위는 팀이 아니라 **매칭(requestId)** — 같은 두 팀이 다른 경기로 또 만나면 또 쓸 수 있다.

### ReviewResponse

```json
{ "id": 3, "requestId": 7, "postId": 12, "postTitle": "토요일 아침 풋살 상대 구합니다",
  "matchAt": "2026-08-30T07:00:00+09:00", "reviewerTeam": "<TeamSummary>",
  "targetTeamId": 3, "rating": 5, "comment": "시간 약속 정확하고 매너 좋았습니다.",
  "createdAt": "2026-08-30T10:12:00+09:00" }
```

### POST /api/requests/{requestId}/review — 인증 필요, 매칭 당사자 팀만

```json
{ "rating": 5, "comment": "시간 약속 정확하고 매너 좋았습니다." }
```

- `rating` 1~5 **정수** 필수 / `comment` 최대 500자 optional (없으면 `null` 저장)
- 대상 팀은 서버가 결정한다: 글 작성 팀이 쓰면 → 신청 팀, 신청 팀이 쓰면 → 글 작성 팀
- 작성 조건 (전부 만족해야 함):
  - 신청 `status`가 `ACCEPTED` — 아니면 409 `REVIEW_NOT_AVAILABLE`
  - `matchAt`이 현재보다 **과거** (경기가 끝났어야 함) — 아니면 409 `REVIEW_NOT_AVAILABLE`
  - 내 팀이 그 매칭의 당사자 (글 작성 팀 또는 신청 팀) — 아니면 403 `FORBIDDEN`.
    **팀이 없는 사용자도 403** (어느 매칭의 당사자도 아니므로. `TEAM_REQUIRED`는
    글 작성/신청 전용이라 리뷰에는 쓰지 않는다)
  - 내 팀이 이 매칭에 아직 안 씀 — 이미 썼으면 409 `REVIEW_ALREADY_EXISTS`
- 검사 순서는 **권한 먼저**: 여러 조건을 동시에 위반하면 409가 아니라 403이 나간다
  (제3자에게 매칭의 상태를 알려주지 않기 위함)
- 201 → `ReviewResponse`
- 수정/삭제는 없다 (한 번 쓰면 확정). 관리 기능은 v2로 미룬다

### GET /api/teams/{teamId}/reviews — 인증 불필요

팀이 **받은** 리뷰 목록. 쿼리 `page`(기본 0), `size`(기본 20, 최대 50).
200 → `PageResponse<ReviewResponse>`, `createdAt` DESC (동률이면 `id` DESC —
페이징이 결정적이어야 무한 스크롤에서 중복/누락이 없다). 팀이 없으면 404 `TEAM_NOT_FOUND`.

### 평점 집계 규칙

- `TeamResponse.averageRating` = 받은 리뷰 rating 평균, 소수 첫째 자리 반올림 (`4.4666…` → `4.5`)
- 리뷰 0건이면 `averageRating: null`, `reviewCount: 0`
- 집계는 조회 시점 계산이든 반정규화든 BE 구현 자유 — 계약은 응답 값만 규정한다

## 8. 푸시 알림 (v1.4.0)

Expo Push Service 기반. FE가 기기에서 Expo push token을 받아 BE에 등록하면,
BE가 매칭 이벤트 발생 시 Expo Push API(`https://exp.host/--/api/v2/push/send`)로 보낸다.
푸시는 **best-effort** — 발송 실패가 원 트랜잭션(신청/수락 등)을 실패시키면 안 된다.

### PUT /api/users/me/push-token — 인증 필요

```json
{ "expoPushToken": "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]" }
```

- 사용자당 토큰 **1개** (마지막 등록이 이김 — 기기를 바꾸면 새 기기만 받는다. 다중 기기는 v2)
- `expoPushToken` 필수, `ExponentPushToken[` 접두 형식 검증 (아니면 400 `VALIDATION_FAILED`)
- 204. 같은 토큰 재등록도 204 (멱등)
- **토큰 삭제**: body를 `{ "expoPushToken": null }`로 보내면 등록 해제 → 204. FE는 로그아웃 시 호출

### 알림 이벤트 4종

수신자는 전부 **팀의 소유자(owner) 사용자**다. push token이 없으면 조용히 건너뛴다.

| 이벤트 | 시점 | 수신자 | title / body 예시 |
|---|---|---|---|
| `REQUEST_RECEIVED` | 신청 생성 | 글 작성 팀 | "새 매칭 신청" / "{신청팀}이(가) '{글제목}'에 신청했습니다" |
| `REQUEST_ACCEPTED` | 수락 | 신청 팀 | "매칭 성사!" / "'{글제목}' 신청이 수락됐습니다. 연락처가 공개됐어요" |
| `REQUEST_REJECTED` | 거절 | 신청 팀 | "매칭 불발" / "'{글제목}' 신청이 거절됐습니다" |
| `DEPOSIT_CONFIRMED` | 입금 확인 | 신청 팀 | "입금 확인" / "'{글제목}' 입금이 확인됐습니다" |

수락 시 자동 거절되는 다른 PENDING 신청들에도 각각 `REQUEST_REJECTED`를 보낸다.

### 푸시 payload의 data (FE 딥링크용)

```json
{ "type": "REQUEST_ACCEPTED", "requestId": 7, "postId": 12 }
```

FE는 알림 탭 시 `type`에 따라 이동한다: `REQUEST_RECEIVED` → 매칭 관리(받은 신청),
나머지 → 매칭 관리(보낸 신청). 상세 화면 딥링크는 v2.

### 환경변수 (BE)

`PUSH_ENABLED` (기본 false — dev에서 실수로 실기기에 안 나가게. 운영과 푸시 테스트 시 true)

## 9. v1 범위 밖 (구현하지 말 것)

실시간 채팅, 이미지 업로드(팀 로고 포함), 스쿼드/포메이션 보드,
기록 수정, 소유권 이전·소유자 탈퇴, 가입 초대(팀→사용자 방향),
경기 종료 리마인드 푸시(기록·리뷰 유도 알림),
리뷰 수정·삭제·신고, 소셜 계정 연동 해제, GOOGLE·APPLE 로그인 활성화(값만 예약),
다중 기기 push token, 알림 히스토리 화면, 알림 설정(끄기/켜기),
홈 목록 지도 뷰, 좌표 반경 검색, 정적 지도 이미지
