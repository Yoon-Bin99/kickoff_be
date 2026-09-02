# Kickoff API 계약 v1 (현재 v1.24.0)

조기축구 팀 매칭 앱. 이 문서가 FE/BE 사이의 **단일 진실 공급원**이다.
변경이 필요하면 임의로 고치지 말고 supervisor에게 보고할 것.

> v1.24.0 (2026-09-02): 계정 편의 3종 (사용자 지시) —
> ① §3-5 신설: **이메일(아이디) 찾기** — §3-2 전화 인증의 `existingAccount`를 그대로
> 쓰는 FE 전용 흐름. BE 변경 없음.
> ② §3-3 개정: 재설정 요청 전 FE가 `availability`로 **사전 존재 확인** — 없는 이메일은
> 발송 전에 "가입된 계정이 없습니다"로 안내(사용자 결정. 이메일 존재는 availability로
> 이미 조회 가능해 보안 저하 없음). 서버의 항상-204는 유지.
> ③ `PATCH /api/users/me/password` 신설: 로그인 상태 비밀번호 변경(현재 비밀번호
> 재확인) — §9에서 제거.
> ④ §0 승격: "인증 불필요 경로의 401 = 아직 배포 안 된 서버" 해석 규칙(v1.23.0 FE
> 판단을 공통 규약으로).
>
> v1.23.0 (2026-09-02): 비밀번호 재설정 + 회원 탈퇴 —
> ① §3-3 신설: 이메일 인증코드로 비밀번호 재설정. `POST /api/auth/password-reset`
> (발송, **항상 204** — 계정 존재 비노출)·`.../confirm`(코드+새 비밀번호 → 204).
> §3-2 문자 인증 패턴 재사용(코드 6자리·레이트리밋·5회 실패 무효), `EMAIL_ENABLED` env.
> ② §3-4 신설: `DELETE /api/users/me` — 앱 내 회원 탈퇴(§9에서 제거). 이메일 계정은
> 비밀번호 재확인(`PASSWORD_MISMATCH`), 소유 팀에 예정된 매칭 있으면 409
> `ACTIVE_MATCH_EXISTS`(먼저 매칭 취소). 소유 팀·개인정보 삭제, 전화번호 유니크 해제.
>
> v1.22.0 (2026-09-01): 고객센터 문의 채팅 — §7-1 신설: 사용자↔운영자 1:1 상시 채팅
> (건의·문의·신고 접수 창구). 매칭 채팅 §6-1 구조 재사용(폴링·after 커서), 만료 없음.
> 운영자는 env로 지정된 계정, 문의함(방 목록)과 답장은 운영자만. 푸시 `SUPPORT_MESSAGE`.
> **3단 응대**: FAQ 퀵버튼(`GET /api/support/faq`) → AI 상담사(Claude API, 미해결 시)
> → 운영자 연결(`POST /api/support/chat/escalate`). 메시지 발신자는 `sender`
> (USER/AI/OPERATOR). 챗봇 UI는 고객센터 화면 안에서만 — 플로팅 버튼 없음(사용자 결정).
>
> v1.21.0 (2026-08-31): 약관·개인정보 동의 — 이메일 signup에 `termsAgreed: true` 필수
> (아니면 400). `User.termsAgreedAt` 저장(응답 비노출). 약관·개인정보처리방침은 BE가
> 정적 페이지(`GET /terms`, `GET /privacy` — 인증 불필요 HTML)로 서빙, FE 가입 화면에
> 동의 체크+문서 열람. 소셜 가입자의 앱 자체 약관 동의는 §9 범위 밖(v2).
>
> v1.20.0 (2026-08-31): 매칭 취소 — §6-2 신설: 수락된 매칭을 경기 전까지 양 팀 어느
> 쪽이든 취소할 수 있다. `POST /api/requests/{id}/cancel-match`, `RequestStatus`에
> `MATCH_CANCELED` 추가, 글은 OPEN 복구, 푸시 `MATCH_CANCELED`, 에러
> `MATCH_CANCEL_EXPIRED`. 취소되면 연락처·채팅이 닫힌다.
>
> v1.19.0 (2026-08-30): **11대11 전용 전환** — 서비스가 11대11 축구만 취급하기로
> 확정(사용자 결정). `FieldType` 열거형과 모집글의 `fieldType` 필드·필터를 **폐지**.
> 요청에 fieldType이 와도 무시(에러 아님 — 구버전 호환), 응답에는 싣지 않는다.
>
> v1.18.0 (2026-08-30): 홈 날짜·시간대 필터 — `GET /api/posts`에 `dates`(YYYY-MM-DD
> 콤마 구분)·`times`(시간대 enum 콤마 구분) 쿼리. 각각 다중 선택 OR, 서로는 AND,
> KST 기준 matchAt 판정. `TimeSlot` 열거형 신설. FE 홈에 날짜·시간대 다중 선택 UI.
>
> v1.17.0 (2026-08-30): 전화번호 기반 1인 1계정 — ① confirm 응답에 `existingAccount`
> (그 번호로 이미 가입된 계정의 가입 방식·마스킹 이메일. **번호 소유를 인증한 뒤에만**
> 노출) ② 인증 강제 상태에서 소셜 가입자의 전화번호 등록을 FE가 게이트로 강제.
> 진짜 계정 통합(로그인 수단 연결·계정 병합)은 §9 범위 밖 — v2.
>
> v1.16.1 (2026-08-30): `GET /api/auth/signup-policy` 신설 — FE의 인증 UI 강제를
> "엔드포인트 존재"가 아니라 **서버의 실제 강제 여부**에 맞춘다. 존재 기반 감지 그대로면
> SMS 실발송 전에 새 앱의 가입이 통째로 막히는 조합이 생긴다 (FE 발견). §3-2의
> "구버전 클라이언트 감지" 규칙을 이걸로 대체.
>
> v1.16.0 (2026-08-29): 가입 폼 강화 — ① 닉네임·전화번호도 유니크 (이메일은 기존부터).
> `GET /api/auth/availability`로 사전 중복 확인, 에러 `NICKNAME_ALREADY_EXISTS`·
> `PHONE_ALREADY_EXISTS` 신설, signup·PATCH /users/me에 적용 ② 비밀번호 규칙 강화:
> 8~64자 + 영문·숫자·특수문자 각 1개 이상 ③ FE 이메일 분리 입력(도메인 드롭다운+직접입력).
>
> v1.15.0 (2026-08-28): 전화번호 문자 인증 — §3-2 신설: 가입·전화번호 변경 시 SMS
> 인증번호로 번호 소유를 확인한다. `POST /api/auth/phone/verifications`(발송),
> `.../confirm`(검증 → verificationToken), signup·PATCH /users/me의 phone에
> verificationToken 요구. 에러 5종, `SMS_ENABLED` env. 실명 본인인증(PASS 등
> 본인확인기관)은 범위 밖 — 사업자 등록 후 v2.
>
> v1.14.0-용어 (2026-08-28): 팀 소유자(OWNER)의 사용자 노출 호칭을 "주장" → **"감독"**
> 으로 통일 (사용자 결정). API 필드·역할 enum은 변경 없음 — 화면·푸시 문구만.
>
> v1.14.0 (2026-08-28): 팀 검색 개선 — `GET /api/teams`의 `keyword` 매칭을 정규화
> (공백 제거·대소문자 무시) 부분 일치로, 정렬을 정확도 순(정규화 전방일치 → 포함,
> 그룹 내 생성일 DESC)으로 개정. FE 팀 찾기에 지역(시/도) 필터 노출(기존 `region`
> 파라미터 사용 — API 변경 없음). 오타 유사도 검색(trigram 등)은 범위 밖.
>
> v1.13.0 (2026-08-28): 채팅 탭·나가기 — `GET /api/users/me/chats`(내 채팅방 목록),
> `POST /api/requests/{id}/chat/leave`(나가기 — 영구 숨김·상대 방에 SYSTEM 메시지·
> 나간 시점 이전 내역 비공개·푸시 중단, 재전송하면 복귀), `ChatMessage.type`
> (TEXT/SYSTEM) 추가. FE 하단 탭 5개(홈/팀/매칭/채팅/마이).
>
> v1.12.1 (2026-08-28): `METHOD_NOT_ALLOWED`(405) 신설 — 존재하는 경로를 틀린 HTTP
> 메서드로 부르면 500이 아니라 405. 경로 오타의 404(`NOT_FOUND`)와 같은 취지 —
> 클라이언트 실수를 서버 장애로 오인하지 않게 한다 (FE가 accept를 PATCH로 불러
> 500을 받고 서버 회귀로 오인한 실사례에서 접수).
>
> v1.12.0 (2026-08-28): 매칭 채팅 — §6-1 신설: 수락된 매칭의 두 팀 감독(OWNER) 간
> 1:1 채팅. `matchAt`까지 전송 가능, 이후 읽기 전용 보존. 폴링 기반(`after` 커서),
> 푸시 `CHAT_MESSAGE` 추가, 에러 `CHAT_CLOSED`. §9의 "실시간 채팅"을 세분화 —
> WebSocket·읽음 표시·안읽음 배지·첨부는 여전히 범위 밖.
>
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
| `CHAT_CLOSED` | 409 | 매치 일시가 지난 채팅방에 메시지 전송 시도 |
| `NOT_FOUND` | 404 | 매핑되지 않은 경로/리소스 (FE는 "요청한 페이지를 찾을 수 없습니다") |
| `METHOD_NOT_ALLOWED` | 405 | 존재하는 경로를 지원하지 않는 HTTP 메서드로 호출 (v1.12.1) |
| `PHONE_NOT_VERIFIED` | 400 | verificationToken 없음·무효·전화번호 불일치 (v1.15.0) |
| `NICKNAME_ALREADY_EXISTS` | 409 | 닉네임 중복 (v1.16.0) |
| `MATCH_CANCEL_EXPIRED` | 409 | 경기 시각이 지난 매칭의 취소 시도 (v1.20.0) |
| `PHONE_ALREADY_EXISTS` | 409 | 전화번호 중복 (v1.16.0) |
| `VERIFICATION_CODE_MISMATCH` | 400 | 인증번호 불일치 (v1.15.0) |
| `VERIFICATION_EXPIRED` | 400 | 인증번호·토큰 만료 (v1.15.0) |
| `VERIFICATION_RATE_LIMITED` | 429 | 인증번호 발송 한도 초과 (v1.15.0) |
| `SMS_SEND_FAILED` | 502 | 문자 발송 외부 API 실패 (v1.15.0) |
| `SUPPORT_RATE_LIMITED` | 429 | 고객센터 AI 호출 한도 초과 (v1.22.0) |
| `PASSWORD_MISMATCH` | 400 | 회원 탈퇴 시 비밀번호 재확인 불일치 (v1.23.0) |
| `ACTIVE_MATCH_EXISTS` | 409 | 예정된 매칭이 있는 팀의 소유자가 탈퇴 시도 (v1.23.0) |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 (FE는 "잠시 후 다시 시도해주세요"로 표시) |

