# Kickoff API 계약 v1

조기축구 팀 매칭 앱. 이 문서가 FE/BE 사이의 **단일 진실 공급원**이다.
변경이 필요하면 임의로 고치지 말고 supervisor에게 보고할 것.

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
```

## 2. 공통 응답 오브젝트

### UserResponse
```json
{ "id": 1, "email": "kim@example.com", "nickname": "김주장", "hasTeam": true, "teamId": 3 }
```

### TeamSummary (목록/카드에 박히는 축약형)
```json
{ "id": 3, "name": "FC 새벽", "region": "서울 강서구", "skillLevel": "INTERMEDIATE",
  "ageGroup": "THIRTIES", "memberCount": 18, "logoUrl": null }
```

### TeamResponse
```json
{ "id": 3, "name": "FC 새벽", "region": "서울 강서구", "homeGround": "강서구민운동장",
  "skillLevel": "INTERMEDIATE", "ageGroup": "THIRTIES", "memberCount": 18,
  "introduction": "매주 토요일 오전 7시에 모입니다.", "logoUrl": null,
  "ownerNickname": "김주장", "isMine": false, "createdAt": "2026-08-01T10:00:00+09:00" }
```

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

201 응답

```json
{ "accessToken": "eyJhbGci...", "user": { "id": 1, "email": "kim@example.com", "nickname": "김주장", "hasTeam": false, "teamId": null } }
```

### POST /api/auth/login — 인증 불필요

요청 `{ "email": "...", "password": "..." }` → 200, signup과 동일한 형태

### GET /api/auth/me — 인증 필요

200 → `UserResponse`

토큰은 만료 7일 access token 단일. refresh token은 v1 범위 밖.

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

### GET /api/teams/{teamId} — 인증 불필요

200 → `TeamResponse`

### PATCH /api/teams/{teamId} — 인증 필요, 소유자만

POST와 같은 필드, 전부 optional. 200 → `TeamResponse`

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

### DELETE /api/posts/{postId} — 인증 필요, 작성자만 → 204

### GET /api/posts/me — 인증 필요

내 팀이 작성한 글 목록. 200 → `PageResponse<PostSummary>`
- 정렬: `createdAt` DESC (방금 쓴 글이 위로 — 홈 목록과 다름)
- 팀이 없으면 에러가 아니라 **빈 페이지** 반환

### requestCount 정의 (PostSummary/PostDetail 공통)

살아 있는 신청(`PENDING` + `ACCEPTED`)만 센다. 취소·거절된 신청은 제외.

## 6. 매칭 신청

### RequestResponse

```json
{ "id": 7, "postId": 12, "postTitle": "토요일 아침 풋살 상대 구합니다",
  "postStatus": "OPEN", "matchAt": "2026-08-30T07:00:00+09:00",
  "applicantTeam": "<TeamSummary>", "message": "저희도 강서구라 가깝습니다!",
  "status": "PENDING", "contact": null, "payment": null, "depositPaid": false,
  "createdAt": "2026-08-23T11:00:00+09:00" }
```

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

## 7. v1 범위 밖 (구현하지 말 것)

실시간 채팅, 푸시 알림, 이미지 업로드, 소셜 로그인, refresh token, 경기 결과/전적 기록, 지도, 리뷰·평점