참고 사항:
- `timestamp`는 밀리초 정밀도까지만 내려간다 (`…16.384+09:00`)
- 응답의 nullable 규칙: 요청에서 optional인 필드는 응답에서도 `null`일 수 있다.
  `costPerTeam`(null이면 FE는 "협의" 표시), `homeGround`, `introduction`, `preferredSkillLevel`, `logoUrl`이 해당.
  BE는 optional 필드를 빈 문자열이 아니라 `null`로 내려보낸다.
- 만료/위조 토큰으로 **인증 불필요** 엔드포인트를 호출하면 401이 아니라 비로그인으로 간주하고 정상 응답한다. 401은 인증 필요 엔드포인트에서만 발생
- 존재하지 않는 경로는 Security 기본 동작으로 404가 아닌 401이 나갈 수 있다 (FE는 경로 오타를 401로 오인하지 말 것)
- 따라서 (v1.24.0 승격) **인증 불필요 엔드포인트에서 401을 받으면 "그 API가 아직 없는
  서버"로 해석한다** — "로그인이 필요합니다"가 아니라 "지금은 이 기능을 쓸 수 없어요"
  류로 표시. 배포 시차(FE가 먼저 나간 기간)에 새 기능이 열리는 표준 강등 경로다
  (§3-2 signup-policy·§3-3 재설정에서 확립).
  원리로 기억할 것: **401은 필터 단계, 404·405는 라우팅 단계**다. 유효한 토큰으로
  인증 필요 경로를 부르면 필터를 통과한 뒤 라우팅에서 떨어지므로, 미배포 신호가
  401이 아니라 404(경로 없음)·405(메서드 없음)로 온다. 새 API를 낼 때 BE는 "미배포
  서버가 무엇을 답하는지"를 함께 알린다.

## 1. 열거형

FE는 아래 값을 그대로 서버에 보내고, 화면 표시는 FE가 한글로 매핑한다.

```
SkillLevel    BEGINNER | AMATEUR | INTERMEDIATE | ADVANCED
              입문      초급       중급           상급

AgeGroup      TEENS | TWENTIES | THIRTIES | FORTIES | FIFTIES_PLUS | MIXED
              10대    20대       30대       40대      50대 이상       혼합

FieldType     (v1.19.0 폐지 — 11대11 전용. 값 자체가 사라짐)
              풋살     6인제      9인제      11인제

PostStatus    OPEN | MATCHED | CLOSED
              모집중  매칭완료   마감

RequestStatus PENDING | ACCEPTED | REJECTED | CANCELED | MATCH_CANCELED (v1.20.0 — 수락 후 취소)
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

- `email` 이메일 형식 필수 / `password` **8~64자 + 영문·숫자·특수문자 각 1개 이상**
  (v1.16.0 — 위반은 400 `VALIDATION_FAILED`, fieldErrors에 규칙 안내. 기존 계정의
  로그인·비밀번호는 소급하지 않는다) / `nickname` 2~20자 필수 / `phone` `010-0000-0000` 형식 필수
- `activityRegion` optional (v1.6.0, 최대 20자) — FE 가입 화면은 선택을 권하지만 건너뛸 수 있다
- **약관 동의 (v1.21.0)**: 요청에 `termsAgreed: true` 필수 — 없거나 false면 400
  `VALIDATION_FAILED` (fieldErrors에 안내). 서버는 동의 시각을 저장한다(응답 비노출).
  약관 문서는 `GET /terms`, 개인정보처리방침은 `GET /privacy` (인증 불필요, HTML —
  share-card와 같은 정적 서빙, API 표면 밖이지만 FE가 링크하므로 여기 적는다).
  소셜 가입(OAuth 최초 로그인)의 앱 자체 동의는 v1 범위 밖 (§9).
- **유니크 규칙 (v1.16.0)**: `email`(기존), `nickname`, `phone` 모두 중복 불가 —
  409 `EMAIL_ALREADY_EXISTS` / `NICKNAME_ALREADY_EXISTS` / `PHONE_ALREADY_EXISTS`.
  `PATCH /api/users/me`의 nickname·phone 변경에도 같은 규칙(자기 자신의 기존 값은 허용).
  phone은 null(소셜 미등록)을 유니크 대상에서 제외한다

### GET /api/auth/availability — 인증 불필요 (v1.16.0)

가입 폼의 **사전 중복 확인**. 쿼리로 `email`·`nickname`·`phone` 중 1개 이상.

`GET /api/auth/availability?email=kim@example.com&nickname=김감독` → 200:

```json
{ "email": false, "nickname": true }
```

- **요청한 키만** 응답에 담긴다. `true` = 사용 가능
- email·**nickname 모두 대소문자 무시 비교** — signup·PATCH의 409 판정도 동일하다
  (availability와 최종 판정이 어긋나면 안 된다). nickname을 무시 비교로 두는 이유:
  "FCseoul"/"FCSeoul" 같은 겉모습이 같은 이름의 공존은 사칭·혼동 여지만 만든다.
  저장은 입력한 표기 그대로다. phone은 `010-0000-0000` 형식 그대로 비교
- 형식이 틀린 값은 400 `VALIDATION_FAILED`
- 이건 UX 보조다 — 확인과 제출 사이의 경합은 최종 제출의 409가 진실이고, FE는 409를
  받으면 해당 필드로 포커스를 돌린다
- 인증이 없는 API라 **"자기 자신의 값은 허용" 규칙은 여기 적용되지 않는다** (그건
  signup·PATCH의 409 판정 이야기다). 로그인 사용자가 자기 값을 물으면 `false`가
  오므로, FE는 자기 기존 값(대소문자 무시 비교)은 아예 묻지 않는다

201 응답

```json
{ "accessToken": "eyJhbGci...", "user": { "id": 1, "email": "kim@example.com", "nickname": "김주장",
  "phone": "010-1234-5678", "hasTeam": false, "teamId": null, "authProviders": [] } }
```

### POST /api/auth/login — 인증 불필요

요청 `{ "email": "...", "password": "..." }` → 200, signup과 동일한 형태
- v1.16.0: **이메일 조회도 대소문자 무시** — 중복 판정과 같은 기준이어야
  "가입은 같은 주소 취급, 로그인은 다른 주소 취급"이 되지 않는다. 기존 데이터에
  케이스만 다른 중복이 이론상 남아 있으면 정확 일치를 우선한다

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

## 3-2. 전화번호 문자 인증 (v1.15.0)

전화번호가 **본인 소유인지** SMS 인증번호로 확인한다. 실명 확인이 아니다 — 실명
본인인증(PASS·본인확인기관)은 범위 밖(§9, 사업자 등록 후 v2).

인증이 필요한 자리: ① 이메일 가입의 `phone` ② `PATCH /api/users/me`로 phone을
**넣거나 바꿀 때** (소셜 가입자의 최초 등록 포함). phone을 건드리지 않는 PATCH는
무관하다.

### POST /api/auth/phone/verifications — 인증 불필요

```json
{ "phone": "010-1234-5678" }
```

- 6자리 숫자 인증번호를 SMS로 발송한다. **유효 3분.** 204 응답 (본문 없음 —
  코드는 절대 응답에 싣지 않는다)
- 같은 번호로 재요청하면 이전 코드는 무효가 되고 새 코드가 발송된다
- 레이트리밋: 같은 번호 기준 **1분에 1회, 1시간에 5회** — 초과 시 429
  `VERIFICATION_RATE_LIMITED`. 발송 실패는 502 `SMS_SEND_FAILED`
- `phone` 형식은 signup과 동일 검증 (`010-0000-0000`)

### POST /api/auth/phone/verifications/confirm — 인증 불필요

```json
{ "phone": "010-1234-5678", "code": "482913" }
```

200 응답:

```json
{ "verificationToken": "..." }
```

- `verificationToken`은 **10분 유효, 1회용**이고 그 전화번호에 묶인다
- 불일치 400 `VERIFICATION_CODE_MISMATCH` — **5회 연속 실패 시 코드 무효** (재발송부터
  다시). 만료·코드 없음 400 `VERIFICATION_EXPIRED`

**existingAccount (v1.17.0)**: 그 번호로 **이미 가입된 계정이 있으면** 200 응답에 함께
담는다 (없으면 `null` — 키는 항상 존재):

```json
{ "verificationToken": "...",
  "existingAccount": { "method": "EMAIL", "maskedEmail": "ki***@ex*****.com" } }
```

- `method`: `"EMAIL" | "KAKAO" | "NAVER"` — email이 있으면 EMAIL, 아니면 첫 provider
- `maskedEmail`: EMAIL일 때만. 마스킹: 로컬파트 앞 2자+`***`, 도메인 앞 2자+`*`(TLD는
  그대로). 2자 미만이면 첫 자만 남긴다
- **번호 소유를 코드로 증명한 사람에게만** 알려준다 — 남의 번호를 넣어 가입 여부·수단을
  캐는 것은 발송 단계(코드를 모름)에서 막힌다. availability의 phone `false`는 "사용
  불가"만 알려줄 뿐 수단은 노출하지 않는다 (기존 그대로)
- FE: `existingAccount`가 오면 가입을 진행시키지 않고(어차피 409) "이미 가입된 계정이
  있어요 — {방식}으로 로그인해 주세요"로 로그인을 유도한다. 이것이 **전화번호 기반
  1인 1계정**의 안내 경로다 (차단 자체는 v1.16.0의 phone 유니크가 담당)
- **로그인된 화면**(전화번호 변경 등)에서 `existingAccount`가 오면 "로그인해 주세요"가
  아니라 **로그아웃이 필요함**을 안내한다 — 지금 계정과 다른 계정의 번호라는 사실,
  [로그아웃하고 그 계정으로 로그인] 버튼, 그리고 "다른 번호를 쓰려면 번호를 바꿔라"의
  **두 출구**를 함께 제시한다 (그 번호로는 몇 번을 시도해도 409라 제3의 길이 없다).
  마스킹 문자열은 서버 값을 그대로 쓴다 — FE가 재마스킹하지 않는다

**소셜 가입자 게이트 (v1.17.0)**: `phoneVerificationRequired`가 true일 때, 소셜
로그인 후 `phone: null`인 사용자는 FE가 **전화번호 등록(인증 포함)을 마칠 때까지 주요
화면 진입을 막는다** (등록은 기존 PATCH /users/me 경로). 서버 강제(미등록 사용자의
API 차단)는 범위 밖 — v1은 화면 게이트만. 알려진 한계: 소셜 계정은 OAuth 첫 로그인
시점에 이미 생성되므로, 번호가 기존 계정 것이면 방금 만든 소셜 계정이 빈 채로 남는다
— 병합·정리는 계정 통합(v2)에서 다룬다.

### 기존 API 변경

- **`POST /api/auth/signup`**: 요청에 `verificationToken` 필수. 없거나 무효거나
  `phone`과 안 맞으면 400 `PHONE_NOT_VERIFIED`
- **`PATCH /api/users/me`**: `phone`을 포함하는 요청은 `verificationToken` 필수 (같은
  규칙). phone이 없는 요청은 기존 그대로
- 소셜 가입(OAuth)은 가입 시점에 phone이 없으므로 변경 없음 — 이후 등록이 PATCH
  규칙을 탄다
- **강제 스위치**: 위 두 API의 토큰 요구는 `PHONE_VERIFICATION_REQUIRED`(기본 false)가
  true일 때만 적용된다. false면 토큰이 없어도 예전처럼 통과하고, **있으면 검증한다**
  (무효 토큰은 false에서도 400 — 조용히 삼키지 않는다). 인증 API 자체(§3-2 두
  엔드포인트)는 스위치와 무관하게 항상 동작한다.
  이유: 구버전 앱(preview APK)이 토큰 없이 가입하는 기간이 있다 — 서버를 먼저
  배포해도 구버전이 깨지지 않고, 새 클라이언트가 충분히 퍼진 뒤 스위치를 켠다.

### GET /api/auth/signup-policy — 인증 불필요 (v1.16.1)

200 응답:

```json
{ "phoneVerificationRequired": false }
```

- `PHONE_VERIFICATION_REQUIRED`의 현재값을 그대로 알린다. permitAll, 무헤더 호출.
- **FE의 인증 UI 판정은 이 값만 따른다** (v1.16.0까지의 "발송 엔드포인트 존재 감지"
  규칙은 폐기): `true`면 가입·전화번호 변경에서 인증을 강제하고, `false`면 인증 UI를
  감추고 토큰 없이 제출한다. 이 경로가 없는 옛 서버(401)는 `false`로 간주한다.
- 이유: 존재 기반 감지는 "엔드포인트는 있는데 SMS 실발송이 안 되는" 전환기에 새 앱의
  가입을 통째로 막는다. 서버가 강제하지 않는 동안 화면도 강제하면 안 된다 — 최종
  진실은 어차피 signup의 400 `PHONE_NOT_VERIFIED`다.

### 환경변수 (BE)

`SMS_ENABLED` (기본 false — dev에서는 실제 발송을 생략하고 코드를 서버 로그로만
남긴다. 운영 true), `PHONE_VERIFICATION_REQUIRED` (기본 false — 롤아웃 순서: BE 배포
→ FE 배포 → preview 재빌드·배포 → true로 전환), SMS 제공자 API 키 (제공자는 BE
재량 — 쿨SMS/솔라피 권장). 발송 문구: "[킥오프] 인증번호 {코드}를 입력해 주세요."

## 3-3. 비밀번호 재설정 (v1.23.0)

이메일 계정 전용 — 소셜 계정은 비밀번호가 없다. §3-2 문자 인증과 같은 패턴
(6자리 코드·재요청 시 이전 코드 무효·5회 연속 실패 무효)을 이메일 채널로 옮긴 것.

### POST /api/auth/password-reset — 인증 불필요

```json
{ "email": "kim@example.com" }
```

- **항상 204** — 계정이 없어도, 소셜 계정(email null)이어도, 발송이 실패해도 204다.
  계정 존재 여부를 이 API로 캘 수 없어야 한다. 실제 발송은 **비동기**로 하고 실패는
  서버 로그로만 남긴다 (502를 주면 "존재하는 계정"이 노출된다)
- 존재하는 이메일 계정일 때만: 6자리 숫자 코드를 그 주소로 발송. **유효 10분**
  (이메일은 문자보다 지연이 커서 3분이 아니라 10분)
- 레이트리밋: **입력된 이메일 문자열 기준** 1분 1회·1시간 5회 — 계정 존재와 무관하게
  적용해야 한도 응답(429 `VERIFICATION_RATE_LIMITED`)이 존재 신호가 되지 않는다
- `email` 형식 오류만 400 `VALIDATION_FAILED`
- 발송 문구: 제목 "[킥오프] 비밀번호 재설정 인증번호", 본문에 코드와 유효시간 안내

### POST /api/auth/password-reset/confirm — 인증 불필요

```json
{ "email": "kim@example.com", "code": "482913", "newPassword": "newPass1!" }
```

- 성공 204. 비밀번호가 교체되고 **그 계정의 refresh token 전부 폐기** (모든 기기
  재로그인 — 탈취 대응의 핵심)
- 코드 불일치 400 `VERIFICATION_CODE_MISMATCH` (5회 연속 실패 시 코드 무효 — 재발송부터).
  만료·코드 없음·계정 없음은 전부 400 `VERIFICATION_EXPIRED`로 동일 응답 (여기서도
  존재 비노출)
- `newPassword`는 signup과 같은 규칙 (8~64자 + 영문·숫자·특수문자 각 1+) — 위반 400
  `VALIDATION_FAILED`

### 환경변수 (BE)

`EMAIL_ENABLED` (기본 false — dev에서는 발송을 생략하고 코드를 서버 로그로만 남긴다.
운영 true), SMTP 설정(Gmail SMTP 권장 — 호스트·포트·계정·**앱 비밀번호**. 속성 이름은
BE 재량, 값은 env로만·커밋 금지). 발신 주소는 운영자 지메일.

### FE 규칙

- 로그인 화면에 "비밀번호를 잊으셨나요?" 링크 → ① 이메일 입력·발송 ② 코드+새 비밀번호
  입력(한 화면, §3-2 인증 UI 관례 재사용) ③ 성공 시 로그인 화면 복귀 + "비밀번호가
  변경됐어요. 다시 로그인해 주세요"
- **사전 존재 확인 (v1.24.0, 사용자 결정)**: 발송을 부르기 전에
  `GET /api/auth/availability?email=`로 확인해서, **미가입 이메일이면 발송 없이 "해당
  이메일로 가입된 계정이 없습니다"를 표시**한다 (소셜 계정은 email이 null이라 자연히
  같은 안내 — 어차피 재설정할 비밀번호가 없다). 가입된 이메일이면 발송하고 안내는
  "인증번호를 보냈어요"로 확정형을 쓴다.
  근거: 이메일 등록 여부는 가입 중복확인(availability)으로 이미 조회 가능한 정보라,
  여기서 감춰도 보안 이득이 없고 UX만 나빴다. **서버의 항상-204·레이트리밋·확인
  단계 동일 응답은 그대로 유지**한다 — availability를 우회해 직접 쏘는 클라이언트에게
  추가 정보를 주지 않기 위해서다
- 재발송 버튼은 §3-2와 같은 60초 쿨다운

### PATCH /api/users/me/password — 인증 필요 (v1.24.0)

로그인 상태의 비밀번호 변경 — 재설정(위)과 별개 경로. 이메일 계정 전용.

```json
{ "currentPassword": "...", "newPassword": "..." }
```

- 성공 204. `currentPassword` 불일치 400 `PASSWORD_MISMATCH` (§3-4 탈퇴와 같은 코드·
  같은 이유 — 401 금지)
- `newPassword`는 signup 규칙 (8~64자 + 3종). 위반 400 `VALIDATION_FAILED`
- 소셜 계정(비밀번호 없음)의 호출은 400 `VALIDATION_FAILED` — FE가 메뉴 자체를
  이메일 계정에게만 노출하므로 정상 경로에서는 생기지 않는다
- **현재 세션은 유지**한다 (refresh 폐기 없음 — 단일 기기 정책이라 "다른 기기 로그아웃"
  개념이 없고, 본인이 방금 현재 비밀번호를 댄 상황이다. 재설정 §3-3의 전폐기와 다른
  이유: 그쪽은 탈취 대응, 이쪽은 일상 변경)
- FE: 계정 관리 화면에 "비밀번호 변경" (이메일 계정만 노출) — 현재/새 비밀번호 입력,
  성공 시 "비밀번호가 변경됐어요" 후 화면 유지

## 3-4. 회원 탈퇴 (v1.23.0)

§9의 "회원 탈퇴 API(문의로 수동 처리)"를 v1로 승격 — 개인정보처리방침 §3·§6이 약속한
삭제권의 앱 내 실행 경로. 마이 탭 깊숙이(설정/계정 관리) 배치한다.

### DELETE /api/users/me — 인증 필요

```json
{ "password": "..." }
```

- **이메일 계정**: `password` 필수 — 불일치 400 `PASSWORD_MISMATCH` (401을 쓰지
  않는 이유: FE 인터셉터가 세션 만료로 오인해 refresh를 타면 안 된다).
  **소셜 계정**: 비밀번호가 없으므로 body 없이(또는 password 무시) 호출 — FE의 확인
  다이얼로그가 유일한 관문
- **차단 조건**: 소유 팀에 **경기 예정(matchAt 미래)인 ACCEPTED 매칭**이 있으면 409
  `ACTIVE_MATCH_EXISTS` — 상대 팀의 확정 매칭을 소리 없이 증발시키지 않는다. FE는
  "예정된 매칭을 먼저 취소해 주세요"로 매칭 탭 유도. 지난 매칭·PENDING 신청은 차단
  사유가 아니다
- 성공 204. 처리 내용:
  - **계정·개인정보 즉시 삭제** — 이메일·전화번호·닉네임·소셜 연동·push token·refresh
    token 전부. **전화번호 유니크가 풀려 같은 번호로 재가입 가능**. 전화 인증 이력은
    부정 이용 방지 목적 보관(방침 §3-2와 일치)
  - **소유 팀이 있으면 팀 전체 삭제** — 명단·기록·가입 신청·모집글·그 글의 PENDING
    신청·팀이 주고받은 리뷰까지 (상대 팀 평점은 재계산된다). 지난 ACCEPTED 매칭과
    채팅도 삭제 — 단 **상대 팀이 자기 페이지에 가진 수동 전적(TeamRecord)은 보존**하고
    `requestId` 연결만 끊는다(null). 그 외 FK 정리 세부는 BE 재량 — 원칙은 "탈퇴가
    500으로 막히는 경로가 없을 것, 상대 팀 화면이 깨지지 않을 것"
  - 소속 팀(MEMBER)에서는 탈퇴 처리(명단 항목 삭제), 내 PENDING 가입·매칭 신청 삭제,
    고객센터 문의방 삭제
  - 탈퇴로 인한 푸시는 보내지 않는다 (v1 — 매칭 차단 조건이 있어 상대에게 급한 통지가
    필요한 경우가 없다)
- 멱등 아님 — 이미 삭제된 토큰의 재호출은 401 `UNAUTHORIZED` (자연 동작)

### FE 규칙

- 마이 탭 → "계정 관리"(또는 설정) → 맨 아래 "회원 탈퇴" (회색 톤 — 빨간 강조로
  유도하지 않되 찾을 수는 있게)
- 이중 확인: ① 삭제되는 것들 안내(팀·글·기록이 함께 삭제, 복구 불가) + 확인
  다이얼로그 ② 이메일 계정은 비밀번호 입력 요구
- 성공 시 로컬 토큰·push token 정리 → 로그인 화면 + "탈퇴가 완료됐어요" 안내
- `ACTIVE_MATCH_EXISTS`는 "예정된 매칭이 있어요 — 매칭을 먼저 취소해 주세요"로 표시

### 문서 연동

시행 시 개인정보처리방침 §3("탈퇴는 문의처로 요청")·FAQ 8번("탈퇴 기능 준비 중")을
"앱 내 마이 탭에서 직접 탈퇴"로 갱신한다 — 원본은 supervisor가 고치고 BE가 서빙
사본·FAQ 리소스에 반영한다.

## 3-5. 이메일(아이디) 찾기 (v1.24.0)

**FE 전용 흐름 — BE 변경 없음.** §3-2의 전화 인증 API와 `existingAccount` 응답을
그대로 쓴다: 번호 소유를 증명한 사람에게 그 번호의 계정 정보를 알려주는 것은 §3-2가
이미 허용·구현한 노출이다.

- 로그인 화면에 "이메일 찾기" 링크 → ① 전화번호 입력 → 발송(`POST
  /api/auth/phone/verifications`) ② 코드 확인(`.../confirm`) ③ 응답의
  `existingAccount`로 분기:
  - `method: "EMAIL"` → "**{maskedEmail}** 로 가입돼 있어요" + [로그인하러 가기].
    마스킹은 서버 값 그대로 (§3-2 규칙 — FE 재마스킹 금지, 전체 공개는 하지 않는다)
  - `KAKAO`/`NAVER` → "카카오/네이버로 가입돼 있어요" + 해당 소셜 로그인 버튼
  - `null` → "이 번호로 가입된 계정이 없습니다" + [회원가입]
- 발급된 `verificationToken`은 쓰지 않고 버린다 (1회용·10분이라 무해)
- 마스킹으로도 이메일을 못 알아보는 사용자는 고객센터 문의로 — 전체 공개 응답은 v2
  후보로만 남긴다

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

### GET /api/teams — 인증 불필요 (v1.11.0, 팀 찾기 / v1.14.0 매칭·정렬 개정)

쿼리 (전부 optional): `keyword`(팀 이름 — 아래 정규화 매칭), `region`(부분 일치),
`page`(기본 0), `size`(기본 20, 최대 50).
200 → `PageResponse<TeamSummary>`.

- **keyword 매칭 (v1.14.0)**: 검색어와 팀 이름 양쪽에서 **공백을 제거하고 대소문자를
  무시**한 뒤 부분 일치. "인천스트라이커즈"·"fc서울"처럼 띄어쓰기·케이스가 달라도
  찾아진다. 검색어 속 `%`·`_` 같은 와일드카드 문자는 **리터럴로 취급**한다 (이스케이프 —
  `region` 파라미터도 동일). 오타 유사도(편집거리·trigram)는 범위 밖 (§9).
- **정렬 (v1.14.0)**: `keyword`가 있으면 정확도 순 — ① 정규화한 팀 이름이 정규화한
  검색어로 **시작**하는 팀 ② 나머지 포함 팀. 각 그룹 안에서는 생성일 DESC (동률 id
  DESC). `keyword`가 없으면 기존대로 생성일 DESC (동률 id DESC).

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

팀당 최대 **30명**. 명단은 실제 앱 계정이 아니라 **감독(OWNER)이 입력하는 정보**다.

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

감독(OWNER)이 **수동 입력**하는 전적. 매칭 시스템과의 자동 연동은 범위 밖(v2).

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
"신청됨+취소", 둘 다 null이면 "가입 신청".
`myJoinStatus`는 **`GET /api/teams/{teamId}` 응답에서만** 채워진다 — `PostDetail.team`
등 다른 응답에 내포된 `TeamResponse`에서는 항상 `null` (그 자리에 가입 UI가 없어
추가 조회 비용을 치를 이유가 없다). MEMBER는 조회·소속 표시만 갖는다 — 팀 페이지 수정 권한 없음,
매칭·리뷰 권한 없음 (v1.9 권한표 유지).

**명단-계정 통합**: `TeamMember`에 `userId`(nullable)가 추가된다. 가입 승인 시 명단에
항목이 자동 생성되고(`name` = 가입자 닉네임, `userId` 연결, position·backNumber null —
감독·관리자가 나중에 채움), 수기 명단은 `userId: null` 그대로다. **계정 연결 항목의
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
| ~~`fieldType`~~ | — | v1.19.0 폐지 (보내면 무시) |
| `skillLevel` | SkillLevel | 모집글이 원하는 상대 수준 |
| `status` | PostStatus | 기본값 `OPEN` |
| `keyword` | string | 제목+내용 부분 일치 |
| `dates` | string | v1.18.0 — `YYYY-MM-DD`를 콤마로 구분한 다중 날짜 (아래 규칙) |
| `times` | string | v1.18.0 — `TimeSlot`을 콤마로 구분한 다중 시간대 (아래 규칙) |
| `page` | number | 기본 0 |
| `size` | number | 기본 20, 최대 50 |

**dates 규칙 (v1.18.0)**: 나열된 날짜 중 **어느 하나**(OR)에 `matchAt`이 속하는 글만
반환한다 — 날짜 판정은 **KST(+09:00) 기준 그 날의 00:00~24:00**. 다른 필터(region 등)
와는 AND. 최대 **14개**(초과 400 `VALIDATION_FAILED`), 형식이 틀린 항목이 하나라도
있으면 400. 과거 날짜도 허용한다(별도 취급 없음 — status 필터가 걸러준다).

**times 규칙 (v1.18.0)**: `TimeSlot` 열거형 — matchAt의 KST 시각 기준.

| 값 | 시간대 | FE 라벨 |
|---|---|---|
| `DAWN` | 05:00 ~ 08:00 | 새벽 |
| `MORNING` | 08:00 ~ 12:00 | 오전 |
| `AFTERNOON` | 12:00 ~ 18:00 | 오후 |
| `EVENING` | 18:00 ~ 22:00 | 저녁 |
| `NIGHT` | 22:00 ~ 05:00 | 심야 (자정을 넘는 유일한 구간) |

- 경계는 **시작 포함, 끝 제외** (08:00 정각은 MORNING)
- 나열한 시간대끼리 OR, `dates` 및 다른 필터와는 AND (예: `dates=토,일 & times=DAWN`
  = "주말 새벽 경기")
- 모르는 값은 400 `VALIDATION_FAILED`

정렬은 `matchAt` 오름차순(가까운 경기 먼저) 고정.

**지난 경기 규칙**: `matchAt`이 현재보다 과거인 글은 이 목록에서 **제외**한다
(status 필터와 무관 — 지난 경기는 매칭 대상이 아니다). `GET /api/posts/me`는
과거 글도 전부 보여준다 (내 기록). 지난 경기의 상세 조회는 가능하지만
신청은 409 `POST_NOT_OPEN`으로 거절한다 (status가 OPEN이어도).

200 → `PageResponse<PostSummary>`

```json
{ "id": 12, "title": "토요일 아침 풋살 상대 구합니다", "matchAt": "2026-08-30T07:00:00+09:00",
  "location": "강서구민운동장 A구장", "region": "서울 강서구",
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
  "region": "서울 강서구", "preferredSkillLevel": "INTERMEDIATE",
  "rentalFee": 100000, "depositAmount": 50000,
  "bankName": "카카오뱅크", "accountNumber": "3333-01-1234567", "accountHolder": "김주장" }
```

- `title` 2~60자 필수 / `content` 최대 2000자 필수 / `matchAt` 미래 시각 필수 / `location`, `region` 필수 / `preferredSkillLevel` optional(null이면 무관) / `rentalFee`, `depositAmount` 0 이상 optional / `fieldType`은 v1.19.0 폐지 — 와도 무시
- `bankName`(최대 20자), `accountNumber`(최대 30자), `accountHolder`(최대 20자): **입금받을 계좌** — 전부 optional이지만 `depositAmount`를 넣으면 세 필드 모두 필수 (400 `VALIDATION_FAILED`)
- 계좌 3필드는 **절대 목록/상세에 공개되지 않는다.** 오직 수락된 신청 팀에게만 `payment` 오브젝트로 내려간다 (아래 참고)
- 팀 없으면 400 `TEAM_REQUIRED`
- 201 → `PostDetail`

### GET /api/posts/{postId} — 인증 불필요 (토큰 있으면 추가 필드 채움)

```json
{ "id": 12, "title": "...", "content": "...", "matchAt": "2026-08-30T07:00:00+09:00",
  "location": "강서구민운동장 A구장", "region": "서울 강서구",
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
- 필수 필드(`title`, `content`, `matchAt`, `location`, `region`)와
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

## 6-1. 매칭 채팅 (v1.12.0)

수락된 매칭(`ACCEPTED` 신청)마다 채팅방이 하나 있다. 별도의 방 리소스는 없다 —
**`requestId`가 곧 방 식별자**이고, 방은 수락 시점부터 암묵적으로 존재한다.

- **참여자는 두 팀의 감독(OWNER)뿐이다** — 접근 시점 기준 글 작성 팀 OWNER와 신청 팀
  OWNER. ADMIN·MEMBER는 접근 불가 (사용자 결정: 일정 조율 채널이므로 1:1).
- **전송은 `matchAt`까지만.** 매치 일시가 지나면 방은 **읽기 전용으로 영구 보존**된다
  (계좌·장소 등 주고받은 정보를 나중에 다시 볼 수 있어야 한다).
- 전송 방식은 **폴링**이다. WebSocket 등 실시간 전송은 v1 범위 밖 (§9). FE 폴링
  주기는 FE 재량 (권장: 채팅 화면에 있을 때 5초).
- 진입점: 매칭 관리 탭의 `ACCEPTED` 신청 카드에 "채팅" 버튼. 읽음 표시·안읽음
  배지는 범위 밖 — 새 메시지 인지는 푸시가 담당한다.

### ChatMessage

```json
{ "id": 45, "requestId": 7, "type": "TEXT", "senderTeamId": 3,
  "content": "그럼 7시까지 구장에서 뵙겠습니다!",
  "createdAt": "2026-08-28T21:15:00+09:00" }
```

`senderTeamId`로 내 팀/상대 팀 말풍선을 구분한다. 양 팀의 이름·정보는
`RequestResponse`의 `applicantTeam`/`postTeam`에 이미 있으므로 메시지에는 싣지 않는다.
`id`는 방 안에서 시간순 단조 증가 — 폴링 커서로 쓴다.

`type`(v1.13.0): `"TEXT" | "SYSTEM"`. SYSTEM은 서버가 만드는 안내 줄로
`senderTeamId: null`이고 `content`가 안내 문구다 (현재는 나가기 안내 하나 —
"상대 팀이 채팅방을 나갔습니다"). FE는 말풍선이 아니라 가운데 회색 안내로 그린다.
SYSTEM 메시지는 푸시를 보내지 않는다.

### GET /api/requests/{requestId}/chat — 인증 필요, 매칭 당사자 팀 OWNER만

쿼리 (전부 optional): `after`(이 메시지 id **이후**만), `limit`(기본 50, 최대 100).

200 응답:

```json
{ "messages": [ "<ChatMessage>", "..." ], "chatOpen": true }
```

- `messages`는 **오름차순**(오래된 것부터). `after` 없으면 **최신 `limit`개**를
  오름차순으로 (첫 로드용). `after`가 있으면 그 이후 것만 (폴링용).
- `chatOpen`: 지금 전송 가능한지 (**서버 시계** 기준 `matchAt` 전인지). FE는 이 값으로
  입력창을 잠근다 — 클라이언트 시계로 판정하지 않는다.
- 거절 케이스: 당사자 팀 OWNER 아님 403 `FORBIDDEN` (팀 없는 사용자·ADMIN·MEMBER 포함,
  §7 리뷰와 같은 규칙) / 신청이 `ACCEPTED` 아님 409 `REQUEST_NOT_ACCEPTED` /
  없는 신청 404 `REQUEST_NOT_FOUND`. 검사 순서는 **권한 먼저** (§7과 동일 —
  제3자에게 매칭 상태를 알려주지 않는다).

### POST /api/requests/{requestId}/chat — 인증 필요, 매칭 당사자 팀 OWNER만

```json
{ "content": "그럼 7시까지 구장에서 뵙겠습니다!" }
```

- `content` 1~500자 필수 (공백만인 것 불가)
- 거절 케이스: GET과 동일 + `matchAt`이 지났으면 409 `CHAT_CLOSED`
- 201 → `ChatMessage`
- 수정·삭제는 없다 (§9)

### 푸시 (§8 규약을 따른다)

| 이벤트 | 시점 | 수신자 | title / body 예시 |
|---|---|---|---|
| `CHAT_MESSAGE` | 메시지 전송 | 상대 팀 OWNER | "{보낸 팀 이름}" / "{내용 앞 50자}" |

data: `{ "type": "CHAT_MESSAGE", "requestId": 7, "postId": 12 }`. 탭 시 해당 매칭의
채팅방으로 이동. 메시지마다 발송한다 (묶음·스로틀은 v2). best-effort — 발송 실패가
메시지 저장을 실패시키면 안 된다 (§8과 동일).
v1.13.0: 방을 나간 상대에게는 발송하지 않는다. SYSTEM 메시지도 발송하지 않는다.

### 채팅 탭·방 목록 (v1.13.0)

FE 하단 탭이 5개가 된다: **홈 / 팀 / 매칭 / 채팅 / 마이**. 채팅 탭이 방 목록 화면이다.

#### GET /api/users/me/chats — 인증 필요

내 팀이 당사자인 **ACCEPTED 매칭**의 채팅방 목록. **내가 나간 방은 제외**된다.

```json
[ { "requestId": 16, "postId": 22, "postTitle": "다음 주 토요일 11인제 상대 구합니다",
    "matchAt": "2026-09-02T06:30:00+09:00", "chatOpen": true,
    "otherTeam": "<TeamSummary>",
    "lastMessage": "<ChatMessage | null>" } ]
```

- `otherTeam`: 상대 팀 (내가 글 작성 팀이면 신청 팀, 반대면 글 작성 팀)
- `lastMessage`: 내게 보이는 마지막 메시지 (나간 시점 이전은 제외 후 계산). 메시지가
  없으면 `null` — 방은 목록에 나온다 (조율을 시작하라는 뜻이므로 숨기지 않는다)
- 정렬: `lastMessage.createdAt` DESC, 메시지 없는 방은 그 뒤에 **신청의 마지막 상태
  변경(updatedAt) 최신순** (수락이 보통 마지막 변경이므로 사실상 수락 순 — 입금 확인이
  있으면 그만큼 위로 온다. acceptedAt 컬럼을 따로 두지 않는 의도적 선택)
- 팀이 없으면 빈 배열 (조회성 API 공통 규칙)
- 페이징 없음 — 한 팀의 진행 중 매칭 수는 작다 (필요해지면 v2)

### 나가기 (v1.13.0)

#### POST /api/requests/{requestId}/chat/leave — 인증 필요, 매칭 당사자 팀 OWNER만

내 팀이 방을 나간다. 204 (이미 나간 상태에서 또 불러도 204 — 멱등).
거절 케이스는 GET /chat과 동일 (권한 403 먼저, ACCEPTED 아니면 409, 없으면 404).

나가기의 효과:
1. **내 방 목록에서 사라진다 — 영구.** 상대가 새 메시지를 보내도 목록에 다시 나타나지
   않고, `CHAT_MESSAGE` 푸시도 내게 오지 않는다
2. **상대 방에는 SYSTEM 메시지**("상대 팀이 채팅방을 나갔습니다")가 남는다 — 상대가
   답 없는 방에서 기다리지 않게 한다
3. **나간 시점 이전 내역은 내게 보이지 않는다.** 이후 매칭 카드로 재입장하면 나간
   시점 이후 메시지만 온다 (`GET /chat`이 걸러서 준다 — FE가 거를 필요 없음)
4. **재입장해서 전송하면 복귀다.** 전송 성공 시 숨김이 풀려 목록에 다시 나오고 푸시도
   재개된다 (복귀 SYSTEM 메시지는 없다). 매치가 끝난 방(`CHAT_CLOSED`)은 전송이
   없으므로 복귀 경로도 없다
- 나가기는 채팅에만 영향을 준다 — 매칭·리뷰·전적 등 다른 기능은 그대로다

FE: 나가기는 채팅방 안에서 제공하고(위치는 FE 재량), **되돌릴 수 없는 동작이므로 확인
다이얼로그를 거친다.** 문구에 "지난 대화를 다시 볼 수 없게 됩니다"를 포함할 것.

## 6-2. 매칭 취소 (v1.20.0)

수락된 매칭을 **경기 전까지** 무를 수 있다. 사정이 생기거나 노쇼가 예상될 때 매칭을
풀고 글을 다시 여는 경로다.

### POST /api/requests/{requestId}/cancel-match — 인증 필요, 매칭 당사자 팀 OWNER만

- **양 팀 어느 쪽이든** 취소할 수 있다 (글 작성 팀 OWNER 또는 신청 팀 OWNER)
- 조건: 신청이 `ACCEPTED` && `matchAt`이 **미래**
  - `ACCEPTED` 아니면 409 `REQUEST_NOT_ACCEPTED` / `matchAt`이 지났으면 409
    `MATCH_CANCEL_EXPIRED` (경기가 이미 진행된 것으로 본다 — 리뷰·기록의 영역)
  - 제3자·ADMIN·MEMBER는 403 `FORBIDDEN` (검사 순서 권한 먼저, §7 규칙)
- 효과:
  1. 신청 `status` → **`MATCH_CANCELED`** (기존 `CANCELED`는 수락 전 신청 철회 —
     의미가 달라 값을 나눈다)
  2. 글 `status` → **`OPEN` 복구** — 다시 신청받을 수 있다. 수락 때 자동 거절됐던
     다른 신청들은 `REJECTED` 그대로 (재신청 허용 이력 규칙에 따라 다시 신청 가능)
  3. **연락처·payment 비공개 복귀, 채팅 닫힘** — 채팅 접근은 기존 규칙(ACCEPTED
     아니면 409)에 의해 자동으로 막히고, 방 목록에서도 사라진다
  4. `depositPaid`는 그대로 보존 (환불 분쟁 시 기록)
- 200 → `RequestResponse` (status MATCH_CANCELED)
- 멱등 아님 — 이미 취소된 매칭에 또 부르면 409 `REQUEST_NOT_ACCEPTED`

### 푸시 (§8 규약)

| 이벤트 | 시점 | 수신자 | title / body 예시 |
|---|---|---|---|
| `MATCH_CANCELED` | 취소 | **상대 팀** OWNER | "매칭 취소" / "'{글제목}' 매칭이 취소됐습니다" |

data: `{ "type": "MATCH_CANCELED", "requestId": 7, "postId": 12 }`. 탭 시 매칭 관리로.

### FE 규칙

- 취소 진입: 매칭 관리의 ACCEPTED 카드 (양 관점 모두). **확인 다이얼로그 필수** —
  문구에 ① 상대 팀에게 알림이 간다 ② **입금했다면 취소 전에 환불을 확인하라**
  (취소되면 연락처·채팅이 닫힌다) 를 포함할 것
- `MATCH_CANCELED` 상태 뱃지: "매칭 취소됨". 취소된 카드에는 채팅·입금·리뷰·전적
  버튼을 두지 않는다
- 구버전 호환: 옛 서버에는 이 상태가 없으므로 FE는 모르는 status를 안전하게(회색
  뱃지 등) 그린다

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

## 7-1. 고객센터 문의 (v1.22.0)

사용자와 **운영자** 간의 1:1 상시 채팅. 건의·문의·신고가 모두 여기로 온다.
매칭 채팅(§6-1)과 같은 폴링 구조지만 **별개 도메인**이다 — 만료가 없고, 상대가
팀이 아니라 운영자다.

- **방은 사용자당 하나**, 첫 조회/전송 시 암묵 생성. 닫히지 않는다
- **운영자**: `SUPPORT_OPERATOR_EMAIL` env로 지정된 이메일의 계정 (기본 미설정 —
  미설정이면 문의함 API는 전부 403). 운영자 판정은 서버만 안다
- 사용자에게 운영자는 "킥오프 고객센터"로 표시된다 (계정 정보 비노출)

### SupportMessage

```json
{ "id": 3, "sender": "USER", "content": "매칭 상대가 연락이 안 돼요.",
  "createdAt": "2026-09-01T12:00:00+09:00" }
```

`sender`: `"USER" | "AI" | "OPERATOR"`. FE 표시 — AI는 "AI 상담사", OPERATOR는
"킥오프 고객센터". `id`는 방 안에서 단조 증가 — 폴링 커서.

### 3단 응대 구조 (v1.22.0)

1. **FAQ 퀵버튼** — `GET /api/support/faq` (인증 필요) → `[{ "id": 1, "question":
   "...", "answer": "..." }]`. FE가 방 상단/빈 방에 버튼으로 깔고, 누르면 질문·답변을
   **화면에서만** 주고받은 것처럼 그린다 (서버 저장·AI 호출 없음 — 무료·즉답.
   방 이력에는 안 남는다). 내용은 BE 리소스 파일(운영 문서에서 발췌)
2. **AI 상담사** — 방이 운영자 연결 상태가 아니면, 사용자 메시지 저장 후 서버가
   Claude API로 답변을 생성해 `sender: "AI"` 메시지로 저장한다(다음 폴링에 잡힘).
   - env: `ANTHROPIC_API_KEY`, `AI_SUPPORT_ENABLED`(기본 false — 미설정·꺼짐이면 AI
     없이 운영자 연결만 동작), 모델은 저비용 모델(BE 재량, claude-haiku 계열 권장)
   - 시스템 프롬프트·서비스 지식은 BE 리소스 파일 (supervisor가 작성·관리)
   - **AI 제약**: 환불·제재·보상은 약속하지 않고 운영자 연결을 권하도록 프롬프트로
     제한. 응답 실패(타임아웃 등) 시 "운영자에게 전달해 드릴까요?" 안내 메시지로 강등
   - 남용 방지: 사용자당 AI 호출 분당 5회 초과 시 429 `VERIFICATION_RATE_LIMITED`
     재사용이 아니라 **`SUPPORT_RATE_LIMITED`(429) 신설**
3. **운영자 문의 (비동기 접수)** — `POST /api/support/chat/escalate` → 204. 방이
   **운영자 모드**가 되고 이후 AI는 답하지 않는다(v1에서는 되돌리기 없음).
   에스컬레이트 시점과 운영자 모드 방의 새 사용자 메시지에 운영자 푸시.
   **UX는 실시간 연결이 아니라 "문의 접수"다** (v1.22.0 확정판, 사용자 결정 — 운영자가
   실시간 응대하지 않는다): FE 버튼명 "운영자에게 문의 남기기", 누르면 문의 작성
   입력을 거쳐 전송+escalate를 함께 수행하고, "문의가 접수되었습니다. 답변이 등록되면
   알림으로 알려드려요"로 안내한다. "확인 중" 등 즉답 기대를 만드는 문구는 쓰지
   않는다. 운영자 모드 방은 답변 후에도 자유롭게 이어서 대화할 수 있다

### 사용자 쪽 API — 인증 필요

- `GET /api/support/chat?after=&limit=` — 내 문의방 메시지. §6-1과 같은 규칙
  (오름차순, after 없으면 최신 limit개, limit 기본 50 최대 100). 200 →
  `{ "operatorMode": false, "messages": [...] }` (chatOpen 없음 — 항상 열려 있다)
- **`operatorMode` (v1.22.0 확정판)**: escalate했거나 운영자가 답장한 방이면 true —
  서버가 방 상태의 진실이다. FE는 true면 "운영자 연결하기" 버튼과 AI 안내를 감춘다
  (말풍선 유무로 추론하지 않는다 — escalate 직후 무응답 구간이 추론으로는 안 보인다)
- `POST /api/support/chat` — `{ "content": "..." }` (1~500자). 201 → `SupportMessage`

### 운영자 쪽 API — 인증 필요, 운영자만 (아니면 403 `FORBIDDEN`)

- `GET /api/support/rooms` — 문의방 목록. 200 →
  `[{ "userId": 5, "nickname": "김감독", "lastMessage": "<SupportMessage>" }]`
  (lastMessage.createdAt DESC). 메시지 없는 방은 목록에 없다 (문의가 시작돼야 방이
  의미를 가진다). 페이징 없음
- `GET /api/support/rooms/{userId}/chat?after=&limit=` / `POST .../chat` — 해당
  사용자 방 조회·답장 (형태는 사용자 쪽과 동일, 없는 사용자 404 `USER_NOT_FOUND`)

### 푸시 (§8 규약)

| 이벤트 | 시점 | 수신자 | title / body 예시 |
|---|---|---|---|
| `SUPPORT_MESSAGE` | 사용자가 전송 (**운영자 모드 방만** — AI 응대 중에는 미발송, v1.22.0) | 운영자 | "고객센터 문의" / "{닉네임}: {내용 앞 50자}" |
| `SUPPORT_MESSAGE` | 운영자가 답장 | 그 사용자 | "킥오프 고객센터" / "{내용 앞 50자}" |

data: `{ "type": "SUPPORT_MESSAGE", "userId": 5 }` (userId는 방 주인 — 운영자 탭용.
사용자 탭 시에는 무시하고 자기 문의방으로). best-effort.

### FE 규칙

- 진입: 마이 탭에 "고객센터 문의" (모든 사용자). 채팅 화면은 §6-1 컴포넌트 재사용,
  입력창 상시 활성, 헤더 "킥오프 고객센터"
- **운영자 계정으로 로그인하면** 마이 탭에 "문의함"이 추가로 보인다 — 방 목록 →
  각 방 진입·답장. 운영자 여부는 문의함 API가 403이 아닌지로 판정 (강등 패턴)
- 빈 방 안내: "궁금한 점, 건의, 신고할 일을 남겨 주세요. 운영자가 확인 후 답변드립니다."

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

실명 본인인증(PASS·본인확인기관 연동 — 사업자 등록 후 v2),
계정 통합(한 계정에 복수 로그인 수단 연결, 기존 계정 병합·빈 소셜 계정 정리 — v2),
소셜 가입자의 앱 자체 약관 동의 절차,
이메일 찾기의 전체 이메일 공개 응답(v1은 마스킹만 — §3-5),
팀 검색 오타 유사도(편집거리·trigram),
채팅 실시간 전송(WebSocket — v1은 폴링), 채팅 읽음 표시·안읽음 배지,
채팅 메시지 수정·삭제·신고, 채팅 이미지 첨부, 채팅 푸시 묶음·스로틀,
이미지 업로드(팀 로고 포함), 스쿼드/포메이션 보드,
기록 수정, 소유권 이전·소유자 탈퇴, 가입 초대(팀→사용자 방향),
경기 종료 리마인드 푸시(기록·리뷰 유도 알림),
리뷰 수정·삭제·신고, 소셜 계정 연동 해제, GOOGLE·APPLE 로그인 활성화(값만 예약),
다중 기기 push token, 알림 히스토리 화면, 알림 설정(끄기/켜기),
홈 목록 지도 뷰, 좌표 반경 검색, 정적 지도 이미지
