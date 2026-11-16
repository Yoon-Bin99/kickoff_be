# Kickoff API 계약 v1 (현재 v1.37.3)

조기축구 팀 매칭 앱. 이 문서가 FE/BE/채팅 서버 사이의 **단일 진실 공급원**이다.
변경이 필요하면 임의로 고치지 말고 supervisor에게 보고할 것.

> v1.37.3 (2026-11-16): §7-2 — ① 운영자 계정에 이용 제한을 걸 때의 거절을 **409 `OPERATOR_NOT_SUSPENDABLE`**로 나눈다(v1.37.2의 400을
> 바꿈 — 범위를 벗어난 `suspendDays`의 400과 FE가 구별할 수 없었다, FE 지적). ② 신고 상세 `target.messages`의 원소는 **다섯 필드만**
> (`requestId` 없음). ③ **복원 뒤 재탈퇴용 운영자 API** `POST /api/admin/users/deletions` 신설(BE 제안 — 평시에는 꺼 둔다).

> v1.37.2 (2026-11-16): §7-2 — Supervisor의 실서버 점검(BE+FE를 붙여 본 결과)에서 나온 것. ① **`ReportDetail`의 모양을 필드 이름까지
> 못 박는다** — 지금까지 글로만 적혀 있어 BE(`summary` 안에 중첩, `body`)와 FE(펼친 모양, `content`)가 서로 다르게 만들었다. 펼친
> 모양이 맞다. ② `resolution`에 `suspendDays`. ③ **운영자 계정은 이용 제한 대상이 될 수 없다**(400) — 운영자가 자기 팀이 신고된
> 건을 제한으로 처리하면 운영자 API가 전부 403이 되어 스스로 풀 수 없었다. ④ 금칙어 안내 문구에 마침표.

> v1.37.1 (2026-11-16): 법무 검토 반영 (v1.36.0·v1.37.0 모두 구현 전). ① `GET /api/places/search` 응답에서 `latitude`·`longitude`를
> **뺀다** — 미리보기 지도도 기기에서 다시 조회해 그리므로 쓰이지 않는 값이다(실수로 저장할 여지를 없앤다). ② 검색 결과의
> 이름·주소를 칸에 넣어 주는 도우미(복사 버튼 등)를 두지 않는다. ③ `Weather.area` — 예보의 기준 지역(시군구를 못 찾아 시/도 대표
> 지점을 썼는지 화면이 말할 수 있게). ④ 날씨에 기상청 값에 없는 판단 문구를 붙이지 않는다(기상법 제17조). ⑤ 앱의 고정된 자리에
> "데이터 출처" 표기.

> v1.37.0 (2026-11-16): **카카오 장소 검색 결과를 저장하지 않는다 (§5-1 전면 개정, 법률 점검 A-3 — YOON 결정: 그때그때 조회해 지도 표시)**.
> 카카오 정책상 검색 응답의 장소 이름·주소·좌표는 저장할 수 없고 장소 ID만 저장할 수 있다. ① 모집글의 `latitude`·`longitude`를
> **없앤다**(요청·응답·DB). ② 대신 `kakaoPlaceId`(고른 장소의 ID)와 `placeQuery`(그때 **사용자가 친 검색어**)를 저장한다. ③ 글 상세의
> 지도는 볼 때마다 **보는 사람의 기기가** 카카오에 `placeQuery`로 다시 물어 ID가 같은 장소를 찾아 그린다(서버를 거치지 않는다 —
> 서버 부하 없음, 좌표는 화면에만 있다). 못 찾으면 "카카오맵에서 보기" 버튼만. ④ 장소 이름·주소·지역은 **사용자가 직접 적은 값만**
> 저장한다 — 검색 결과로 자동으로 채우지 않는다(`locationAddress` 자동 채움 v1.29.0 폐지, 지역 자동 채움 폐지). ⑤ 기존 글의 좌표와
> 주소는 지운다. ⑥ `GET /api/places/search` 응답에 `id`. **구현은 Supervisor가 따로 지시할 때 시작한다.**

> v1.36.0 (2026-11-16): **경기일 날씨 (§8-3 신설)**. 모집글·매칭 신청 응답에 `weather`(nullable)가 생긴다 — 경기 시각의 예상
> 날씨(이모지용 값)와 기온. 기상청 단기예보(오늘~약 3일)와 중기예보(~10일)를 BE가 대신 받아 메모리에 두고, 응답은 **그 보관분만**
> 읽는다(요청 중에 기상청을 부르지 않는다 — 날씨 때문에 목록이 느려지거나 실패하지 않는다). 위치는 글의 `region`(시/도+시군구)
> 기준이라 좌표가 필요 없다. 인증키(env `KMA_SERVICE_KEY`)가 없으면 `weather`는 전부 `null`이고 나머지는 정상이다.
> 새 열거형 `WeatherCondition`·`WeatherKind`. **구현은 Supervisor가 따로 지시할 때 시작한다**(§7-2 작업이 먼저다).

> v1.35.4 (2026-11-16): §7-2 문구 다듬기 (BE 구현 보고 — 동작이 바뀌는 곳은 없다). ① 가려진 자리의 표시 문구 세 개에 마침표를
> 붙인다(법무 초안·FE 화면과 같게). ② 신고 "하루 10건"은 달력 날짜가 아니라 **지난 24시간** 기준. ③ 자동 가림에서 "같은 팀"을
> 세는 법을 적는다(감독·관리자·팀원 모두 그 팀의 1명, 여러 팀에 속한 계정은 소유한 팀 → 없으면 가장 먼저 들어간 팀).

> v1.35.3 (2026-11-16): §3 「동의」의 재동의 판정을 고침 (BE 지적). 시행일 설정값에 **아직 오지 않은 날짜**를 넣으면 전원이 막히고
> 다시 동의해도 풀리지 않는 구멍이 있었다(새 동의 시각도 그 날짜보다 이르므로). 이제 **시행일이 이미 지났을 때만** 그보다 이른
> 기록을 "다시 받아야 함"으로 본다 — 개정을 미리 공지하면서 값을 올려 둘 수 있다. 그리고 이 값은 **재동의가 필요한 개정일 때만**
> 올린다(위탁 업체 변경 같은 일반 개정은 공지로 충분하고 값을 올리지 않는다).

> v1.35.2 (2026-11-16): §7-2 보완 (FE 구현 보고의 지적 — BE 구현 전이라 동작이 바뀌는 서버는 없다). ① 409
> `BLOCK_HAS_UPCOMING_MATCH` 응답에 `matchAt`(가장 가까운 예정 경기)을 싣는다 — 확인창이 날짜를 말할 수 있게. ② `ReportSummary`에
> `hiddenUntil` — 임시 가림의 기한이 임박한 건을 운영자 목록에서 알아볼 수 있게. ③ 감독이 아닌 계정의 차단 확인 문구를 나눈다.

> v1.35.1 (2026-11-16): **운영자 기능에 2단계 인증과 감사 기록** (법무 — 안전성 확보조치 기준 제6조 제2항·제8조) + v1.35.0 문구
> 정정. ① 운영자 API(§7-1 문의함, §7-2 신고함)는 로그인에 더해 **일회용 비밀번호(OTP)로 연 운영자 세션**이 있어야 한다 —
> `POST /api/admin/session`, 헤더 `X-Operator-Token`, 30분 뒤 만료. ② 운영자 API의 모든 호출을 **감사 기록**으로 남긴다(누가·언제·
> 어디서·누구의 정보를·무엇을, 1년 보관). ③ 채팅 서버 지적 반영(동작은 구현된 그대로): report-context 검사 순서, 나가기 전
> 메시지는 404, 차단의 `ROOM_STATE`는 `chatOpen`이 바뀔 때만, `CHAT_CLOSED` 문구를 중립으로. ④ 채팅 서버 공개 상태 확인
> `GET /health`.

> v1.35.0 (2026-11-16): **신고·차단·운영 조치 신설** (사용자 결정 — 2026-09-01의 "신고 기능은 만들지 않음"을 뒤집음. 법률 점검
> A-4, 스토어 심사 요건). §7-2 신설. ① **신고** `POST /api/reports` — 모집글·리뷰·팀·채팅 메시지, 사유 7종. 신고한 사람 화면에서는
> 바로 가려지고, 서로 다른 신고자 3명이면 운영자 확인 전까지 자동으로 임시 가림(최대 30일). ② **차단** `/api/blocks` — 팀 단위.
> 차단한 쪽 화면에서 그 팀의 글·리뷰가 사라지고, 두 팀은 서로 신청·채팅 전송을 못 한다. 예정된 매칭이 있으면 취소와 함께만 차단된다.
> ③ **운영 조치** `/api/admin/reports` — 임시 가림·삭제·이용 제한, 신고자·게재자 양쪽 통지(고객센터 메시지 `NOTICE`). ④ 가려진
> 콘텐츠의 응답 형태(`moderation`, 410 `CONTENT_MODERATED`), 이용 제한 계정(403 `ACCOUNT_SUSPENDED`). ⑤ 금칙어 검사. ⑥ §10 —
> `RoomContext.blocked`, 채팅 서버의 신고 문맥 내부 API. §9에서 "신고"를 뺀다.

> v1.34.1 (2026-11-16): v1.34.0을 **법무 검토에 맞춰 고침** (구현이 끝나기 전의 정정 — v1.34.0만 구현한 서버·앱은 없다).
> ① **연락처 제공 동의(`contactSharing`)는 가입할 때가 아니라 팀을 만들 때 받는다** — 전화번호가 상대에게 넘어가는 사람은
> 감독뿐이라, 팀원으로만 활동할 가입자에게 필수로 요구하지 않는다. 가입·동의 화면의 항목은 셋(`age14`·`terms`·`privacy`),
> `POST /api/teams`에 `contactSharingConsent: true` 필수. ② `privacy`는 "동의"가 아니라 **"안내 확인"**이다(가입에 꼭 필요한
> 정보는 동의 없이 수집하고 알린다 — 제15조 제1항 제4호, 제22조 제3항). ③ `UserResponse.pendingConsents` 신설 — 동의 화면이
> 어느 항목을 받아야 하는지 서버가 알려 준다. ④ **경기 뒤 연락처·계좌 표시 기간**: 상대 팀에게는 경기일 + 14일까지만 보인다
> (법률 점검 C-2 — 동의 문구의 "보유·이용 기간"이 이 값이다).

> v1.34.0 (2026-11-16): **동의 구조 개편** (사용자 승인 — 법률 점검 A-2). 한 번의 체크로 묶여 있던 동의를 **네 항목으로
> 나누고**, 서버가 항목별로 기록하며, **동의 전에는 서버가 막는다**. ① 가입 요청의 `termsAgreed`를 폐지하고 `consents`
> 오브젝트(만 14세 이상·이용약관·개인정보 수집·이용·매칭 성사 시 연락처 제공)로 바꾼다. ② `POST /api/users/me/terms-agreement`를
> 폐지하고 `PUT /api/users/me/consents`로 바꾼다. ③ `UserResponse.consentRequired` 신설 — 동의 화면을 띄울지는 **서버가
> 판정**한다(FE 상수 `TERMS_EFFECTIVE_AT` 폐지). ④ 동의가 필요한 계정의 인증 API 호출은 403 **`CONSENT_REQUIRED`**(신설).
> ⑤ 모집글에 입금 계좌를 적을 때 `paymentShareConfirmed: true` 필수(수락된 상대 팀 감독에게 보인다는 확인). ⑥ 입금 계좌
> 3필드는 암호화해 저장한다(A-1, BE env `ACCOUNT_ENC_KEY` — API 변화 없음). 화면 문구는 법무 초안이 오면 FE에 따로 전달한다.

> v1.33.1 (2026-11-16): v1.33.0의 **문구만** 구현에 맞춰 바로잡음(채팅 서버 지적, 동작 변화 없음). ① 검사 순서 표기 —
> 없는 신청 404가 맨 앞이다(신청이 없으면 누가 당사자인지 알 수 없다. v1.12.0부터 같은 동작). ② "지났으면"의 경계 —
> `deleteAt`과 같은 순간까지는 읽히고 그보다 뒤부터 410이다(`matchAt`의 전송 가능 판정과 같은 규칙).

> v1.33.0 (2026-11-16): **매칭 채팅의 보관 기간을 정한다 — 경기일로부터 90일** (사용자 결정, 법률 점검 11-2). §6-1의
> "읽기 전용으로 영구 보존"을 없앤다. ① 경기가 끝난 방은 `matchAt` + 90일까지만 읽을 수 있고 그 뒤 삭제된다. ② 그 시각이
> 지나면 실제 삭제(하루 한 번)를 기다리지 않고 바로 410 **`CHAT_EXPIRED`**(신설)로 막히고 방 목록에서 빠진다. ③ `GET /chat`
> 응답에 **`deleteAt`**(삭제 예정 시각) 추가 — FE는 이 날짜로 안내 한 줄을 보여 준다. ④ 취소된 방(이미 접근 불가)은
> 취소 후 30일 뒤 삭제. ⑤ §10 보관 기간 정리를 **기본 켜짐**으로 바꾸고, 경기 뒤의 활동(나가기)이 기간을 늘리지 않게 한다.
> BE의 API는 바뀌지 않는다(처리방침·고객센터 문구만).

> v1.32.0 (2026-11-15): **웹은 refresh token을 HttpOnly 쿠키로 받는다** (사용자 결정 — "웹은 웹 표준 방식으로"). §3
> 「웹의 토큰 전달」 신설. 웹 클라이언트가 `X-Kickoff-Client: web` 헤더로 자신을 밝히면, 토큰을 내주는 모든 응답(로그인·
> 가입·소셜 코드 교환·refresh)이 refresh token을 **본문에서 빼고** `Set-Cookie`(HttpOnly·Secure·SameSite)로 준다. 웹의
> refresh·logout은 본문이 아니라 쿠키로 동작한다. access token은 지금처럼 본문으로 받아 **메모리에만** 둔다(웹의
> localStorage 저장 폐지). **앱(네이티브)은 그대로다** — 헤더를 보내지 않으면 v1.31.4와 같은 응답이 온다. 채팅 서버는
> 쿠키를 쓰지 않는다(access token만 본다). 요청 위조 방어로 쿠키를 쓰는 두 경로에 Origin 검사를 둔다.

> v1.31.4 (2026-11-15): `POST_HAS_REQUESTS`의 **안내 문구만** 고침(BE 지적). 화면의 "신청 N건"(`requestCount`)은 대기·수락만
> 세는데 삭제를 막는 기준은 모든 상태라, 거절·취소된 신청만 남은 글은 "신청 0건"으로 보이면서 지워지지 않는다. 그래서
> 문구를 "신청 **이력**이 있는 글은 삭제할 수 없어요(거절·취소된 신청 포함). 더 받지 않으려면 마감으로 바꿔 주세요"로 바꾼다.
> 규칙·코드·상태는 v1.31.3 그대로.

> v1.31.3 (2026-11-15): **신청이 달린 모집글은 삭제할 수 없다** (§5 DELETE, 사용자 결정). 지금까지는 신청이 하나라도 있는
> 글을 지우면 500이 났다(신청 행의 외래 키 — 막지도 지우지도 않는 버그). 이제 409 `POST_HAS_REQUESTS`로 막고, FE는
> "신청이 있는 글은 삭제할 수 없어요. 더 받지 않으려면 마감으로 바꿔 주세요"를 보여 준다. 신청 기록(상대 팀의 이력)을
> 조용히 지우지 않기 위해서다. 그 밖에 §10 문구 두 줄을 분명히 했다(4xx 처리, 푸시 생략 로그의 범위).

> v1.31.2 (2026-11-15): **채팅 서버 전환 완료** + 구현으로 확정된 세부 명문화 + 낡은 문구 정리. 동작을 바꾸는 규칙은 없다.
> ① §10 전환 순서 4단계가 끝났다 — BE에는 매칭 채팅 API·표가 없고(V25), §6-1은 채팅 서버만 답한다. BE에 옛 경로를
> 부르면 토큰 없이 401, 토큰 있으면 404다(**401로 "옛 서버"를 판정하지 말 것**). ② §10 명문화: 푸시 요청 본문 한도,
> 이벤트 응답별 처리(재시도·dead)와 한 바퀴 시간 예산, 미전송 PURGE 경보, 탈퇴 뒤 접근이 막히기까지의 시간(통상 5초
> 안팎·최대 15초), 파기 기록, 보관 기간 정리 장치(기본 꺼짐 — 기간은 미정), 채팅 서버의 소켓 세부(유휴 70초 등).
> ③ 문구 정리: 하단 탭은 6개(구장 포함), FE는 Expo SDK 54, 예시의 "풋살·6인제"와 지도에 없는 구장 이름 교체,
> `PASSWORD_MISMATCH`·동의 게이트·`TeamMember` 참조 절, 옛 필드명 `costPerTeam`, §3-1 토큰 전달 문구.

> v1.31.1 (2026-11-15): §6-1 실시간 연결을 **구현 보고에 맞춰 확정**(채팅 서버·FE 실측 반영, 규칙 추가는 종료 코드 하나).
> ① 종료 코드 **4008** 신설 — 동시 연결 5개 초과로 서버가 오래된 연결을 정리할 때(v1.31.0은 1000으로 묶여 FE가 "내가
> 닫은 것"과 구별할 수 없었다). FE는 다시 붙지 않는다(다른 기기와 서로 밀어내게 된다). ② **연결 범위를 좁힌다** — FE는
> 앱 전체가 아니라 **채팅방 화면이 떠 있는 동안만** 연결한다(지금 소켓으로 하는 일이 방 화면뿐이라 채팅을 안 쓰는 사용자가
> 연결을 물고 있을 이유가 없다. 방 목록 실시간 갱신을 넣는 날 넓힌다). ③ 4001이 `AUTH_OK` 없이 연속 두 번이면 즉시
> 재연결을 멈추고 백오프(무한 루프 방지). ④ 구독 `ERROR`가 `CHAT_UPSTREAM_UNAVAILABLE`이면 FE는 읽던 대화를 오류 화면으로
> 덮지 않는다. ⑤ 웹은 탭이 숨으면 연결을 닫는다(푸시 생략 규칙의 전제). ⑥ 전송 한도는 **시도 기준**, 탈퇴 직후 남은 토큰의
> 동작(403/404/빈 목록) 명문화.

> v1.31.0 (2026-11-15): **매칭 채팅을 별도 서버(`kickoff_chat`)로 분리하고 받기를 WebSocket으로** (사용자 결정 —
> "채팅 기능이 다른 서비스에 영향을 주지 않게"). ① §0 **채팅 Base URL** 신설(FE env `EXPO_PUBLIC_CHAT_URL`). §6-1의
> REST 4개는 **경로 그대로, 답하는 서버만 바뀐다.** ② §6-1 「실시간 연결」 신설 — `/ws`, 연결 후 첫 메시지 `AUTH`로
> 토큰 전달(주소에 싣지 않는다), `SUBSCRIBE`/`MESSAGE`/`ROOM_STATE`/`PING`, 종료 코드 표. **보내기는 REST `POST` 그대로**
> (소켓은 받기 전용), 소켓이 안 되면 FE는 5초 폴링으로 강등. ③ 푸시: 받는 사람이 그 방을 소켓으로 구독 중이면 보내지
> 않는다. 발송 주체는 계속 BE. ④ §0 에러 `CHAT_RATE_LIMITED`(429)·`CHAT_UPSTREAM_UNAVAILABLE`(503). ⑤ **§10 서버 간 내부
> 규약** 신설(BE↔채팅 서버, FE 무관) — 내부 API 3개·이벤트 2종·공유 비밀 헤더. ⑥ §9에서 "채팅 실시간 전송" 제거.
> 고객센터 문의(§7-1)는 이번 분리 대상이 아니다(BE에 남는다). **전환 중 안내**: 채팅 서버·FE 전환이 끝날 때까지 BE의
> 옛 채팅 API가 남아 있고, 전환 완료 후 BE에서 제거한다(§10 전환 순서).

> v1.30.1 (2026-11-10): §8-2를 **카카오 실응답에 맞춰 확정**(BE 실측 반영). ① `start_at`은 **UTC 자정**으로 온다
> (`2026-02-16T00:00:00Z`) — 카카오 문서 예시(KST 자정 `…T15:00:00Z`)와 다르다. 그래서 연휴 펼치기는 `end_at`의
> 날짜가 아니라 **`(end_at − start_at)` 일수**로 센다(두 관례 모두 같은 답). ② 연휴는 한 건이 아니라 **하루씩**
> 온다(설날연휴·설날·설날연휴 세 건, 같은 이름이 다른 날에 반복) — 묶어 오는 경우는 방어만. ③ 31일 제한 실측
> (32일 창은 400 code -2, 검사는 KST 환산), 2050년까지 데이터 있음·2060년 빈 배열. 규칙 변경 없음, 사실 정정만.

> v1.30.0 (2026-11-10): **공휴일 조회** 신설 (§8-2). `GET /api/holidays?year=YYYY`(인증 불필요)가 카카오 톡캘린더
> 공휴일 API(어드민 키, 서버 전용)에서 받은 그 해 **법정공휴일**(임시·대체공휴일 포함)을 내린다. `source`가
> `KAKAO`면 FE는 그 해 달력을 이 목록으로 칠하고(자체 계산값 대체), `NONE`(키 없음·호출 실패·데이터 없음)이면
> 지금처럼 `src/lib/holidays.ts` 계산값을 쓴다. 실패해도 **절대 500 아님**. 환경변수 `KAKAO_ADMIN_KEY` 추가.

> v1.29.1 (2026-11-10): `locationAddress`는 PATCH에서 **명시적 null로 지울 수 있는 필드**(장소 검색으로 잘못
> 고른 글을 직접 입력으로 되돌리는 길). 예시의 "강서구민운동장"은 카카오 지도에 없는 이름(검색하면 부산이
> 유사 매칭)이라 §5·§5-1 예시를 실제로 검색되는 **우장산축구장**(서울 강서구 우장산로 72)으로 교체 — 시드도
> 같은 값으로 수정됨(BE 4126d59). 규칙 변경은 PATCH 한 줄뿐.

> v1.29.0 (2026-11-10): **모집글에 경기장 주소** `locationAddress`(§5). 장소 검색(§5-1)에서 고르면 FE가
> `roadAddress ?? address`를 자동으로 채워 보내고, 상세·목록 카드에 전체 주소를 표시한다. nullable(직접
> 입력 글·기존 글은 null), 최대 100자, 사용자가 고친 값 그대로 저장(검증은 길이만). 목록 `dates` 다중
> 필터(v1.18.0)는 그대로 — 홈 날짜 선택 UI만 복수 선택 드롭다운으로 바뀐다(계약 변경 없음).

> v1.28.1 (2026-10-08): §4-4 확정(BE 구현 반영). ① §0 `SQUAD_NOT_FOUND`(404) 신설 — 없는 스쿼드와
> **남의 팀 스쿼드 id를 내 팀 주소에 끼운 경우** 둘 다 이 코드(구분하면 타 팀 id 탐색이 됨).
> ② 읽기 API의 비로그인은 다른 인증 필요 API와 같이 **401 `UNAUTHORIZED`**(시큐리티 필터), 로그인했지만
> 비소속이면 403 `FORBIDDEN` — v1.28.0의 "비로그인 403"은 permitAll+서비스 검사 한 줄에 기대야 해서 철회.
> ③ 30개 상한은 **생성에만** 적용(수정은 상한에서도 가능 — 막다른 길 방지). ④ 중복 memberId의
> fieldErrors는 **두 번째** 등장 자리를 가리킨다.

> v1.28.0 (2026-10-08): **스쿼드 메이커** 신설 (§4-4). 팀 안에서 포메이션을 고르고 팀원 명단(§4-1)을
> 자리에 배치해 저장·열람한다. 공유는 **FE가 경기장 그림을 이미지로 만들어 OS 공유창(카톡 등)으로**
> 보내는 방식 — 서버는 이미지를 만들거나 저장하지 않는다. §1 `Formation` 열거형 추가. §9의
> "스쿼드/포메이션 보드"를 범위 안으로 옮기고, 서버 이미지 렌더링·링크 공유는 v2로 남긴다.

> v1.27.1 (2026-09-28): §8-1을 실제 서울 API 응답에 맞춰 확정(BE 실측 반영). ① `address`는
> nullable — 서울 공공 API는 주소를 주지 않아 SEOUL_PUBLIC은 항상 null(지어내지 않는다).
> ② `region`은 BE가 자치구 앞에 "서울 "을 붙여 §5 형식으로 맞춘다(AREANM은 "성동구"만 옴).
> ③ 행 단위는 구장이 아니라 **예약 서비스**(같은 구장이 "평일"·"토/일/공휴일"로 복수 행,
> SVCID 기준 갱신, name=SVCNM 원문). FE 목록 상단 안내에 이 사실을 한 줄 포함.
> ④ `usePeriod`는 날짜만 남긴 "YYYY-MM-DD ~ YYYY-MM-DD", 형식이 예상과 다르면 원문 유지.

> v1.27.0 (2026-09-28): **구장 디렉터리** 신설 (§8-1). 지역(시·자치구)으로 구장을 찾아 외부
> 예약 페이지로 보내는 조회 전용 기능. GET /api/stadiums (인증 불필요) — region 필터,
> 이름·주소·예약 링크·출처·(있으면) 접수 상태를 준다. 데이터는 ① 서울 공공서비스예약
> 공공 API 동기화(축구장·풋살장, 예약 URL·접수 상태 포함, 하루 1회 갱신+수동 트리거)
> ② 운영자 수동 시드(경기·인천·사설). **시간대별 빈자리 조회는 범위 밖**(§9) — 공공/사설
> 예약 시스템이 슬롯 데이터를 공개하지 않아 불가능, 서울 공공분은 "접수중/마감" 상태까지만.
> 예약 자체는 전 구간 외부 사이트에서(중개·대행 아님, 링크 연결만).

> v1.26.2 (2026-09-10): **이미 가입된 번호에는 인증번호를 기본 발송하지 않는다** (§3-2 FE 규칙).
> availability 사전 확인이 phone `false`(사용 중)를 알고 있으면 발송 버튼 단계에서 막고 "이미
> 가입에 사용된 번호"와 로그인 안내를 먼저 보여준다(사용자 제보: 어차피 409로 막힐 번호에
> 문자부터 와서 혼란+발송 비용 낭비). 단 "어느 계정인지 확인"은 번호 소유 증명 뒤에만 알 수
> 있으므로(existingAccount 규칙), 사용자가 명시적으로 [인증해서 계정 확인]을 누르면 발송을
> 허용한다. BE 변경 없음 — 발송 API 자체는 기존대로(사전 확인이 비었거나 어긋나는 경쟁
> 상황의 최종 방어는 여전히 409).

> v1.26.1 (2026-09-10): **authorize의 400 응답을 브라우저에는 HTML로** (§3-1). 이 API는 항상
> 브라우저(앱은 인앱 브라우저, 웹은 페이지 이동)가 열므로 JSON 400이 나가면 사용자는 API
> 도메인의 날 JSON을 보고 사이트 밖에 갇힌다(웹 실측). 요청 `Accept`에 `text/html`이 있으면
> 같은 400 상태에 사람이 읽는 HTML 오류 페이지(메시지 + 뒤로가기 버튼)를 준다. `Accept`가
> JSON이면 현행 JSON 그대로 — 테스트·curl 무영향. FE 변경 없음.

> v1.26.0 (2026-09-09): **웹 클라이언트 OAuth 복귀를 일회용 코드 교환으로** (웹사이트 추가 — 앱은
> 그대로). BE는 `redirect` 스킴으로 분기: 커스텀 스킴(`kickoff://`·`exp://`)은 현행 토큰 쿼리,
> `http(s)://`는 `?code=<60초·1회용>`만 싣고 FE(웹)가 `POST /api/auth/oauth/exchange`로 토큰을
>받는다(§3-1). 이유: https 복귀 URL의 토큰은 브라우저 히스토리·`Referer`로 새어 나간다
> (refresh 30일). §0 `OAUTH_CODE_INVALID`(401) 신설. 허용 목록은 완전 일치라 웹 복귀 URL은
> 쿼리 없이 `https://<site>/oauth`로 고정하거나 `https://<site>/*`로 등록한다. 기존 앱 빌드 무영향.
> 배포 체크리스트 ⑮(일회용 코드 교환)의 웹 부분 완료 — 앱 쪽 전환은 v2.
>
> v1.25.1 (2026-09-08): §0 에러 코드 `LOGIN_RATE_LIMITED`(429) 신설 — 로그인 실패 반복
> 차단(이메일당 15분 내 10회, 성공 시 해제, 인메모리라 서버 재시작 시 초기화). 부재 중
> 감사 F-8. §7-1 선례(전용 코드 신설)와 같은 이유로 기존 429 코드를 재사용하지 않는다.
>
> v1.25.0 (2026-09-08): **약관·개인정보 동의를 모든 계정에 요구** (부재 중 감사 — FE 발견:
> 소셜 가입자는 동의 절차가 없었음). ① `UserResponse.termsAgreedAt` 노출(nullable, ISO-8601)
> ② `POST /api/users/me/terms-agreement` 신설 — 로그인된 계정의 동의 시각을 지금으로 기록
> ③ **동의 게이트 규칙**: FE는 `termsAgreedAt`이 null이거나 **현행 약관·방침 시행일보다
> 이전**이면 앱 진입 전에 동의 화면을 띄우고, 동의 시 ②를 호출한다 — 소셜 최초 로그인,
> 개정 후 재동의 모두 이 한 규칙으로. 이메일 signup의 `termsAgreed: true`는 그대로.
> ④ 동의 화면에 **만 14세 이상 확인** 체크(클라이언트 확인, 서버 필드 없음 — 방침 §7).
> §9에서 "소셜 가입자의 앱 자체 약관 동의 절차" 제거. 스키마 변경 없음(`User.termsAgreedAt`
> v1.21.0 컬럼 재사용). 약관 v2·개인정보처리방침 v2(시행 2026-09-08)와 동행.
>
> v1.24.3 (2026-09-07): §4 `memberCount` 의미 명문화 — **감독이 직접 입력하는 팀 규모(자기 신고)**이며
> `TeamMember` 명단 수와 **무관**하다(가입 수락·강퇴에도 재계산되지 않음 — 설계). 값·API 무변경, 설명만.
> 화면은 두 값을 같은 이름("인원")으로 보이지 않게 구분해 표기한다. 시드 6팀 전부 신고값 > 명단 수인 것은
> 명단 화면(정렬·빈 상태)을 밟기 위한 표본 구성이지 버그가 아니다.
>
> v1.24.2 (2026-09-07): §0 `VALIDATION_FAILED` 설명 확장 — "요청 본문 검증 실패"뿐 아니라
> **요청 자체를 해석할 수 없는 경우**(깨진 JSON, 쿼리 스트링의 무효 UTF-8 바이트)도 같은
> 코드로 400. 코드 신설 없음, FE 분기 무변경 (깨진 JSON은 이미 이 코드로 나가고 있었다).
> BE의 "무효 바이트 쿼리 → 500" 버그 수정과 동행. 경로 변수의 무효 바이트는 서블릿 도달
> 전에 컨테이너가 400을 내며 **본문이 §0 형식이 아닐 수 있다** — 알려진 예외, 백로그.
>
> v1.24.1 (2026-09-02): 사용자 실기기 피드백 2건 —
> ① 용어: 사용자 노출 "이메일 찾기" → **"아이디 찾기"**, 그 흐름·로그인 화면의 "이메일"
> 표기도 "아이디"로 (값·API·필드명은 이메일 그대로 — v1.14.0-용어와 같은 화면 전용 개정).
> ② 계정 관리 화면 진입에 비밀번호 게이트(이메일 계정만) —
> `POST /api/users/me/verify-password` 신설.
>
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
- 채팅 서버: `kickoff_chat` (Spring Boot 4.1.1 / Java 17 / Gradle / JPA / WebSocket) — §6-1 매칭 채팅만 (v1.31.0)
- FE: `kickoff_fe` (Expo SDK 54 / React Native / expo-router / TypeScript — 앱과 웹이 같은 코드)

## 0. 공통 규약

- Base URL: `http://localhost:8080`
- **채팅 Base URL (v1.31.0)**: 매칭 채팅(§6-1)은 **별도 서버**가 답한다. 개발 `http://localhost:8090`
  (실기기·에뮬레이터는 Base URL과 같은 호스트 규칙에 포트만 8090), 운영은 별도 호스트. FE env
  `EXPO_PUBLIC_CHAT_URL` — 비면 위 개발 규칙. 프리픽스·오류 형식·인증 헤더는 이 절과 같다.
  access token은 BE가 발급한 것을 그대로 쓴다 — 채팅 서버는 토큰을 발급하지 않고, refresh(§3)는 언제나 BE로 한다.
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
| `VALIDATION_FAILED` | 400 | 요청 본문 검증 실패, 또는 요청을 해석할 수 없음(깨진 JSON·쿼리의 무효 UTF-8 바이트) (v1.24.2) |
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
| `POST_HAS_REQUESTS` | 409 | 신청이 하나라도 달린 모집글을 삭제 시도 (v1.31.3). FE·서버 메시지는 "신청 이력이 있는 글은 삭제할 수 없어요(거절·취소된 신청 포함). 더 받지 않으려면 마감으로 바꿔 주세요" (v1.31.4) |
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
| `SQUAD_NOT_FOUND` | 404 | 없는 스쿼드 또는 다른 팀의 스쿼드 id (v1.28.1) |
| `ALREADY_TEAM_ADMIN` | 409 | 이미 관리자거나 소유자 본인을 임명 시도 |
| `JOIN_ALREADY_REQUESTED` | 409 | 이미 대기 중인 가입 신청 있음 |
| `ALREADY_TEAM_MEMBER` | 409 | 이미 팀 소속 (OWNER·ADMIN·MEMBER) |
| `JOIN_NOT_FOUND` | 404 | |
| `JOIN_NOT_PENDING` | 409 | 이미 처리된 가입 신청 |
| `RECORD_NOT_AVAILABLE` | 409 | 기록 불가 상태 (매칭 미성사 또는 경기 전) |
| `RECORD_ALREADY_EXISTS` | 409 | 같은 매칭에 이미 전적 기록함 |
| `TEAM_ADMIN_LIMIT` | 400 | 팀 관리자 5명 초과 |
| `ADMIN_NOT_FOUND` | 404 | |
| `CONSENT_REQUIRED` | 403 | 필수 동의를 마치지 않은 계정의 호출 (v1.34.0, §3 「동의」). FE는 `GET /api/auth/me`를 다시 읽고 동의 화면으로 보낸다 |
| `ACCOUNT_SUSPENDED` | 403 | 이용 제한 중인 계정의 호출 (v1.35.0, §7-2). FE는 이용 제한 안내 화면으로 보낸다 |
| `CONTENT_MODERATED` | 410 | 운영 조치로 가려졌거나 삭제된 콘텐츠 (v1.35.0, §7-2). `message`가 화면에 보일 표시 문구다 |
| `REPORT_ALREADY_EXISTS` | 409 | 같은 대상을 이미 신고했고 아직 처리 중 (v1.35.0) |
| `REPORT_RATE_LIMITED` | 429 | 신고가 지난 24시간에 10건을 넘음 (v1.35.0) |
| `OPERATOR_NOT_SUSPENDABLE` | 409 | 운영자 계정에 이용 제한을 걸려 함 (v1.37.3) |
| `REPORT_NOT_FOUND` | 404 | (v1.35.0, 운영자 API) |
| `OPERATOR_SESSION_REQUIRED` | 401 | 운영자 API를 운영자 세션 없이(또는 만료된 세션으로) 호출 (v1.35.1, §7-2 「운영자 인증」). FE는 OTP 입력으로 보낸다 |
| `OPERATOR_OTP_INVALID` | 401 | OTP가 틀림 (v1.35.1) |
| `OPERATOR_OTP_LOCKED` | 429 | OTP를 연속 5번 틀려 15분 잠김 (v1.35.1) |
| `BLOCK_HAS_UPCOMING_MATCH` | 409 | 차단하려는 팀과 예정된 매칭이 있음 — `cancelUpcomingMatch: true`로 다시 보내야 한다 (v1.35.0) |
| `REQUEST_NOT_ALLOWED` | 409 | 차단 관계라 신청할 수 없음 (v1.35.0). 문구는 차단 사실을 드러내지 않는다 — "지금은 이 팀에 신청할 수 없습니다" |
| `CHAT_CLOSED` | 409 | 매치 일시가 지난 채팅방에 메시지 전송 시도 (v1.35.0: 차단 관계인 방의 전송도 이 코드. v1.35.1: `message`는 사유를 드러내지 않는 "지금은 이 채팅방에 메시지를 보낼 수 없습니다") |
| `CHAT_EXPIRED` | 410 | 보관 기간(경기일로부터 90일)이 지나 삭제된 채팅방 (v1.33.0, §6-1 「보관 기간」). FE는 "보관 기간이 지나 삭제된 대화예요" |
| `CHAT_RATE_LIMITED` | 429 | 채팅 전송이 짧은 시간에 몰림 — 사용자당 10초 10건 초과 (v1.31.0). FE는 "잠시 후 다시 보내 주세요" |
| `CHAT_UPSTREAM_UNAVAILABLE` | 503 | 채팅 서버가 매칭 정보를 확인하지 못함 — BE 무응답·오류 (v1.31.0). FE는 `INTERNAL_ERROR`와 같은 문구로 표시 |
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
| `OAUTH_CODE_INVALID` | 401 | 웹 OAuth 일회용 코드가 없거나 만료(60초)·재사용됨 — 다시 로그인 (v1.26.0) |
| `LOGIN_RATE_LIMITED` | 429 | 로그인 실패가 짧은 시간에 반복되어 잠시 차단 — 이메일당 15분 내 실패 10회 초과, 성공 시 해제 (v1.25.1). FE는 "잠시 후 다시 시도" 안내, 비밀번호 재설정 링크 제시 |
| `PASSWORD_MISMATCH` | 400 | 비밀번호 재확인 불일치 — 회원 탈퇴(§3-4, v1.23.0), 비밀번호 변경의 현재 비밀번호(§3-3), 계정 관리 진입 확인(verify-password) |
| `ACTIVE_MATCH_EXISTS` | 409 | 예정된 매칭이 있는 팀의 소유자가 탈퇴 시도 (v1.23.0) |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 (FE는 "잠시 후 다시 시도해주세요"로 표시) |

참고 사항:
- `timestamp`는 밀리초 정밀도까지만 내려간다 (`…16.384+09:00`)
- 응답의 nullable 규칙: 요청에서 optional인 필드는 응답에서도 `null`일 수 있다.
  `rentalFee`(null이면 FE는 "협의" 표시 — v1.1에서 `costPerTeam`을 대체), `homeGround`, `introduction`, `preferredSkillLevel`, `logoUrl`이 해당.
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
Formation     F11_4_4_2 | F11_4_3_3 | F11_4_2_3_1 | F11_3_5_2 | F11_3_4_3 | F11_5_3_2 | F7_2_3_1 | F7_3_2_1 | F6_2_2_1 | F6_1_3_1   (v1.28.0, §4-4 — 앞 숫자가 총원, GK 포함)
              골키퍼  수비   미드필더  공격

AuthProvider  KAKAO | NAVER | GOOGLE | APPLE
              (v1.3에서는 KAKAO·NAVER만 활성. GOOGLE·APPLE은 값만 예약 —
               비활성 제공자 요청은 400 UNSUPPORTED_PROVIDER)
```

**v1.35.0 추가 (§7-2)**

- `ReportTargetType`: `POST` | `REVIEW` | `TEAM` | `CHAT_MESSAGE`
- `ReportReason`: `ABUSE`(욕설·비방·괴롭힘) | `DEFAMATION`(명예훼손·사생활 침해) | `PERSONAL_INFO`(개인정보 노출) |
  `FALSE_INFO`(허위 정보·사칭) | `FRAUD`(사기·금전 피해) | `NO_SHOW`(경기 불참·약속 불이행) | `SPAM`(스팸·광고·부적절한 내용)
- `ModerationStatus`: `HIDDEN`(임시 가림 — 기한이 있다) | `REMOVED`(운영자 삭제)
- `ReportStatus`: `OPEN` | `TEMP_HIDDEN`(임시 가림 중 — 최종 결정 대기) | `CLOSED`

**v1.36.0 추가 (§8-3)**

- `WeatherCondition`: `CLEAR`(맑음) | `PARTLY_CLOUDY`(구름많음) | `CLOUDY`(흐림) | `RAIN`(비) | `RAIN_SNOW`(비 또는 눈) |
  `SNOW`(눈) | `SHOWER`(소나기) | `UNKNOWN`(예보는 있는데 날씨 글자를 해석하지 못함)
- `WeatherKind`: `HOURLY`(단기예보 — 경기 시각의 값) | `DAILY`(중기예보 — 그날의 최저·최고 기온)

## 2. 공통 응답 오브젝트

### UserResponse
```json
{ "id": 1, "email": "kim@example.com", "nickname": "김주장", "phone": "010-1234-5678",
  "hasTeam": true, "teamId": 3, "authProviders": [],
  "termsAgreedAt": "2026-09-08T10:00:00+09:00", "consentRequired": false, "pendingConsents": [],
  "suspendedUntil": null }
```
- `suspendedUntil` (v1.35.0): 이용 제한이 끝나는 시각. 제한 중이 아니면 `null`(§7-2 「이용 제한」).
- `consentRequired` (v1.34.0): 이 계정이 지금 동의 화면을 거쳐야 하는지 — **서버가 판정한다**(§3 「동의」). FE는
  이 값이 `false`가 **아니면**(true·없음·해석 실패) 동의 화면을 띄운다.
- `pendingConsents` (v1.34.1): 지금 받아야 하는 항목의 키 목록(§3 「동의」의 키). 비어 있으면 `consentRequired`는 `false`다.
  FE 동의 화면은 이 목록의 항목만 보인다. 동의가 필요한데 이 값이 없거나 비어 있으면 가입 화면의 세 항목을 모두 보인다.
- `termsAgreedAt` (v1.25.0): 가입 화면의 세 항목을 모두 마친 가장 최근 시각. 소셜 가입자 등 아직 동의하지
  않은 계정은 `null`. 표시용이다 — 동의 화면을 띄울지는 `consentRequired`로만 정한다(v1.34.0).

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
{ "id": 3, "name": "FC 새벽", "region": "서울 강서구", "homeGround": "우장산축구장",
  "skillLevel": "INTERMEDIATE", "ageGroup": "THIRTIES", "memberCount": 18,
  "introduction": "매주 토요일 오전 7시에 모입니다.", "logoUrl": null,
  "ownerNickname": "김주장", "isMine": false, "createdAt": "2026-08-01T10:00:00+09:00",
  "reviewCount": 4, "averageRating": 4.5,
  "blockedByMe": false, "introductionModeration": null }
```

`blockedByMe`(v1.35.0): 내가 이 팀을 차단했는지(비로그인은 `false`). `introductionModeration`(v1.35.0): 팀 소개가 운영 조치로
가려졌으면 `{ "status", "until" }`이고 그때 `introduction`은 `null`이다(§7-2).

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
{ "email": "kim@example.com", "password": "pass1234", "nickname": "김주장", "phone": "010-1234-5678",
  "consents": { "age14": true, "terms": true, "privacy": true } }
```

- `email` 이메일 형식 필수 / `password` **8~64자 + 영문·숫자·특수문자 각 1개 이상**
  (v1.16.0 — 위반은 400 `VALIDATION_FAILED`, fieldErrors에 규칙 안내. 기존 계정의
  로그인·비밀번호는 소급하지 않는다) / `nickname` 2~20자 필수 / `phone` `010-0000-0000` 형식 필수
- `activityRegion` optional (v1.6.0, 최대 20자) — FE 가입 화면은 선택을 권하지만 건너뛸 수 있다
- **동의 (v1.34.0, v1.34.1)**: 요청에 `consents` 필수 — 세 항목(`age14`·`terms`·`privacy`)이 모두 `true`여야 한다. 아니면 400 `VALIDATION_FAILED`
  (fieldErrors의 `field`는 `consents.age14`처럼 항목별). 옛 `termsAgreed`는 폐지 — 보내도 읽지 않는다. 규칙은 아래 「동의」.
  약관 문서는 `GET /terms`, 개인정보처리방침은 `GET /privacy` (인증 불필요, HTML —
  share-card와 같은 정적 서빙, API 표면 밖이지만 FE가 링크하므로 여기 적는다).
- **유니크 규칙 (v1.16.0)**: `email`(기존), `nickname`, `phone` 모두 중복 불가 —
  409 `EMAIL_ALREADY_EXISTS` / `NICKNAME_ALREADY_EXISTS` / `PHONE_ALREADY_EXISTS`.
  `PATCH /api/users/me`의 nickname·phone 변경에도 같은 규칙(자기 자신의 기존 값은 허용).
  phone은 null(소셜 미등록)을 유니크 대상에서 제외한다

### 동의 (v1.34.0, v1.34.1)

동의는 항목마다 따로 받고(개인정보 보호법 제22조 제1항), 서버가 항목별 시각을 기록하며, 마치기 전에는 서버가 막는다.

| 키 | 뜻 | 성격 | 받는 곳 |
|---|---|---|---|
| `age14` | 만 14세 이상입니다 | 필수 확인 | 가입 화면, 동의 화면 |
| `terms` | 이용약관에 동의합니다 | 필수 동의 | 같음 |
| `privacy` | 개인정보 수집·이용 안내를 확인했습니다 | 필수 **확인**(동의가 아니다 — 가입에 꼭 필요한 정보는 동의 없이 수집하고 알린다) | 같음 |
| `contactSharing` | 매칭이 성사되면 상대 팀 감독에게 내 닉네임·휴대전화 번호가 제공되는 것에 동의합니다(제3자 제공, 제17조) | 필수 동의 — **감독만** | **팀을 만들 때** (`POST /api/teams`) |

- **기록**: 서버는 항목별 시각과, 그때 시행 중이던 약관·방침의 시행일을 저장한다(응답에는 내지 않는다). 탈퇴하면 함께 지운다.
- **`pendingConsents` 판정(서버)**: `age14`·`terms`·`privacy`는 기록이 없으면 받아야 하는 항목이다. 기록이 있어도 **시행일
  설정값이 이미 지난 시각이고(서버 시계) 기록의 동의 시각이 그 시행일보다 이르면** 다시 받아야 한다(v1.35.3 — 설정값이 아직
  오지 않은 날짜면 그날이 될 때까지 아무에게도 재동의를 요구하지 않는다. 그날이 되면 그 전에 동의한 계정이 모두 대상이 되고,
  다시 동의하면 풀린다). 이 설정값은 **재동의가 필요한 개정**(제3자 제공의 상대·목적·항목·기간 변경, 동의받는 항목의 추가 등)
  때만 올린다. `age14`도 같은 기준으로 함께 다시 받는다(한 번 더 묻는 쪽이 안전하다). `contactSharing`은 **팀을 소유한(OWNER) 계정에 한해** 같은 기준으로 본다(팀이 없으면 받을 항목이 아니다).
  `consentRequired`는 이 목록이 비어 있지 않다는 뜻이다. 현행 시행일은 BE 설정값이다(`kickoff.legal.effective-at`, 현재
  `2026-09-08T00:00:00+09:00` — 문서를 개정하면 문서와 함께 BE가 올린다). FE는 시행일을 갖지 않는다(옛 상수
  `TERMS_EFFECTIVE_AT` 폐지). 이 한 규칙이 소셜 최초 로그인, 구조 개편 전 가입자, 개정 후 재동의를 모두 덮는다.
- **서버가 막는다**: `consentRequired`인 계정이 **인증이 필요한 API**를 부르면 403 `CONSENT_REQUIRED`. 예외(동의 전에도
  되는 것)는 `GET /api/auth/me`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `PUT /api/users/me/consents`,
  탈퇴(`DELETE /api/users/me`, `POST /api/users/me/verify-password`)뿐이다. 검사 순서는 인증(401) → 동의(403) → 그 밖의
  검증. 인증이 선택인 조회 API(모집글 목록·상세, 팀 조회 등)는 막지 않는다. 채팅 서버는 이 검사를 하지 않는다(사용자
  정보가 없다 — 채팅은 팀을 만들고 매칭이 수락돼야 닿는 기능이라 동의 없이 처음 들어올 길이 없다).

#### PUT /api/users/me/consents — 인증 필요 (v1.34.0)

```json
{ "age14": true, "terms": true, "privacy": true, "contactSharing": true }
```

보낸 항목을 **지금 시각**으로 기록하고 **204**. 본문에는 `pendingConsents`의 항목이 **모두 `true`로** 들어 있어야 한다 —
빠졌거나 `false`면 400 `VALIDATION_FAILED`(`field`는 항목 키). 받을 항목이 아닌 키는 보내지 않아도 되고, `true`로 보내면
함께 기록한다(`false`는 400). 멱등. 소셜 최초 로그인과 재동의가 이 한 API를 쓴다.
옛 `POST /api/users/me/terms-agreement`는 폐지한다(404).

#### 팀을 만들 때의 연락처 제공 동의 (v1.34.1)

`POST /api/teams` 요청에 **`contactSharingConsent: true` 필수** — 없거나 `false`면 400 `VALIDATION_FAILED`(`field`는
`contactSharingConsent`). 서버는 팀을 만들면서 `contactSharing` 동의 시각을 기록한다. 팀을 만들지 않는 가입자에게는 이 동의를
받지 않는다.

#### FE 규칙

- 가입 화면과 동의 화면 모두 **항목을 따로 보이고 따로 체크**하게 한다. "전체 동의"는 둘 수 있지만 누르기 전에 개별 항목이
  보여야 하고, 미리 체크돼 있으면 안 되며, 하나씩 풀 수 있어야 한다. 전부 체크돼야 다음으로 간다.
- `terms`에는 약관 전문 링크. `privacy`에는 체크 아래 펼침 영역으로 수집 항목·목적·보유 기간·법적 근거 요약과 처리방침
  전문 링크.
- `contactSharing`(팀 만들기 화면의 완료 버튼 위, 그리고 동의 화면에 이 항목이 올 때)에는 **접지 않고** 다섯 가지를 보인다 —
  제공받는 사람, 제공 목적, 제공 항목, 보유·이용 기간, 거부할 권리와 거부 시 불이익(제17조 제2항).
- 문구는 `docs/legal-drafts/consent-and-report-copy.md`(법무 초안)의 1절을 FE 상수로 옮겨 쓴다.
- 로그인 뒤 `consentRequired`가 `false`가 아니면 앱 화면 진입 전에 동의 화면. 어느 API에서든 403 `CONSENT_REQUIRED`를
  받으면 `GET /api/auth/me`를 다시 읽고 동의 화면으로 보낸다. 동의 화면에서는 로그아웃과 탈퇴로 가는 길을 열어 둔다.

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
  (refresh 응답에는 `user` 없음 — 아래 참고). **웹은 `refreshToken`이 본문 대신 HttpOnly 쿠키로 간다** —
  아래 「웹의 토큰 전달」(v1.32.0).
- **로테이션**: refresh를 쓸 때마다 새 refresh가 발급되고 이전 것은 즉시 무효.
  사용자당 활성 refresh token은 **1개** (push token과 같은 단일 기기 정책 —
  새 로그인이 이전 기기의 refresh를 무효화한다)
- 서버는 refresh token을 해시로 저장한다 (유출 대비 원문 비보관)

### 웹의 토큰 전달 (v1.32.0)

브라우저에서 refresh token을 스크립트가 읽을 수 있는 곳(localStorage)에 두면, 페이지에 악성 스크립트가 심겼을 때 30일짜리
토큰을 통째로 잃는다. 그래서 **웹은 refresh token을 HttpOnly 쿠키로만** 주고받는다. 앱(네이티브)은 기기 보안 저장소를
쓰므로 지금 방식 그대로다.

**어느 쪽인지는 클라이언트가 헤더로 밝힌다**: 웹 FE는 아래 다섯 경로에 `X-Kickoff-Client: web`을 붙인다. 헤더가 없거나
다른 값이면 앱으로 보고 v1.31.4와 똑같이 동작한다(본문에 `refreshToken`).

| 경로 | 헤더가 `web`일 때 |
|---|---|
| `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/oauth/exchange` | 응답 본문의 `refreshToken`은 **`null`**, 대신 `Set-Cookie`로 준다. 나머지 본문은 같다 |
| `POST /api/auth/refresh` | **본문 없이** 부른다(본문의 `refreshToken`은 읽지 않는다). 쿠키의 토큰으로 검사·로테이션하고, 응답은 `{ "accessToken": "...", "refreshToken": null }` + 새 쿠키. 쿠키가 없거나 무효면 401 `INVALID_REFRESH_TOKEN` + 쿠키 삭제 |
| `POST /api/auth/logout` | 지금처럼 서버의 refresh token을 폐기하고(204), 추가로 쿠키를 지운다(`Max-Age=0`) |

- 그 밖에 refresh token을 내주는 응답이 있다면(BE가 전수 확인) 같은 규칙을 따른다. 소셜 로그인의 웹 복귀는 이미 일회용
  코드 교환(§3-1)이라 토큰이 주소에 실리지 않는다.
- **쿠키**: 이름 `kickoff_rt`, `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/auth`, `Max-Age` = refresh 수명(30일).
  `Path`를 좁혀 다른 API 요청에는 실리지 않는다 — 일반 API의 인증은 계속 `Authorization` 헤더뿐이다(쿠키로 인증되는
  경로는 refresh·logout 둘).
  - 설정(env): `AUTH_COOKIE_SECURE`(기본 prod `true` / dev `false` — http://localhost), `AUTH_COOKIE_SAMESITE`(기본 `Strict`),
    `AUTH_COOKIE_DOMAIN`(기본 없음). **웹과 API를 같은 사이트(같은 도메인 아래)에 두는 것이 전제다** — 서로 다른 사이트면
    `SameSite=None`이 필요해져 방어가 약해진다. 운영 구성을 그렇게 잡는다(인프라).
- **요청 위조 방어**: 헤더가 `web`인 refresh·logout은 `Origin` 헤더가 허용 오리진(`CORS_ALLOWED_ORIGINS`)에 있어야 한다 —
  없거나 다르면 403 `FORBIDDEN`. 허용 목록이 비어 있는 dev에서는 검사하지 않는다. `X-Kickoff-Client`는 단순 요청에 붙일 수
  없는 헤더라 다른 사이트의 폼 전송으로는 이 경로에 닿지 못하고, `SameSite=Strict`가 그 위의 방어다.
- **CORS**: 허용 오리진에 한해 `Access-Control-Allow-Credentials: true`를 준다(`*`와는 함께 쓸 수 없다). **운영에서는
  `CORS_ALLOWED_ORIGINS`가 반드시 있어야 한다** — 비어 있으면 브라우저가 쿠키를 받지 않아 웹 로그인이 유지되지 않는다.
  허용 헤더에 `X-Kickoff-Client`를 넣는다.
- 정책은 그대로다: 로테이션, 사용자당 활성 refresh token 1개(웹 로그인이 앱의 refresh를 무효화하고 그 반대도 같다),
  서버는 해시만 저장, 로그아웃 뒤에도 access token은 만료(최대 1시간)까지 유효.

**FE(웹) 규칙**
- access token은 **메모리에만** 둔다. localStorage·sessionStorage에 토큰을 쓰지 않는다. 예전에 저장한 토큰 키는 시작할 때 지운다.
- 새로고침·새 탭에서는 시작할 때 `POST /api/auth/refresh`(쿠키 자동 전송)로 access token을 받는다. 401이면 로그아웃 상태다.
  이 한 번은 "로그인하지 않은 방문자"에게도 일어나므로 실패를 오류로 표시하지 않는다.
- 쿠키가 실리게 하는 옵션(`withCredentials`)은 **위 다섯 경로에만** 켠다. 다른 API와 채팅 서버 요청에는 켜지 않는다.
- 여러 탭이 동시에 refresh하면 로테이션 때문에 한쪽이 무효가 된다 — 탭 사이에서 refresh를 한 번에 하나씩만 하게
  직렬화한다(Web Locks. 지원하지 않는 브라우저에서는 지금처럼 탭 안에서만 묶는다).
- 앱(네이티브) 코드는 바뀌지 않는다: 헤더를 보내지 않고, 본문의 `refreshToken`을 기기 보안 저장소에 둔다.
- 채팅 서버(§6-1)는 쿠키를 쓰지 않는다 — REST와 소켓 `AUTH` 모두 메모리의 access token을 쓴다.

### POST /api/auth/refresh — 인증 불필요 (v1.7.0)

`{ "refreshToken": "..." }` → 200 `{ "accessToken": "...", "refreshToken": "..." }`
(새 쌍. user는 없다 — FE는 필요하면 /auth/me로).
만료·무효·로테이션된 토큰이면 401 `INVALID_REFRESH_TOKEN` — FE는 이때만 로그인
화면으로 보낸다 (access 만료 401과 구분).
웹은 본문 없이 쿠키로 부른다 — 위 「웹의 토큰 전달」(v1.32.0).

### POST /api/auth/logout — 인증 필요 (v1.7.0)

서버의 refresh token 폐기. 204. FE는 이어서 로컬 토큰·push token(null)을 정리한다. 웹은 서버가 쿠키도 지운다(v1.32.0).
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
BE ─302→ {redirect}?token=<accessToken>&refreshToken=<…>&isNewUser=true|false   ← redirect가 커스텀 스킴(kickoff://, exp://)일 때 (앱, 현행)
BE ─302→ {redirect}?code=<일회용 코드>&isNewUser=true|false                         ← redirect가 http(s)://일 때 (웹, v1.26.0)
        (실패 시 어느 쪽이든 {redirect}?error=<에러코드>)
FE(앱): token 저장 → GET /api/auth/me
FE(웹): POST /api/auth/oauth/exchange {code} → 토큰 수령 → 주소창 쿼리 즉시 제거 → GET /api/auth/me
```

**웹 복귀는 토큰을 URL에 싣지 않는다 (v1.26.0)**: `https://` 복귀 URL에 토큰을 쿼리로 실으면
브라우저 히스토리·`Referer` 헤더·동기화·링크 복사로 refresh 토큰(30일)이 새어 나간다 —
앱의 딥링크와 달리 실제 경로다. 그래서 BE는 **redirect의 스킴으로 분기**한다: 커스텀
스킴이면 현행(토큰 쿼리), `http`/`https`면 **일회용 코드**만 싣고 FE가 아래 API로 교환한다.
기존 앱 빌드는 영향 없다.

### POST /api/auth/oauth/exchange — 인증 불필요 (v1.26.0, 웹 전용)

요청 `{ "code": "<콜백 쿼리의 code>" }` → 200 `{ "accessToken", "refreshToken", "isNewUser" }`
(login 응답의 토큰 부분과 같은 형태). 코드는 **발급 후 60초·1회용**이며 BE 메모리에만 둔다
(state와 같은 방식 — 서버 재시작 시 소멸, 그러면 다시 로그인). 없거나 만료·재사용이면
401 `OAUTH_CODE_INVALID`. FE(웹)는 교환 성공 즉시 다음 화면으로 `replace` 이동해 주소창의
`code` 쿼리가 히스토리에 남지 않게 하고, 복귀 페이지에서는 외부 자원(이미지·폰트·스크립트)을
불러오지 않는다(`Referer` 누출 경로 차단 — FE 실측 확인). 교환 **실패** 시에는 코드가 이미
만료·소진된 값이라 주소에 남아도 무해하며, 실패 안내를 지우지 않는 쪽을 우선한다
(expo-router가 라우트 상태로 주소를 되쓰는 특성 때문에 `replaceState`만으로는 지워지지 않음).

### GET /api/auth/oauth/{provider}/authorize — 인증 불필요

- `provider` 경로값: `kakao` | `naver` (소문자)
- **검사 순서** (v1.3.3): ① `redirect` 허용 목록 검증 — 실패 시 400 JSON (아래).
  ② 그 밖의 모든 실패(비활성 제공자 등)는 이 API를 인앱 브라우저가 열기 때문에 JSON이
  FE에 닿지 않는다 — 검증된 `redirect`로 `{redirect}?error=UNSUPPORTED_PROVIDER` 302 한다
- `redirect` 쿼리 필수: 완료 후 돌아갈 URL. **BE의 허용 목록**과 대조해 통과한 것만 쓴다
  (open redirect 방지, **프리픽스 매칭**). dev 허용 목록: `exp://*`, `kickoff://*`, `http://localhost:*`
- 허용 목록 밖 redirect는 **302 하지 않고** 400 `VALIDATION_FAILED`
  (`fieldErrors[0].field = "redirect"`) — 신뢰하지 않는 주소로 되돌려 보내지 않는다 (v1.3.1)
- **브라우저 요청의 400은 HTML** (v1.26.1): 요청 `Accept`에 `text/html`이 포함되면 위 400을
  JSON 대신 HTML 오류 페이지로 준다 — 상태 코드 400 동일, `Content-Type: text/html; charset=UTF-8`,
  본문은 한국어 안내 한 줄("허용되지 않은 복귀 주소입니다. 앱이나 사이트로 돌아가 다시 시도해
  주세요.")과 `history.back()` 버튼. 페이지는 외부 자원을 불러오지 않고, `redirect` 값을 링크로
  쓰지 않는다(허용 목록 밖 주소라 open redirect가 된다). `Accept`가 `application/json`이거나
  없으면 현행 JSON — 다른 API의 오류 형식은 바뀌지 않는다
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

**토큰을 쿼리로 전달하는 것은 dev 한정 허용.** 웹은 v1.26.0에서 이미 일회용 코드 교환으로 바뀌었다.
앱(딥링크 복귀)은 아직 쿼리 전달이며, 운영 배포 단계에서 같은 방식으로 강화한다.

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
- FE (v1.26.2): availability가 그 번호를 **사용 중**이라고 알고 있으면 **발송 버튼 단계에서**
  막는다 — "이미 가입에 사용된 전화번호입니다"와 로그인(게이트 화면에서는 로그아웃 후 로그인)
  안내를 먼저. [인증해서 계정 확인] 명시 동작으로만 발송을 허용한다(가입 수단·마스킹 이메일은
  소유 증명 뒤에만 알 수 있으므로 이 경로는 남긴다)
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

### POST /api/users/me/verify-password — 인증 필요 (v1.24.1)

본인 확인용 비밀번호 검증 — 상태를 바꾸지 않는다. 계정 관리 화면 진입 게이트가 용도.

```json
{ "password": "..." }
```

- 일치 204 / 불일치 400 `PASSWORD_MISMATCH` (§3-4와 동일 코드 — 401 금지 이유 동일)
- 소셜 계정(비밀번호 없음) 400 `VALIDATION_FAILED` — 정상 경로에서는 안 생긴다:
  **FE는 소셜 계정에 게이트를 적용하지 않는다** (물을 비밀번호가 없다)
- `currentPassword` 무형식 원칙 동일 (§ PATCH password 참고 — 옛 규칙 비밀번호도 통과)
- FE 규칙: 마이 탭 → 계정 관리 진입 시 이메일 계정이면 비밀번호 입력 → 204일 때만
  화면 진입. 캐시 없음 — 진입할 때마다 묻는다 (탈퇴·비밀번호 변경이 있는 화면이라
  보수적으로). 불일치 문구는 "비밀번호가 일치하지 않아요" 재사용

## 3-5. 이메일(아이디) 찾기 (v1.24.0)

> (v1.24.1 용어) 사용자 노출 명칭은 **"아이디 찾기"**다 — 이 흐름과 로그인 화면에서
> "이메일"로 쓰던 표기를 "아이디"로 바꾼다(사용자 결정). 값은 이메일 주소 그대로이고
> API·필드명·검증은 무변경. 입력 칸 등 형식 혼동이 생길 자리는 "아이디(이메일)" 병기
> 허용 — FE 재량.

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
{ "name": "FC 새벽", "region": "서울 강서구", "homeGround": "우장산축구장",
  "skillLevel": "INTERMEDIATE", "ageGroup": "THIRTIES", "memberCount": 18,
  "introduction": "매주 토요일 오전 7시에 모입니다.", "contactSharingConsent": true }
```

- `contactSharingConsent` (v1.34.1): `true` 필수 — §3 「동의」의 연락처 제공 동의. 팀 정보에는 저장되지 않고 응답에도 없다
- `name` 2~30자 필수 / `region` 필수 / `skillLevel`, `ageGroup` 필수 / `memberCount` 1~100 필수 / `introduction` 최대 1000자
- `memberCount`는 **감독이 신고하는 팀 규모**다 (v1.24.3). 팀원 명단(`TeamMember`, §4-1) 수와 무관하며 가입 수락·강퇴에도 재계산되지 않는다. FE는 이 값을 명단 수와 같은 이름으로 표기하지 않는다 — 예: "팀 규모 15명" vs "등록 팀원 0명".
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

## 4-4. 스쿼드 메이커 (v1.28.0)

팀이 경기 전에 선발·교체를 짜 두는 보드. 한 팀에 여러 스쿼드를 저장한다("10/12 vs 마포" 식).
**공유는 FE 몫**: 경기장 그림을 이미지(PNG)로 캡처해 OS 공유창으로 보낸다(앱 `expo-sharing`
→ 카톡·문자·갤러리, 웹은 Web Share API(files) 지원 시 공유창, 아니면 다운로드). 서버는 이미지를
만들지도 저장하지도 않는다(링크 공유·서버 렌더링은 §9 v2).

### 자리 배치 규칙

- `formation`(§1 Formation)이 총원 N을 정한다. `slots`는 **정확히 N개**, `slot` 0은 GK,
  1..N-1은 포메이션 줄 순서(수비→미드→공격, 각 줄 왼쪽→오른쪽). 자리 좌표는 FE가 포메이션별로
  고정 테이블을 가진다(서버는 좌표를 모른다).
- 각 자리/교체 항목은 `memberId`(§4-1 TeamMember, 이 팀 소속) **또는** `name`(1~20자, 게스트
  등 명단 밖 사람) 중 하나. 둘 다 null이면 **빈 자리**(허용 — 짜다 만 스쿼드도 저장된다).
  둘 다 있으면 `memberId`가 이긴다. 같은 `memberId`가 선발+교체 통틀어 두 번 나오면 400
  `VALIDATION_FAILED`(`fieldErrors[].field` = `slots[i].memberId` / `bench[i].memberId`, 두 번째 등장 자리).
- `bench`(교체) 0~10명, 같은 항목 구조, `slot` 없음.
- 응답의 `name`은 memberId가 있으면 **현재 명단 이름**, 그 팀원이 명단에서 지워졌으면 저장 시점
  이름(스냅샷)을 돌려주고 `memberId`는 null로 내려간다(스쿼드는 과거 기록으로 남는다).
- 한 팀 최대 **30개**. 초과 시 400 `VALIDATION_FAILED`(field `squads`, "오래된 스쿼드를 지워 주세요") — **생성(POST)에만** 적용, 수정(PUT)은 상한에서도 된다(v1.28.1).

### 권한

읽기 = 그 팀 소속 전원(OWNER·ADMIN·MEMBER, §4-3) — 비로그인 401 `UNAUTHORIZED`, 로그인했지만 비소속 403 `FORBIDDEN` (v1.28.1).
쓰기(생성·수정·삭제) = OWNER·ADMIN(§4-2 권한표의 "팀원 명단·경기 기록 쓰기"와 같은 줄).

### 오브젝트

```json
{ "squadId": 3, "teamId": 7, "title": "10/12 vs 마포 유나이티드", "formation": "F11_4_3_3",
  "slots": [ { "slot": 0, "memberId": 5, "name": "김철수" }, { "slot": 1, "memberId": null, "name": "게스트 민수" },
             { "slot": 2, "memberId": null, "name": null } ],
  "bench": [ { "memberId": 9, "name": "박영수" } ],
  "createdAt": "…", "updatedAt": "…" }
```
- `title` 1~30자 필수.

### API

- `GET /api/teams/{teamId}/squads` — 인증 필요(소속). 200 → `SquadSummary[]`(squadId·title·formation·
  updatedAt), `updatedAt` 내림차순, 페이지 없음(최대 30).
- `POST /api/teams/{teamId}/squads` — OWNER·ADMIN. 본문 `{ title, formation, slots[], bench[] }`
  (slots/bench 항목은 `{ slot?, memberId?, name? }`). 201 → 오브젝트.
- `GET /api/teams/{teamId}/squads/{squadId}` — 소속. 200 → 오브젝트. 없거나 다른 팀 것이면 404 `SQUAD_NOT_FOUND`.
- `PUT /api/teams/{teamId}/squads/{squadId}` — OWNER·ADMIN. 본문 POST와 동일, **전체 교체**
  (부분 수정 없음 — 보드 하나를 통째로 저장하는 UI라 PATCH 의미가 없다). 200 → 오브젝트.
- `DELETE /api/teams/{teamId}/squads/{squadId}` — OWNER·ADMIN. 204.
- 다른 팀의 memberId를 넣으면 400 `VALIDATION_FAILED`(field `slots[i].memberId`, "이 팀의 팀원이 아닙니다").
- 팀 삭제·소유자 탈퇴 시 스쿼드도 함께 지운다(cascade).


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
{ "id": 12, "title": "토요일 아침 상대 팀 구합니다", "matchAt": "2026-08-30T07:00:00+09:00",
  "location": "우장산축구장", "locationAddress": "서울 강서구 우장산로 72", "region": "서울 강서구",
  "preferredSkillLevel": "INTERMEDIATE", "rentalFee": 100000, "depositAmount": 50000,
  "status": "OPEN", "requestCount": 3, "team": "<TeamSummary>",
  "weather": null,
  "createdAt": "2026-08-23T09:00:00+09:00" }
```

`weather`(v1.36.0): 경기 시각의 예상 날씨, 없으면 `null` — §8-3. `PostDetail`·`RequestResponse`에도 같은 필드가 있다.

비용 필드 의미 (v1.1에서 `costPerTeam` 대체):
- `rentalFee`: 글 작성 팀이 낸 **총 구장 대여료** (optional, 정보 표시용)
- `depositAmount`: **매칭 확정 시 상대 팀이 작성 팀에게 보낼 금액** (optional, 0 이상. null이면 "협의")

### POST /api/posts — 인증 필요, 팀 보유 필수

```json
{ "title": "토요일 아침 상대 팀 구합니다", "content": "11대11로 2시간 뛸 팀 찾습니다. 매너 중요.",
  "matchAt": "2026-08-30T07:00:00+09:00", "location": "우장산축구장",
  "locationAddress": "서울 강서구 우장산로 72",
  "region": "서울 강서구", "preferredSkillLevel": "INTERMEDIATE",
  "rentalFee": 100000, "depositAmount": 50000,
  "bankName": "카카오뱅크", "accountNumber": "3333-01-1234567", "accountHolder": "김주장",
  "paymentShareConfirmed": true }
```

- `title` 2~60자 필수 / `content` 최대 2000자 필수 / `matchAt` 미래 시각 필수 / `location`, `region` 필수 / `locationAddress` optional 최대 100자(**사용자가 직접 적은 주소만** — v1.37.0: 장소 검색 결과로 자동으로 채우지 않는다, §5-1) / `preferredSkillLevel` optional(null이면 무관) / `rentalFee`, `depositAmount` 0 이상 optional / `fieldType`은 v1.19.0 폐지 — 와도 무시
- `bankName`(최대 20자), `accountNumber`(최대 30자), `accountHolder`(최대 20자): **입금받을 계좌** — 전부 optional이지만 `depositAmount`를 넣으면 세 필드 모두 필수 (400 `VALIDATION_FAILED`)
- 계좌 3필드는 **절대 목록/상세에 공개되지 않는다.** 오직 수락된 신청 팀에게만 `payment` 오브젝트로 내려간다 (아래 참고)
- **`paymentShareConfirmed` (v1.34.0)**: 요청 본문에 계좌 3필드 중 하나라도 값(null 아님)이 있으면 `true` 필수 — 아니면 400
  `VALIDATION_FAILED`(`field`는 `paymentShareConfirmed`). "이 계좌 정보는 매칭이 수락된 상대 팀 감독에게 보인다"는 확인이다.
  POST·PATCH 공통이고 **요청 본문 기준**이다(계좌를 건드리지 않는 PATCH에는 필요 없다). 서버는 확인 시각을 글에 기록한다
  (응답 비노출). FE는 계좌 입력 칸 옆에 체크 한 줄을 두고, 계좌를 적었는데 체크가 없으면 제출을 막는다.
- 계좌 3필드는 **암호화해 저장한다 (v1.34.0)** — BE env `ACCOUNT_ENC_KEY`. 요청·응답과 길이 검증(평문 기준)은 그대로다.
- 팀 없으면 400 `TEAM_REQUIRED`
- 201 → `PostDetail`

### GET /api/posts/{postId} — 인증 불필요 (토큰 있으면 추가 필드 채움)

```json
{ "id": 12, "title": "...", "content": "...", "matchAt": "2026-08-30T07:00:00+09:00",
  "location": "우장산축구장", "locationAddress": "서울 강서구 우장산로 72", "region": "서울 강서구",
  "preferredSkillLevel": "INTERMEDIATE", "rentalFee": 100000, "depositAmount": 50000,
  "status": "OPEN", "viewCount": 42, "requestCount": 3, "team": "<TeamResponse>",
  "isAuthor": false, "myRequestStatus": null, "contact": null, "payment": null,
  "weather": "<Weather | null — §8-3>",
  "createdAt": "2026-08-23T09:00:00+09:00" }
```

- `isAuthor`: 내 팀이 쓴 글인지. 비로그인이면 `false`
- `myRequestStatus`: 내 팀이 이 글에 보낸 신청 상태(`RequestStatus`). 신청 안 했거나 비로그인이면 `null`
- `contact`: **매칭 수락된 두 팀에게만** 공개. 그 외 전부 `null`
- **표시 기간 (v1.34.1)**: 상대 팀에게 보이는 `contact`와 `payment`는 **경기일(`matchAt`) + 14일까지**다. 그 뒤에는 수락된
  매칭이어도 `null`이다(서버 시계, BE 설정 `kickoff.match.contact-visible-days` 기본 14). 글 작성자 본인이 자기 글에서 보는
  `payment`(자기 계좌 확인·수정용)는 기간과 무관하다. `RequestResponse`의 `contact`·`payment`도 같은 규칙이다. FE는 값이
  `null`이면 연락처·계좌 영역을 감추고 "경기 후 14일이 지나 연락처가 더 이상 표시되지 않아요"를 보일 수 있다(숫자는
  `GET /api/auth/signup-policy`처럼 따로 내려 주지 않는다 — 문구에 14를 적는다. 바뀌면 계약과 함께 고친다).

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
- `locationAddress`는 명시적 null로 지울 수 있다(v1.29.1) — 생략하면 유지

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

- **신청이 하나라도 달린 글은 삭제할 수 없다 (v1.31.3)** — 409 `POST_HAS_REQUESTS`. 신청의 상태를 가리지 않는다
  (대기·수락·거절·철회·매칭 취소 전부 — 상대 팀의 신청 이력과 리뷰·전적의 근거가 그 글에 묶여 있다).
  더 받지 않으려면 `PATCH`로 `status`를 `CLOSED`(마감)로 바꾼다. FE는 삭제 실패 안내에 그 길을 함께 보여 준다.
- 검사 순서: 없는 글 404 `POST_NOT_FOUND` → 작성자 아님 403 `FORBIDDEN` → 신청 있음 409.
- 신청이 없는 글은 지금처럼 204로 지워진다.
- **`requestCount`와 기준이 다르다 (v1.31.4)**: 목록·상세의 `requestCount`는 대기·수락만 세지만 삭제는 모든 상태의 신청이
  막는다. "신청 0건"으로 보이는 글도 거절·취소된 신청이 있으면 409다 — 그래서 안내 문구에 "이력"과 "거절·취소 포함"을 넣는다.

### GET /api/posts/me — 인증 필요

내 팀이 작성한 글 목록. 200 → `PageResponse<PostSummary>`
- 정렬: `createdAt` DESC (방금 쓴 글이 위로 — 홈 목록과 다름)
- 팀이 없으면 에러가 아니라 **빈 페이지** 반환

### requestCount 정의 (PostSummary/PostDetail 공통)

살아 있는 신청(`PENDING` + `ACCEPTED`)만 센다. 취소·거절된 신청은 제외.

## 5-1. 장소 검색·지도 (v1.5.0, v1.37.0 전면 개정)

카카오 로컬 검색의 응답(장소 이름·주소·좌표, 그것을 가공한 값)은 **저장할 수 없다.** 저장해도 되는 것은 **장소 ID와 그 ID로 만든
카카오맵 주소**, 그리고 **사용자가 직접 입력한 글자**뿐이다(카카오 운영자 답변 2026-07~10 — `docs/research/kakao-place-policy-2026-11.md`).
검색 결과는 화면에 그때그때 보여 주는 데만 쓴다. 이 절의 규칙은 전부 여기서 나온다.

### 모집글의 장소 필드

| 필드 | 어디에 | 내용 |
|---|---|---|
| `location` | 요청·`PostSummary`·`PostDetail` | 장소 이름. **사용자가 직접 적은 글자**(필수, 기존 그대로) |
| `locationAddress` | 〃 | 주소. 사용자가 직접 적은 글자(optional, 최대 100자) |
| `region` | 〃 | 지역("서울 강서구"). 사용자가 직접 고르거나 적은 값(필수) |
| `kakaoPlaceId` | 요청·`PostDetail` | 사용자가 검색 결과에서 고른 장소의 카카오 장소 ID. 숫자로만 된 문자열 1~20자, nullable |
| `placeQuery` | 요청·`PostDetail` | 그 장소를 찾을 때 **사용자가 친 검색어**(앞뒤 공백 제거 뒤 1~100자), nullable |
| ~~`latitude`·`longitude`~~ | — | **v1.37.0 폐지.** 요청에 와도 무시하고 저장하지 않는다. 응답에서 사라진다 |

- `kakaoPlaceId`와 `placeQuery`는 **쌍으로만** 온다 — 하나만 값이 있으면 400 `VALIDATION_FAILED`. 숫자가 아닌 ID도 400.
- **PATCH도 요청 본문 기준으로 쌍 검증한다.** 둘 다 안 보내면 기존 값 유지, 지우려면 둘 다 `null`을 보낸다.
- 서버는 이 두 값을 카카오에 확인하지 않는다(ID로 조회하는 API가 없다). 형식만 본다.
- `PostSummary`에는 싣지 않는다(목록은 지도를 그리지 않는다).
- **이전 데이터**: 마이그레이션에서 좌표 두 컬럼을 없애고, 기존 글의 `locationAddress`를 전부 `null`로 지운다(자동으로 채워진 카카오
  주소와 직접 적은 주소를 구별할 수 없다). 시드도 같이 고친다 — 시드의 장소에는 실제 카카오 응답을 옮겨 적지 않는다.

### GET /api/places/search — 인증 필요 (v1.5.0)

카카오 로컬 키워드 검색의 **BE 대행.** 검색 키를 앱에 넣지 않으려고 BE가 대신 부른다. FE는 글 작성/수정의 "지도에서 찾기"에서
이걸 호출한다.

쿼리: `query` (필수, 1~100자), `size` (기본 10, 최대 15)

200 응답:

```json
{ "places": [
  { "id": "8245930", "name": "우장산축구장", "address": "서울 강서구 화곡동 산 60-1",
    "roadAddress": "서울 강서구 우장산로 72" }
] }
```

- `id`(v1.37.0): 카카오 장소 ID(문자열). 카카오가 ID를 주지 않은 줄은 응답에서 뺀다.
- `latitude`·`longitude`는 **v1.37.1에서 뺐다** — 고르는 화면은 이름·주소만 보이고, 미리보기 지도는 아래 「지도 그리기」와 같은
  방법으로 기기에서 다시 조회해 그린다.
- 결과 없으면 `{ "places": [] }` (에러 아님). `roadAddress`는 nullable
- 카카오 API 실패 시 502 `PLACE_SEARCH_FAILED` (FE는 "장소 검색에 실패했습니다. 직접 입력해 주세요"로 안내하고 직접 입력 경로를 열어둔다)
- **BE는 이 응답을 저장하지 않는다** — DB·캐시·로그 어디에도(요청 검색어와 결과 개수만 로그에 남길 수 있다).
- BE 환경변수 `KAKAO_MAP_REST_KEY`. FE의 지도 JS SDK 키(`EXPO_PUBLIC_KAKAO_MAP_JS_KEY`)와 같은 카카오 앱의 키여야 한다 — 장소 ID가
  같은 앱 안에서 쓰이게. (지도용 카카오 앱을 로그인용과 나누는 것을 권한다 — 정책 문제로 앱이 멈춰도 로그인이 같이 멈추지 않게.)

### FE 규칙 — 글 쓰기·고치기

- 장소 이름·주소·지역 칸은 **사용자가 적는다.** 검색 결과를 골라도 그 결과의 이름·주소·지역으로 칸을 **채우거나 바꾸지 않는다**
  (v1.29.0의 주소 자동 채움, 주소에서 지역을 잘라 넣던 동작을 없앤다).
- "지도에서 찾기": 사용자가 친 검색어로 `GET /api/places/search`를 부르고 결과 목록을 보여 준다(이름·주소는 고르는 동안 화면에만).
  하나를 고르면 `kakaoPlaceId` = 그 줄의 `id`, `placeQuery` = **그때 검색 칸에 있던 사용자의 글자**로 둔다. 장소 이름 칸이 비어
  있었다면 **사용자가 친 검색어**를 이름 칸에 옮겨 줄 수 있다(사용자의 글자이므로 된다 — 결과의 이름이 아니다).
- 고른 뒤에는 "지도 연결됨"과 미리보기 지도를 보여 주고(아래 「지도 그리기」와 같은 방법), "연결 끊기"로 두 값을 `null`로 되돌릴 수 있다.
  검색어를 고쳐 다시 찾으면 새로 고르기 전까지 기존 연결은 그대로다.
- 지역: 시/도는 17개 목록에서 고르고 시군구는 직접 적는다(합쳐서 "서울 강서구"). 처음 값은 내 팀의 지역.
- 검색 결과의 좌표·이름·주소를 기기 저장소(AsyncStorage·localStorage 등)나 서버에 남기지 않는다. 화면 메모리에만 둔다.
- 검색 결과 줄에 이름·주소를 **복사하거나 칸에 넣어 주는 도우미를 두지 않는다**(복사 버튼, "이 주소 쓰기" 등 — v1.37.1). 한 번
  누르면 채워지는 버튼은 자동 채움과 같은 결과다. 사용자가 보고 손으로 적는 것은 사용자의 입력이다.

### FE 규칙 — 지도 그리기 (글 상세)

- `kakaoPlaceId`가 없으면 지도 영역을 그리지 않는다.
- 있으면 **보는 사람의 기기에서** 카카오 지도 JS SDK의 장소 검색(`libraries=services`의 키워드 검색)을 `placeQuery`로 부른다. 결과에서
  `id`가 `kakaoPlaceId`와 같은 줄을 찾아 그 좌표에 지도와 핀을 그린다. 첫 쪽에 없으면 세 쪽(45건)까지 넘겨 본다.
- 5초 안에 못 찾거나 실패하면 지도를 감추고 버튼만 남긴다. 오류 문구는 띄우지 않는다.
- 지도 아래(또는 지도 대신) **"카카오맵에서 보기"** 버튼: `https://place.map.kakao.com/{kakaoPlaceId}`를 새 창/브라우저로 연다. 이 버튼은
  조회 결과와 무관하게 `kakaoPlaceId`가 있으면 항상 보인다.
- 이렇게 얻은 좌표는 그 화면이 떠 있는 동안만 쓴다. 저장·전송하지 않는다(BE로 보내지 않는다).
- 이 조회는 서버를 거치지 않는다. 카카오 하루 호출 한도를 넘으면 지도가 빠지고 버튼만 남는 것으로 끝난다.

## 6. 매칭 신청

### RequestResponse

```json
{ "id": 7, "postId": 12, "postTitle": "토요일 아침 상대 팀 구합니다",
  "postStatus": "OPEN", "matchAt": "2026-08-30T07:00:00+09:00",
  "applicantTeam": "<TeamSummary>", "postTeam": "<TeamSummary>",
  "message": "저희도 강서구라 가깝습니다!",
  "status": "PENDING", "contact": null, "payment": null, "depositPaid": false,
  "myReviewWritten": false, "weather": null,
  "createdAt": "2026-08-23T11:00:00+09:00" }
```

`weather`(v1.36.0): 신청이 걸린 글의 경기 시각·지역으로 구한 예상 날씨, 없으면 `null` — §8-3. 신청 상태와 무관하게 같은 규칙이다.

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

## 6-1. 매칭 채팅 (v1.12.0, v1.31.0 서버 분리)

수락된 매칭(`ACCEPTED` 신청)마다 채팅방이 하나 있다. 별도의 방 리소스는 없다 —
**`requestId`가 곧 방 식별자**이고, 방은 수락 시점부터 암묵적으로 존재한다.

- **참여자는 두 팀의 감독(OWNER)뿐이다** — 접근 시점 기준 글 작성 팀 OWNER와 신청 팀
  OWNER. ADMIN·MEMBER는 접근 불가 (사용자 결정: 일정 조율 채널이므로 1:1).
- **전송은 `matchAt`까지만.** 매치 일시가 지나면 방은 **읽기 전용**이 되고, **경기일로부터 90일간 보관한 뒤
  삭제**된다 (v1.33.0 — 계좌·장소 등 주고받은 정보를 한동안 다시 볼 수 있어야 하지만, 목적이 끝난 대화를 무기한
  두지 않는다. 아래 「보관 기간」).
- **채팅은 별도 서버가 답한다 (v1.31.0).** 이 절의 REST 4개와 WebSocket은 전부 **채팅 Base URL**(§0)로
  부른다. 경로·요청·응답·거절 규칙은 v1.30.1과 같다. 고객센터 문의(§7-1)는 해당 없음 — BE가 답한다.
  **BE에는 이 절의 API가 없다 (v1.31.2 — 전환 완료).** BE에 같은 경로를 부르면 토큰 없이 401, 토큰 있으면 404
  `NOT_FOUND`다. 401만으로 "옛 서버"라고 판정하지 말 것(§0 — 401은 필터 단계, 404가 라우팅 단계).
- **전송 방식 (v1.31.0)**: 받기는 **WebSocket**(아래 「실시간 연결」), 보내기는 REST `POST`. WebSocket을 쓸 수
  없을 때 FE는 `GET ?after=` 폴링(5초)으로 강등한다 — 같은 API라 서버 쪽 차이는 없다.
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
{ "messages": [ "<ChatMessage>", "..." ], "chatOpen": true,
  "deleteAt": "2026-11-28T07:00:00+09:00" }
```

- `messages`는 **오름차순**(오래된 것부터). `after` 없으면 **최신 `limit`개**를
  오름차순으로 (첫 로드용). `after`가 있으면 그 이후 것만 (폴링용).
- `chatOpen`: 지금 전송 가능한지 (**서버 시계** 기준 `matchAt` 전인지). FE는 이 값으로
  입력창을 잠근다 — 클라이언트 시계로 판정하지 않는다.
- `deleteAt` (v1.33.0): 이 방의 대화가 삭제되는 시각 = `matchAt` + 보관 기간(90일). 경기 전에도 내려 준다. 보관 기간
  정리가 꺼진 서버에서는 `null`(「보관 기간」). FE는 이 값을 표시만 한다 — 90이라는 숫자를 FE에 적지 않는다.
- 거절 케이스: 당사자 팀 OWNER 아님 403 `FORBIDDEN` (팀 없는 사용자·ADMIN·MEMBER 포함,
  §7 리뷰와 같은 규칙) / 신청이 `ACCEPTED` 아님 409 `REQUEST_NOT_ACCEPTED` /
  없는 신청 404 `REQUEST_NOT_FOUND`. 검사 순서는 **권한 먼저** (§7과 동일 —
  제3자에게 매칭 상태를 알려주지 않는다).
- v1.33.0: 위 검사를 모두 통과했는데 `deleteAt`이 지났으면 410 `CHAT_EXPIRED` — 이 절의 POST·leave·구독도 같다
  (「보관 기간」).

### POST /api/requests/{requestId}/chat — 인증 필요, 매칭 당사자 팀 OWNER만

```json
{ "content": "그럼 7시까지 구장에서 뵙겠습니다!" }
```

- `content` 1~500자 필수 (공백만인 것 불가)
- 거절 케이스: GET과 동일 + `matchAt`이 지났으면 409 `CHAT_CLOSED` (보관 기간까지 지났으면 410 `CHAT_EXPIRED`가 먼저다)
- 201 → `ChatMessage`
- 수정·삭제는 없다 (§9)
- v1.31.0: 사용자당 10초 10건을 넘으면 429 `CHAT_RATE_LIMITED`(v1.31.1: 실패한 시도도 센다 — 성공 건수가 아니라 요청 건수). 채팅 서버가 BE에서 매칭 정보를 확인하지
  못하면 503 `CHAT_UPSTREAM_UNAVAILABLE` — 이 절의 GET·leave·방 목록도 같다.

### 실시간 연결 (v1.31.0)

#### `GET /ws` — WebSocket 업그레이드, 채팅 Base URL

- 주소: `ws://localhost:8090/ws`(개발), `wss://<채팅 호스트>/ws`(운영 — 평문 `ws://`는 개발 전용).
  **토큰을 주소에 싣지 않는다**(쿼리 문자열·서브프로토콜 모두 금지 — 접근 로그에 남는다).
- 모든 메시지는 UTF-8 JSON 텍스트 프레임, 공통 봉투 `{ "type": "...", ... }`. 클라이언트가 보내는 프레임은 최대 4KB.
- 연결은 **사용자 단위**다(방마다 따로 열지 않는다 — 한 연결로 여러 방을 구독할 수 있다). **FE는 채팅방 화면이
  떠 있는 동안만 연결한다 (v1.31.1)** — 로그인 상태 + 앱 포그라운드 + 채팅방 화면. 화면을 떠나거나 앱이 백그라운드로
  가면(웹은 탭이 숨으면) 닫는다. 숨은 화면이 구독을 쥐고 있으면 아래 푸시 생략 규칙 때문에 알림이 조용히 사라진다.
  사용자당 동시 연결은 5개까지 — 넘으면 가장 오래된 연결이 **4008**로 닫힌다.
- 소켓은 **받기 전용**이다. 메시지 보내기는 아래 REST `POST`뿐이다(오류 모델·401 재시도·폴백을 한 경로로 유지).

**1) 인증 — 연결 후 10초 안에 첫 메시지로**

```json
→ { "type": "AUTH", "token": "<accessToken>" }
← { "type": "AUTH_OK", "userId": 1, "expiresAt": "2026-11-15T18:00:00+09:00" }
```

- 토큰 없음·위조·만료, 또는 10초 안에 AUTH가 없으면 종료 코드 **4001**.
- 채팅 서버는 토큰만 본다(사용자 테이블이 없다). 탈퇴 직후 남은 토큰으로는 AUTH가 통과하지만 방 구독·REST는
  403 `FORBIDDEN`/404, 방 목록은 `[]`가 된다 — 팀이 지워져 당사자가 아니기 때문이다(v1.31.1 명문화).
- 인증 전에는 `AUTH`·`PING` 외의 메시지에 4001로 닫는다.
- 서버는 `expiresAt`이 되면 4001(사유 `TOKEN_EXPIRED`)로 닫는다. FE는 refresh(§3) 후 바로 다시 연결한다.
  연결 중에 **같은 사용자**의 새 토큰으로 `AUTH`를 다시 보내면 만료가 연장된다(다른 사용자의 토큰이면 4001).

**2) 방 구독**

```json
→ { "type": "SUBSCRIBE", "requestId": 7 }
← { "type": "SUBSCRIBED", "requestId": 7, "chatOpen": true }
→ { "type": "UNSUBSCRIBE", "requestId": 7 }
```

- 권한·상태 검사는 `GET /chat`과 같고 순서도 같다(권한 먼저). 실패는 **연결을 닫지 않고**
  `{ "type": "ERROR", "requestId": 7, "code": "FORBIDDEN" | "REQUEST_NOT_ACCEPTED" | "REQUEST_NOT_FOUND" |
  "CHAT_EXPIRED" | "CHAT_UPSTREAM_UNAVAILABLE", "message": "..." }`로 알린다. `CHAT_UPSTREAM_UNAVAILABLE`은 잠시 뒤 풀리는
  사정이므로 FE는 읽던 대화를 오류 화면으로 덮지 않고 폴링으로 이어 간다(v1.31.1). 나머지 코드는 화면 오류다
  (`CHAT_EXPIRED`는 v1.33.0).
- 구독 직후 FE는 `GET /chat?after={마지막으로 가진 id}`로 빈틈을 채운다 — **구독 먼저, 조회 다음**(반대면
  그 사이에 온 메시지가 빠진다). 겹친 메시지는 `id`로 거른다.

**3) 서버가 보내는 것**

```json
← { "type": "MESSAGE", "message": "<ChatMessage>" }
← { "type": "ROOM_STATE", "requestId": 7, "chatOpen": false, "reason": "MATCH_STARTED" }
```

- `MESSAGE`: 구독 중인 방에 메시지가 저장되면 그 방을 구독한 **양쪽의 모든 연결**에 보낸다(보낸 사람 본인
  포함 — FE는 POST 응답과 같은 `id`를 거른다). 나간 팀에게는 워터마크 이후 메시지만 간다 — 나가기 안내 줄은
  나간 쪽에 가지 않는다(`GET /chat`과 같은 규칙).
- `ROOM_STATE`: 방의 전송 가능 여부가 바뀌었을 때. `reason`: `MATCH_STARTED`(경기 시각이 지나 읽기 전용) |
  `MATCH_CANCELED`(매칭 취소 — 이후 그 방의 REST는 409 `REQUEST_NOT_ACCEPTED`) | `SCHEDULE_CHANGED`(경기 시각이
  바뀌어 `chatOpen`이 달라짐 — `chatOpen`이 true로 돌아올 수도 있다). 판단은 언제나 서버 시계다.
- 메시지 순서는 `id`가 진실이다. FE는 도착 순서가 아니라 `id`로 정렬한다.

**4) 연결 유지**

```json
→ { "type": "PING" }
← { "type": "PONG" }
```

FE는 25초마다 PING을 보내고, 10초 안에 PONG이 없으면 끊긴 것으로 보고 재연결한다.

**5) 종료 코드**

| 코드 | 뜻 | FE |
|---|---|---|
| 1000 | 정상 종료(FE가 닫음) | - |
| 1001 | 서버 재시작·배포 | 백오프 재연결 |
| 1009 | 프레임 4KB 초과 | 재연결(버그이므로 로그) |
| 1011 | 서버 내부 오류 | 백오프 재연결 |
| 4001 | 인증 없음·실패·시한 초과·토큰 만료 | refresh 후 즉시 재연결. refresh 실패면 로그아웃 흐름. `AUTH_OK` 없이 연속 두 번이면 즉시 재연결을 멈추고 백오프(v1.31.1) |
| 4008 | 같은 사용자의 동시 연결이 5개를 넘어 오래된 연결을 정리 (v1.31.1) | 재연결하지 않고 폴링으로 버틴다(다시 붙으면 다른 기기를 밀어낸다). 화면 재진입·포그라운드 복귀 때 다시 시도 |
| 4029 | 제어 메시지 과다(10초 30건 초과) | 30초 뒤 재연결 |
| 4400 | 해석할 수 없는 메시지(JSON 아님, 모르는 `type`) | 재연결하지 않음(버그) |

서버 쪽 세부(v1.31.2 명문화): 4KB 한도는 **바이트** 기준이다. 인증 전에 JSON이 아닌 프레임이 오면 4400, JSON이지만
`AUTH`·`PING`이 아니면 4001. 서버는 70초 동안 아무 프레임도 없으면 연결을 닫는다(FE의 25초 PING이 이를 막는다).
`MATCH_CANCELED`를 알린 뒤에는 그 방 구독을 서버가 푼다. 탈퇴로 방이 지워지면(PURGE) 프레임 없이 구독만 풀린다.

백오프: 1·2·4·8·16·30초(최대 30초) + 0~1초 무작위. **연속 3번 실패하면 폴링(5초)으로 강등**하고 60초마다 소켓을
다시 시도한다 — 성공하면 폴링을 멈춘다.

**6) Origin**: 브라우저 연결은 허용 오리진 목록으로 거른다(운영). 거르면 업그레이드 단계에서 403.

### 보관 기간 (v1.33.0)

채팅의 목적은 경기 조율이고 경기가 끝나면 그 목적도 끝난다. 그 뒤에는 입금·노쇼 분쟁에 대응할 만큼만 두고 지운다
(개인정보 보호법 제21조 — 사용자 결정 2026-11-16, 법률 점검 11-2).

| 방의 상태 | 보관 | 사용자에게 보이는 것 |
|---|---|---|
| 경기 전 | 지우지 않는다 | 읽기·쓰기 |
| 경기가 끝남 | **`matchAt` + 90일**까지 | 그때까지 읽기 전용. 지나면 410 `CHAT_EXPIRED` + 방 목록에서 빠짐 |
| 매칭 취소 등 수락 상태가 아님 | 취소 시각 + 30일 | 취소 순간부터 이미 409 `REQUEST_NOT_ACCEPTED`(변화 없음) |
| 탈퇴 | 기간과 무관하게 바로 삭제(§10 `PURGE`) | - |

- **삭제 시각 `deleteAt` = `matchAt` + 90일**(정확히 90×24시간 뒤, 서버 시계). 경기 시각이 바뀌면 따라 바뀐다.
  "지났다"는 서버 시계가 `deleteAt`보다 **뒤**라는 뜻이다 — 같은 순간까지는 읽힌다(v1.33.1).
- **`deleteAt`이 지난 순간부터 막힌다.** 실제 삭제는 하루 한 번 도는 정리가 하지만(§10), 사용자가 보는 결과는 그것을
  기다리지 않는다 — `GET /chat`·`POST /chat`·`leave`·`SUBSCRIBE`는 410 `CHAT_EXPIRED`, 방 목록(`GET /users/me/chats`)에서는
  빠진다. 검사 순서는 없는 신청(404) → 권한(403) → 수락 아님(409) → 보관 기간(410). 구독 중이던 연결은 실제 삭제 때 구독이 풀린다
  (프레임 없음 — `PURGE`와 같다).
- 지우는 것은 그 방의 메시지와 나가기 기록 전부다. 매칭·리뷰·전적 등 BE의 기록은 그대로다.
- **경기 뒤의 활동은 기간을 늘리지 않는다.** 경기가 끝난 뒤 한쪽이 나가서 안내 줄이 생겨도 삭제 시각은 `matchAt` 기준이다.
- 신고·분쟁으로 운영자가 삭제를 보류한 방(§10)도 당사자에게는 똑같이 410이다 — 보류는 실제 삭제만 미룬다.
- 보관 기간 정리가 꺼진 서버(`CHAT_RETENTION_ENABLED=false` — 장애 대응용)에서는 `deleteAt`이 `null`이고 410도 없다.
  지킬 수 없는 삭제 시각을 보여 주지 않기 위해서다.
- **FE**: `deleteAt`이 있으면 채팅방에 안내 한 줄을 둔다 — "이 대화는 2026년 11월 28일에 삭제돼요"(날짜는 `deleteAt`,
  위치·모양은 FE 재량). 410은 화면 오류로 "보관 기간이 지나 삭제된 대화예요"를 보여 주고 방 목록 캐시를 버린다.
  매칭 카드의 "채팅" 버튼은 그대로 둔다(누르면 위 안내 화면).

### 푸시 (§8 규약을 따른다)

| 이벤트 | 시점 | 수신자 | title / body 예시 |
|---|---|---|---|
| `CHAT_MESSAGE` | 메시지 전송 | 상대 팀 OWNER | "{보낸 팀 이름}" / "{내용 앞 50자}" |

data: `{ "type": "CHAT_MESSAGE", "requestId": 7, "postId": 12 }`. 탭 시 해당 매칭의
채팅방으로 이동. 메시지마다 발송한다 (묶음·스로틀은 v2). best-effort — 발송 실패가
메시지 저장을 실패시키면 안 된다 (§8과 동일).
v1.31.0: **받는 사람이 그 방을 지금 WebSocket으로 구독 중이면 보내지 않는다**(화면에 이미 떠 있다 — FE가
화면을 떠나거나 백그라운드로 갈 때 소켓을 닫으므로 "구독 중 = 보고 있음"). 발송 주체는 BE다 — 채팅 서버가 BE에 요청하고(§10)
title·body·data 형식은 그대로다.
v1.13.0: 방을 나간 상대에게는 발송하지 않는다. SYSTEM 메시지도 발송하지 않는다.

### 채팅 탭·방 목록 (v1.13.0)

FE 하단 탭에 채팅이 들어간다. 현재 탭은 6개 — **홈 / 팀 / 구장 / 매칭 / 채팅 / 마이**(구장은 v1.27.0). 채팅 탭이 방 목록 화면이다.

#### GET /api/users/me/chats — 인증 필요

내 팀이 당사자인 **ACCEPTED 매칭**의 채팅방 목록. **내가 나간 방은 제외**된다. 보관 기간이 지난 방(`deleteAt` 경과)도
제외된다(v1.33.0).

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
- **동률 2차 키 (v1.25.1 명문화)**: 위 기준이 같은 방끼리는 `requestId` DESC. 리뷰 목록의
  v1.2.2 규칙과 같은 처방 — 같은 시각에 메시지가 들어온 두 방의 순서가 새로고침마다
  바뀌지 않게 한다 (같은 타임스탬프 동률이 실제로 재현됨).
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
{ "id": 3, "requestId": 7, "postId": 12, "postTitle": "토요일 아침 상대 팀 구합니다",
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

`sender`: `"USER" | "AI" | "OPERATOR" | "NOTICE"`. `NOTICE`(v1.35.0)는 서버가 넣는 알림(신고 처리 결과 등, §7-2) —
"킥오프 고객센터"로 표시하고, 방을 운영자 모드로 바꾸지 않는다. FE 표시 — AI는 "AI 상담사", OPERATOR는
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

v1.35.1: 아래 API는 **운영자 세션**(`X-Operator-Token`)도 필요하고 호출마다 감사 기록이 남는다 — §7-2 「운영자 인증」.

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
  각 방 진입·답장. 운영자 여부는 문의함 API가 403이 아닌지로 판정 (강등 패턴. v1.35.1: 운영자는
  403 대신 401 `OPERATOR_SESSION_REQUIRED`를 받는다 — 그것도 "운영자다"로 본다)
- 빈 방 안내: "궁금한 점, 건의, 신고할 일을 남겨 주세요. 운영자가 확인 후 답변드립니다."

## 7-2. 신고·차단·운영 조치 (v1.35.0)

이용자가 올리는 것(모집글·리뷰·팀 소개·채팅)에 대한 신고 수단, 상대를 피하는 차단, 운영자의 조치와 통지를 정한다.
근거: 정보통신망법 제44조의2(공개 게시물의 삭제 요청·임시조치·통지 — 모집글·리뷰·팀 소개가 대상), 스토어 심사 요건(신고 수단,
이용자 차단, 악성 이용자 제재). 화면 문구는 `docs/legal-drafts/consent-and-report-copy.md` 3·4절을 쓴다.

### 신고

#### POST /api/reports — 인증 필요

```json
{ "targetType": "REVIEW", "targetId": 3, "reason": "DEFAMATION", "rightsHolder": "MY_TEAM",
  "detail": "사실과 다른 내용입니다." }
{ "targetType": "CHAT_MESSAGE", "targetId": 45, "requestId": 7, "reason": "ABUSE",
  "chatDisclosureAgreed": true }
```

- `targetType`·`targetId` 필수: `POST`(글 id) / `REVIEW`(리뷰 id) / `TEAM`(팀 id — 팀 소개·팀 이름, 그리고 그 팀의 행동 전반) /
  `CHAT_MESSAGE`(메시지 id, `requestId` 함께 필수).
- `reason` 필수. 대상별로 고를 수 있는 사유:

  | 사유 | POST | REVIEW | TEAM | CHAT_MESSAGE |
  |---|---|---|---|---|
  | `ABUSE` `DEFAMATION` `PERSONAL_INFO` `FALSE_INFO` `SPAM` | ○ | ○ | ○ | ○ |
  | `FRAUD` | ○ | – | ○ | ○ |
  | `NO_SHOW` | – | – | ○ | ○ |

- `rightsHolder`: `reason`이 `DEFAMATION`일 때만 필수 — `"ME" | "MY_TEAM" | "OTHER"`(누구의 권리가 침해됐는지. 본인·자기 팀이면
  법정 삭제 요청으로 다룬다). `detail` optional 최대 500자.
- `chatDisclosureAgreed`: `CHAT_MESSAGE`일 때 `true` 필수 — "신고한 메시지와 그 앞뒤 대화가 운영자에게 전달된다"는 동의.
- 거절: 위 규칙 위반 400 `VALIDATION_FAILED` / 자기 것(내 팀의 글, 내 팀이 쓴 리뷰, 내 팀, 내 팀이 보낸 메시지)을 신고 400 /
  없는 대상 404(`POST_NOT_FOUND`·`TEAM_NOT_FOUND`·`NOT_FOUND`) / 채팅 메시지는 그 방의 당사자 팀 감독만(아니면 403 `FORBIDDEN`) /
  같은 계정이 같은 대상을 이미 신고했고 처리 전이면 409 `REPORT_ALREADY_EXISTS` / 한 계정 하루 10건 초과 429 `REPORT_RATE_LIMITED`
  ("하루"는 달력 날짜가 아니라 **지난 24시간** — 자정 앞뒤로 몰아서 넣지 못하게, v1.35.4).
- 201 → `{ "id": 31 }`. 처리 결과는 고객센터 메시지(`NOTICE`)와 푸시로 간다. 신고 내역을 조회하는 사용자 API는 없다.
- 채팅 메시지 신고는 BE가 채팅 서버에서 신고된 메시지와 앞뒤 20개씩을 받아 신고 기록에 사본으로 저장한다(§10). 채팅 서버가
  응답하지 않으면 503 `CHAT_UPSTREAM_UNAVAILABLE`.

**신고의 즉시 효과**

1. **신고한 사람 화면에서는 바로 가려진다** (`POST`·`REVIEW`): 그 계정의 `GET /api/posts` 목록과 `GET /api/teams/{teamId}/reviews`에서
   빠진다. 다른 사람에게는 그대로 보인다. 신고 기록이 지워지면(「기록의 보관」) 다시 보인다.
2. **자동 임시 가림** (`POST`·`REVIEW`): 처리 전 신고가 **서로 다른 신고자 3명** 이상 쌓이면 운영자 확인 전까지 모두에게 가린다
   (최대 30일, 아래 「가려진 콘텐츠」). 같은 팀에 속한 계정들(감독·관리자·팀원)의 신고는 1명으로 센다(여러 팀에 속한 계정은
   소유한 팀, 없으면 가장 먼저 들어간 팀 하나로 센다 — v1.35.4). 게재자에게 통지한다. `TEAM`·`CHAT_MESSAGE`는
   자동으로 가리지 않는다.
3. 운영자에게 푸시: `REPORT_RECEIVED` — "신고 접수" / "{사유 표시명} · {대상 종류}", data `{ "type": "REPORT", "reportId": 31 }`.

### 가려진 콘텐츠

운영 조치 상태는 `moderation` 오브젝트로 내려간다: `{ "status": "HIDDEN", "until": "2026-12-16T10:00:00+09:00" }`
(`REMOVED`면 `until`은 `null`). `HIDDEN`은 `until`이 지나면 자동으로 풀린다(운영자가 30일 안에 삭제·복원을 정하지 못하면 다시 보인다).

| 대상 | 가려졌을 때 |
|---|---|
| 모집글 | `GET /api/posts` 목록에서 빠진다. `GET /api/posts/{id}`는 작성자에게만 200(+`moderation`), 그 밖에는 410 `CONTENT_MODERATED`. 새 신청은 409 `POST_NOT_OPEN`. 작성자의 `PATCH`·`DELETE`는 410 `CONTENT_MODERATED`(조치 중인 글은 고칠 수 없다). `GET /api/posts/me`에는 `moderation`과 함께 나온다. 이미 수락된 매칭(연락처·채팅)은 그대로다 |
| 리뷰 | 목록에서 빼지 않고 자리 표시로 남긴다 — `rating: null`, `comment: null`, `moderation` 채움. 평점·리뷰 수 집계에서는 뺀다 |
| 팀 소개 | `TeamResponse.introduction: null` + `introductionModeration` 채움. `HIDDEN` 동안 소개 수정은 410 `CONTENT_MODERATED`. `REMOVED`는 소개를 지우고, 감독이 새 소개를 쓰면 표시가 사라진다 |
| 채팅 메시지 | 가리지 않는다(1:1 대화 — 차단과 이용 제한으로 다룬다) |

- 410 `CONTENT_MODERATED`의 `message`와 자리 표시 문구: `HIDDEN` "권리침해 신고로 임시로 가려진 게시물입니다.", `REMOVED` "운영 정책에
  따라 삭제된 게시물입니다."(리뷰는 "…삭제된 리뷰입니다.") — 마침표까지 같게(v1.35.4). 조치 사실을 이용자가 알 수 있게 그 자리에 남기는 표시다(제44조의2 제2항).
- `PostSummary`·`PostDetail`·`ReviewResponse`에 `moderation`(nullable) 필드가 생긴다. 조치가 없으면 `null`.

### 차단

차단의 단위는 **팀**이다(계정이 다른 팀을 차단한다). 상대에게 차단 사실을 알리지 않는다.

#### POST /api/blocks — 인증 필요

```json
{ "teamId": 5, "cancelUpcomingMatch": false }
```

- 201 → `BlockResponse` `{ "team": "<TeamSummary>", "createdAt": "2026-11-16T10:00:00+09:00" }`. 이미 차단한 팀이면 200으로 같은 응답(멱등).
- 내 팀(소유·소속)을 차단 400 `VALIDATION_FAILED` / 없는 팀 404 `TEAM_NOT_FOUND`.
- **예정된 매칭이 있으면 취소와 묶는다**: 내가 감독인 팀과 그 팀 사이에 `ACCEPTED`이고 `matchAt`이 미래인 매칭이 있는데
  `cancelUpcomingMatch`가 `true`가 아니면 409 `BLOCK_HAS_UPCOMING_MATCH`(v1.35.2: 이 응답에는 공통 에러 형식에 더해
  `"matchAt"` — 그 팀과의 예정 매칭 중 가장 가까운 경기 시각 — 이 실린다). `true`면 그 매칭들을 §6-2와 똑같이 취소하고(상대 팀에
  `MATCH_CANCELED` 푸시, 글은 `OPEN` 복구) 차단한다. 조율 수단 없이 경기만 남는 것을 막기 위해서다.
- 두 팀 사이에 대기 중(`PENDING`)인 신청은 정리한다 — 내 팀이 보낸 것은 `CANCELED`, 내 글에 온 것은 `REJECTED`(기존 거절 푸시가 간다).

#### DELETE /api/blocks/{teamId} — 인증 필요 → 204 (차단한 적 없어도 204)

#### GET /api/blocks — 인증 필요 → `BlockResponse[]` (`createdAt` DESC)

**차단의 효과**

| 범위 | 내용 |
|---|---|
| 차단한 계정의 화면 | `GET /api/posts`에서 그 팀의 글이 빠진다. `GET /api/teams`(팀 찾기)에서 빠진다. `GET /api/teams/{id}/reviews`에서 **그 팀이 쓴** 리뷰가 빠진다. `GET /api/teams/{id}`는 `blockedByMe: true`로 열린다(FE는 가림막과 "차단 해제"를 보인다) |
| 두 팀 사이(차단한 계정이 **감독**일 때만) | 어느 방향이든 신청 409 `REQUEST_NOT_ALLOWED`. 서로의 `contact`·`payment`는 `null`. 채팅은 읽기 전용(§10 `RoomContext.blocked` — 전송 409 `CHAT_CLOSED`, 푸시 없음). 지난 경기의 리뷰 작성은 그대로 된다 |
| 하지 않는 것 | 상대 화면에서 내 글을 숨기지 않는다. 상대가 이미 쓴 리뷰는 제3자에게 계속 보인다(문제가 있으면 신고로 다룬다) |

- 감독이 아닌 계정(관리자·팀원·팀 없음)의 차단은 "화면" 효과만 있다. FE는 그런 계정의 차단 확인창에서 "서로 매칭을 신청할 수
  없고, 채팅을 주고받을 수 없습니다" 줄을 빼고 보인다(v1.35.2 — 일어나지 않는 일을 말하지 않는다).
- 해제하면 위 효과가 풀린다. 취소된 매칭과 정리된 신청은 되살아나지 않는다.
- 탈퇴하면 그 계정이 건 차단과 그 계정의 팀을 대상으로 한 차단이 함께 지워진다.
- 차단·해제 때 BE는 두 팀 사이의 수락된 매칭 방에 `ROOM_INVALIDATED`를 보낸다(§10).

### 운영자 인증 (v1.35.1)

운영자 화면은 이용자의 문의와 신고된 대화를 보는 곳이라, 계정 비밀번호만으로 열리지 않게 한다(안전성 확보조치 기준 제6조
제2항 — 외부에서 접속할 때 일회용 비밀번호 등 안전한 인증수단).

#### POST /api/admin/session — 인증 필요, 운영자 계정만 (아니면 403 `FORBIDDEN`)

```json
{ "otp": "123456" }
```

- `otp`: 인증 앱이 보여 주는 6자리(TOTP — RFC 6238, 30초, SHA-1, 앞뒤 한 칸 허용). 비밀키는 BE env **`OPERATOR_TOTP_SECRET`**
  (Base32). dev 프로파일에만 개발용 기본값이 있다. **운영에서 비어 있으면 운영자 API 전체가 403**이다(닫힌 쪽으로 실패).
- 200 → `{ "operatorToken": "...", "expiresAt": "2026-11-16T10:30:00+09:00" }`. 토큰은 **발급 후 30분** 유효하고 연장되지
  않는다(일정 시간 뒤 자동 차단 — 제6조 제4항). 만료되면 OTP를 다시 넣는다. 서버 메모리에만 둔다(재시작하면 사라진다).
  한 번 쓴 OTP는 같은 30초 안에 다시 받지 않는다.
- 틀리면 401 `OPERATOR_OTP_INVALID`. 연속 5번 틀리면 15분 동안 429 `OPERATOR_OTP_LOCKED`.
- **운영자 API 호출**: 헤더 `X-Operator-Token: <operatorToken>`. 검사 순서는 인증(401) → 동의·이용 제한(403) → 운영자 계정인지
  (403 `FORBIDDEN`) → 운영자 세션(401 `OPERATOR_SESSION_REQUIRED`). 대상은 §7-1 「운영자 쪽 API」와 이 절의 `/api/admin/**`
  전부다(`POST /api/admin/session` 자신은 제외).
- FE: 운영자 메뉴(문의함·신고함)에 들어갈 때 `OPERATOR_SESSION_REQUIRED`를 받으면 OTP 입력 화면을 띄우고, 성공하면 토큰을
  **메모리에만** 두고 하던 요청을 다시 보낸다. 토큰을 저장소에 남기지 않는다.

**감사 기록 (v1.35.1)**: 운영자 API의 모든 호출과 운영자 세션 발급 시도(성공·실패)를 DB에 남긴다 — 운영자 계정, 시각, 접속 IP,
수행한 일(예: 문의방 목록·문의방 열람·답장·신고 목록·신고 열람·신고 조치·이용 제한 해제·OTP 성공/실패), 대상이 된 이용자
(userId — 목록 조회는 생략), 대상 식별값(신고 id 등). 내용(문의 본문·대화)은 적지 않는다. **1년 뒤 지운다**(하루 한 번).
대상 이용자가 탈퇴해도 이 기록은 기간까지 남는다(법령상 보관 — 처리방침에 적는다). 조회 API는 없다(운영자가 DB에서 본다).

### 운영자 API — 인증 필요, 운영자만 (§7-1의 운영자. 아니면 403 `FORBIDDEN`)

- `GET /api/admin/reports?status=OPEN&page=0&size=20` → `PageResponse<ReportSummary>`(`createdAt` DESC). `status` 생략 시 `OPEN`과
  `TEMP_HIDDEN`.

  ```json
  { "id": 31, "targetType": "REVIEW", "targetId": 3, "reason": "DEFAMATION", "status": "OPEN",
    "reporterCount": 1, "autoHidden": false, "hiddenUntil": null,
    "createdAt": "2026-11-16T10:00:00+09:00" }
  ```

  `reporterCount`: 같은 대상에 걸린 처리 전 신고의 서로 다른 신고자 수. `hiddenUntil`(v1.35.2): 대상이 지금 임시 가림 중이면
  그 기한(자동 가림 포함), 아니면 `null` — FE는 기한이 7일 안으로 남은 건을 눈에 띄게 표시한다.
- `GET /api/admin/reports/{id}` → `ReportDetail` — `ReportSummary` + `rightsHolder`, `detail`, `reporter`·`reported`
  (`{ userId, nickname, teamId, teamName }` — 탈퇴했으면 `null`), `target`(신고 시점의 사본: 글은 제목·내용, 리뷰는 별점·내용,
  팀은 이름·소개, 채팅은 `{ reportedMessageId, messages: [...] }`), `moderation`(현재 조치 상태), `resolution`(조치 내용·시각·메모,
  없으면 `null`). **서버는 이 조회를 열람 기록으로 남긴다**(누가·언제·어느 신고 — 접속기록).

  **정확한 모양 (v1.37.2)** — `ReportSummary`의 필드를 **같은 층에 펼친다**(`summary` 같은 묶음에 넣지 않는다):

  ```json
  { "id": 31, "targetType": "POST", "targetId": 12, "reason": "SPAM", "status": "TEMP_HIDDEN",
    "reporterCount": 3, "autoHidden": true, "hiddenUntil": "2026-12-16T10:00:00+09:00",
    "createdAt": "2026-11-16T10:00:00+09:00",
    "rightsHolder": null, "detail": "광고 글로 보입니다.",
    "reporter": { "userId": 1, "nickname": "김주장", "teamId": 1, "teamName": "FC 새벽" },
    "reported": { "userId": 2, "nickname": "이감독", "teamId": 2, "teamName": "마포 유나이티드" },
    "target": { "title": "일요일 오전 11인제 매칭", "content": "실력 있는 팀 환영합니다.", "rating": null,
                "name": null, "introduction": null, "reportedMessageId": null, "messages": [] },
    "moderation": { "status": "HIDDEN", "until": "2026-12-16T10:00:00+09:00" },
    "resolution": { "action": "HIDE", "suspendDays": null, "note": "양쪽 의견 확인 중",
                    "resolvedAt": "2026-11-16T11:00:00+09:00" } }
  ```

  | `targetType` | `target`에서 채워지는 필드 (나머지는 `null`, `messages`는 `[]`) |
  |---|---|
  | `POST` | `title`(제목), `content`(내용) |
  | `REVIEW` | `rating`(별점), `content`(내용) |
  | `TEAM` | `name`(팀 이름), `introduction`(소개 — 없었으면 `null`) |
  | `CHAT_MESSAGE` | `reportedMessageId`, `messages` — 원소는 **`id`·`type`·`senderTeamId`·`content`·`createdAt` 다섯 필드만**(§6-1 `ChatMessage`에서 `requestId`를 뺀 것 — 어느 방인지는 신고가 안다, v1.37.3), 시간 오름차순 |

  `reporter`·`reported`의 `teamId`·`teamName`은 팀이 없으면 `null`. `resolution.suspendDays`는 그 조치에 이용 제한이 붙었으면 그
  날수, 아니면 `null`. 시각은 다른 응답과 같이 밀리초까지만 적는다.
- `POST /api/admin/reports/{id}/resolve` → 200 `ReportDetail`

  ```json
  { "action": "HIDE", "suspendDays": null, "note": "양쪽 의견 확인 중" }
  ```

  | `action` | 뜻 | 신고 상태 |
  |---|---|---|
  | `DISMISS` | 조치하지 않는다. 가려져 있었다면 복원한다 | `CLOSED` |
  | `HIDE` | 임시 가림 30일(`POST`·`REVIEW`·`TEAM`의 소개) | `TEMP_HIDDEN` — 30일 안에 `REMOVE`나 `DISMISS`로 다시 정한다 |
  | `REMOVE` | 삭제(같은 대상 범위) | `CLOSED` |
  | `SUSPEND_ONLY` | 콘텐츠는 두고 계정만 제한한다(`suspendDays` 필수) | `CLOSED` |

  - `suspendDays`(optional, 1~3650): 신고된 쪽 계정(글·리뷰 작성 팀의 감독, 팀의 감독, 메시지를 보낸 계정)의 이용을 그 날수만큼
    제한한다. **신고된 쪽이 운영자 계정이면 409 `OPERATOR_NOT_SUSPENDABLE`**(v1.37.3 — 운영자가 제한되면
    운영자 API가 닫혀 스스로 풀 수 없다. 그런 건은 콘텐츠 조치(`HIDE`·`REMOVE`)나 `DISMISS`로만 처리한다. 검사 순서는
    요청 값 검증(400) 다음이다). FE는 "운영자 계정에는 이용 제한을 걸 수 없어요"로 보인다. `DISMISS`와 함께 쓸 수 없다. `CHAT_MESSAGE` 신고에는 `HIDE`·`REMOVE`를 쓸 수 없다(400).
  - `note` optional 최대 500자(내부 메모 — 통지에 싣지 않는다).
  - 같은 대상에 걸린 다른 처리 전 신고도 같은 결과로 함께 닫는다.
  - **통지**: 조치와 동시에 신고자 전원과 게재자(신고된 쪽)에게 고객센터 메시지(`sender: "NOTICE"`)와 `SUPPORT_MESSAGE` 푸시를
    보낸다. 내용은 대상, 조치 종류, 사유 범주, 조치 일시, 임시 가림이면 기한, 이의 제기 방법과 기한(임시 가림은 그 기간 안,
    삭제·이용 제한은 14일). **신고자가 누구인지는 어느 통지에도 싣지 않는다.** `DISMISS`는 신고자에게만 알린다(자동으로 가려졌다가
    복원되는 경우에는 게재자에게도 복원을 알린다). 자동 임시 가림이 걸릴 때도 게재자에게 같은 양식으로 알린다.
- `DELETE /api/admin/users/{userId}/suspension` → 204 (이용 제한 해제. 없는 사용자 404 `USER_NOT_FOUND`).
- `POST /api/admin/users/deletions` (v1.37.3) — **DB를 백업에서 되살린 뒤, 백업 시점 이후에 탈퇴했던 계정을 다시 탈퇴시키는 용도.**
  평시에는 꺼 둔다: BE env `ADMIN_ACCOUNT_DELETION_ENABLED`(기본 `false`)가 `true`가 아니면 **404 `NOT_FOUND`**(운영자 계정이
  뚫렸을 때 대량 삭제 수단이 되지 않게 — 복원 작업 중에만 켜고 끝나면 끈다).

  ```json
  { "userIds": [12, 15, 31] }
  ```

  - `userIds` 1~200개(양의 정수, 중복은 한 번으로). 벗어나면 400 `VALIDATION_FAILED`. **운영자 자신의 id가 들어 있으면 400**.
  - 각 계정을 §3-4의 탈퇴와 **똑같은 경로**로 지운다(소유 팀·글·리뷰 정리, 채팅 서버의 `PURGE`까지 — 따로 만든 삭제 코드를 쓰지
    않는다). 비밀번호 확인과 `ACTIVE_MATCH_EXISTS` 검사는 하지 않는다 — 예정된 수락 매칭이 있으면 §6-2와 같이 취소한 뒤 지운다.
  - 200 → `{ "deleted": [12, 31], "notFound": [15] }`. 이미 없는 id는 `notFound`로 돌려준다(다시 돌려도 안전하다).
  - 운영자 세션(`X-Operator-Token`)이 필요하고 접속기록에 대상 userId가 전부 남는다.
  - 탈퇴한 userId 목록은 서버 로그의 탈퇴 완료 줄(시각·userId)에서 뽑는다 — 절차는 인프라 운영 문서.

### 이용 제한

- `UserResponse.suspendedUntil`이 미래인 계정이 **인증이 필요한 API**를 부르면 403 `ACCOUNT_SUSPENDED`. 예외: §3 「동의」의 예외
  목록 + 고객센터(`/api/support/**`의 사용자 쪽 API — 이의 제기 창구). 검사 순서는 인증(401) → 동의(403 `CONSENT_REQUIRED`) →
  이용 제한(403 `ACCOUNT_SUSPENDED`) → 그 밖의 검증.
- 기한이 지나면 자동으로 풀린다. 제한 중에도 탈퇴할 수 있다.
- 채팅 서버는 이용 제한을 보지 않는다(사용자 정보가 없다) — 제한된 감독도 이미 수락된 매칭의 채팅은 쓸 수 있다. 잡힌 경기의
  조율은 상대 팀에게도 필요하기 때문이다. 문제가 채팅에서 나면 상대가 차단한다.

### 금칙어

모집글의 제목·내용, 리뷰 내용, 팀 이름·소개, 닉네임을 저장할 때 BE가 금칙어 목록(BE 리소스 파일)으로 검사한다. 걸리면 400
`VALIDATION_FAILED`(`fieldErrors`의 `message`는 "사용할 수 없는 표현이 포함되어 있습니다." — 마침표 포함, v1.37.2). 채팅 메시지는 검사하지 않는다.
이미 저장된 것에는 소급하지 않는다.

### 기록의 보관

- 조치가 있었던 신고는 종료 후 **1년**, 조치 없이 끝난 신고는 종료 후 **90일** 뒤 지운다(하루 한 번).
- 신고자나 신고된 쪽이 탈퇴하면 그 신고 기록의 사용자 식별값을 비우고, **채팅 문맥 사본은 지운다**(대화는 탈퇴와 함께 지운다는
  약속 — §3-4). 신고 건 자체(대상 종류·사유·조치)는 위 기간까지 남는다.
- 운영자의 조회·조치 기록(접속기록)은 1년 이상 남긴다.

### FE 규칙

- **신고 진입**: 모집글 상세(내 글 제외), 리뷰 항목(내 팀이 쓴 것 제외), 팀 페이지(내 팀 제외), 채팅방의 상대 메시지. 위치·모양은
  재량(더보기 메뉴 등). 신고 화면은 대상에 맞는 사유만 보이고, `DEFAMATION`이면 "누구의 권리인지"를 한 번 더 묻고, 채팅 메시지면
  전달 동의 체크를 받는다. 안내·완료 문구는 법무 초안 3-2절.
- 신고 성공 뒤 목록 캐시를 버린다(내 화면에서 사라져야 한다). 409 `REPORT_ALREADY_EXISTS`는 "이미 신고한 내용이에요".
- **차단 진입**: 팀 페이지, 채팅방, 모집글 상세의 더보기 메뉴. 확인창은 법무 초안 4절 — 409 `BLOCK_HAS_UPCOMING_MATCH`를 받으면
  "차단하면 예정된 매칭이 취소됩니다" 확인창을 띄우고 `cancelUpcomingMatch: true`로 다시 보낸다. 차단·해제 뒤 목록 캐시를 버린다.
  마이 탭에 "차단 목록"(해제 가능).
- 가려진 리뷰는 자리 표시 문구로, 410 `CONTENT_MODERATED`는 `message`를 화면 안내로 그린다. 내 글·내 팀 소개가 가려졌으면
  `moderation`의 상태와 기한을 보이고 "고객센터에서 이의를 제기할 수 있어요"로 잇는다.
- 403 `ACCOUNT_SUSPENDED` 또는 `suspendedUntil`이 미래면 이용 제한 안내 화면(기한, 고객센터로 가는 길, 로그아웃·탈퇴).
- 채팅방에 처음 들어갈 때 한 번(기기에 기억): "신고가 접수되면 확인을 위해 운영자가 대화를 볼 수 있습니다."
- **운영자 화면**: 운영자 계정이면 마이 탭에 "신고함"(문의함과 같은 판정 방식 — 운영자 API가 403이 아닌지). 목록 → 상세(대상 사본,
  채팅 문맥) → 조치(가림·삭제·제한·조치 없음, 메모). 꾸밈보다 정확한 표시가 먼저다.

## 8. 푸시 알림 (v1.4.0)

Expo Push Service 기반. FE가 기기에서 Expo push token을 받아 BE에 등록하면,
BE가 매칭 이벤트 발생 시 Expo Push API(`https://exp.host/--/api/v2/push/send`)로 보낸다.
푸시는 **best-effort** — 발송 실패가 원 트랜잭션(신청/수락 등)을 실패시키면 안 된다.
푸시 발송은 언제나 BE가 한다 — 채팅 메시지 알림(§6-1)은 채팅 서버가 BE에 요청해서 나간다(§10, v1.31.0).

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

## 8-1. 구장 디렉터리 (v1.27.0)

조기축구 팀이 경기할 구장을 찾아 **외부 예약 페이지로 연결**하는 조회 전용 기능.
예약·결제·빈자리 확인은 전부 외부 사이트 몫이다 — 킥오프는 중개하지 않고 링크만 건다.

### GET /api/stadiums — 인증 불필요

쿼리: `region`(선택, 예: "서울 강서구" — 모집글 §5의 지역 문자열과 같은 형식, 시 단위
프리픽스 매칭 허용: "서울"이면 서울 전체), `keyword`(선택, 이름 부분 일치 — §5와 같은
LIKE 이스케이프 규칙), `page`/`size`(기본 0/20, 최대 50).

200 응답 (§2 Page 래퍼):

```json
{ "content": [ {
  "stadiumId": 1,
  "name": "월곡 인조잔디구장",
  "region": "서울 성북구",
  "address": null,
  "reservationUrl": "https://yeyak.seoul.go.kr/…",
  "source": "SEOUL_PUBLIC",
  "acceptStatus": "접수중",
  "usePeriod": "2026-09-01 ~ 2026-12-31"
} ], "totalElements": 1, "...": "§2 Page" }
```

- `source`: `SEOUL_PUBLIC`(서울 공공서비스예약 API 동기화) | `MANUAL`(운영자 시드).
- `address` nullable — 서울 공공 API는 주소 미제공이라 SEOUL_PUBLIC은 항상 null, MANUAL도
  모르면 null(지어내지 않는다). `region`은 SEOUL_PUBLIC의 경우 BE가 "서울 "+자치구로 조립.
- **행 = 예약 서비스 단위**(v1.27.1): 같은 구장이 평일/주말 등으로 여러 줄일 수 있다.
  name은 서비스명 원문(예: "월곡 인조잔디구장(평일)-2026년"). FE는 목록 상단 안내 문구에
  "같은 구장이 요일별로 나뉘어 보일 수 있어요"를 포함한다.
- `usePeriod`: "YYYY-MM-DD ~ YYYY-MM-DD"로 날짜만, 원문 형식이 다르면 자르지 않고 원문.
- `acceptStatus`/`usePeriod`: **SEOUL_PUBLIC만**, 원문 그대로(예: 접수중·예약마감). MANUAL은 null.
- `reservationUrl`은 응답에 항상 있고 **FE가 새 창/브라우저로 연다**(웹 `target=_blank
  rel=noopener`, 앱 `Linking.openURL`). 외부 사이트라는 안내 문구를 목록 상단에 1회 표시.
- 정렬: region 오름차순 → name 오름차순. 좌표·지도는 v1 범위 밖(§9).

### 데이터 공급 (FE 무관, BE 내부 규약)

- 서울: 열린데이터광장 체육시설 공공서비스예약 API(OA-2266)에서 소분류 축구장·풋살장만
  동기화. 예약 URL은 `https://yeyak.seoul.go.kr/web/reservation/selectReservView.do?rsv_svc_id=<SVCID>`
  형식 또는 API가 주는 바로가기 값. **하루 1회 스케줄 + 기동 시 비어 있으면 1회** 갱신,
  API 장애 시 마지막 성공 데이터 유지(빈 목록으로 덮지 않는다). 인증키는 env
  `SEOUL_OPENDATA_KEY`, 없으면 동기화 건너뜀(수동 시드만으로 동작).
- 수동 시드: 운영자가 직접 넣는다(경기·인천·사설). v1은 관리 API 없음 — SQL/시드로만.
- 출처 표기: 서울 공공분은 목록에 "서울시 공공서비스예약 제공" 표기(공공데이터 이용조건).


## 8-2. 공휴일 조회 (v1.30.0)

달력(홈 날짜 필터·모집글 경기 일시)에 빨간날을 칠하기 위한 조회 전용 기능. FE는 `src/lib/holidays.ts`로
공휴일을 **스스로 계산**할 수 있지만(법정 규칙 — 어느 해든), 정부가 그때그때 지정하는 **임시공휴일**은
규칙으로 알 수 없다. 그래서 서버가 카카오 톡캘린더 공휴일 API를 대행해 공식 목록을 내리고, FE는 받은 해만
그 목록으로 덮는다. 인증키(어드민 키)를 앱에 넣지 않으려는 것이 서버 대행의 이유다(§5-1과 같다).

### GET /api/holidays?year=YYYY — 인증 불필요

쿼리: `year`(필수, 정수 1900~2200 — 범위 밖·누락·숫자 아님은 400 `VALIDATION_FAILED`, fieldErrors `year`).

200 응답:

```json
{ "year": 2026, "source": "KAKAO",
  "holidays": [
    { "date": "2026-01-01", "name": "새해" },
    { "date": "2026-02-16", "name": "설날연휴" },
    { "date": "2026-02-17", "name": "설날" },
    { "date": "2026-06-03", "name": "지방선거" }
  ] }
```

- `source`: `KAKAO`(카카오에서 받아 옴) | `NONE`(서버가 모른다 — 어드민 키 없음, 호출 실패·타임아웃,
  카카오에 그 해 데이터 없음. 셋을 구별해 내리지 않는다). `NONE`이면 `holidays`는 항상 `[]`.
- `holidays`: **쉬는 날만**(카카오 응답의 `holiday: true`). 기념일(국군의날·어버이날 등 `holiday: false`)은
  빼고 보낸다. `date` 오름차순, 같은 날에 이름이 둘이면 둘 다(순서 유지). `date`는 **한국 날짜**(`YYYY-MM-DD`),
  요청한 해의 날짜만. `name`은 카카오 제목 원문(최대 50자 — 실응답 예: "새해"·"설날연휴"·"대체휴일"·"지방선거", 2026-11-10 실측).
- **절대 500을 내지 않는다.** 외부 실패는 전부 `source: NONE` + 200이다 — 달력은 앱의 기본 화면이라
  공휴일을 몰라서 화면이 안 뜨는 쪽이 공휴일 색이 빠지는 쪽보다 훨씬 나쁘다.
- **FE 규칙**: 달력이 보이는 해마다 1회 조회(앱 세션 내 메모리 캐시). 응답 전·`NONE`이면 계산값
  (`koreanHoliday`)으로 그리고, `KAKAO`가 오면 **그 해는 이 목록만** 빨간날로 쓴다(합치지 않는다 — 카카오가
  빼먹은 날을 계산값으로 메우면 임시로 지정 해제된 날을 되살릴 수 있다). 실패는 조용히(토스트·에러 UI 없음).

### 데이터 공급 (FE 무관, BE 내부 규약)

- 출처: 카카오 톡캘린더 REST API 「공휴일 및 주요 기념일 조회」 `GET https://kapi.kakao.com/v2/api/calendar/holidays`,
  헤더 `Authorization: KakaoAK {어드민 키}`. 쿼리 `from`/`to`는 **UTC RFC3339**(예 `2026-01-01T00:00:00Z`)이고
  **`to`는 `from`+31일 이내**라 한 해를 한 번에 못 받는다 — 31일 이하 창으로 나눠 부르고, 이웃 창은
  **하루 이상 겹치게** 잡아 경계 누락을 막은 뒤 `(date, name)`으로 중복 제거한다. 전체 범위는
  전년도 12월 31일 00:00Z ~ 다음 해 1월 2일 00:00Z를 덮어야 한다(카카오가 날짜를 UTC 자정으로 주든 KST
  자정으로 주든 요청 해의 모든 날이 들어오게). **실측(v1.30.1): `start_at`은 UTC 자정(`2026-02-16T00:00:00Z`)으로
  온다 — 카카오 문서 예시의 KST 자정(`…T15:00:00Z`)과 다르다.** 응답 `events[].time.start_at`을 **Asia/Seoul로 바꾼 날짜**를
  `date`로 쓰고, `end_at − start_at`이 1일보다 길면 **그 일수만큼** 매일을 한 건씩 펼친다(끝을 `end_at`의 날짜로 잡으면
  UTC 자정 관례에서 하루짜리가 이틀이 된다). **실측으로는 연휴도 하루씩 끊겨 온다**(설날연휴·설날·설날연휴 세 건,
  같은 이름이 다른 날에 반복 — 중복 제거 키가 `(date, name)`인 이유). 펼치기는 묶어 올 경우의 방어다. 요청 해가 아닌
  날짜는 버린다.
  콘솔 추가 설정은 필요 없다(문서상 요구 사항 없음, 톡캘린더 활성화 불필요).
- **창 하나라도 실패하면 그 해 전체가 `NONE`**(부분 성공을 성공으로 내리지 않는다). 전부 성공했는데
  0건이면 역시 `NONE`(카카오에 아직 없는 해 — 실측 2050년 데이터 있음, 2060년 `{"events":[]}`). 31일 제한은 실측
  32일 창에서 400 `code: -2`("must be within 31 days", 검사는 KST 환산) — 31일 창은 통과.
- 캐시: 연도 키 인메모리. 지난 해 성공 → 영구, 올해 이후 성공 → 1일(임시공휴일이 중간에 지정된다),
  `NONE` → 10분. 요청 스레드에서 호출하므로 창당 타임아웃 3초, 한 해 전체 10초를 넘기면 `NONE`.
- 인증키는 env `KAKAO_ADMIN_KEY`(카카오 개발자 콘솔 › 앱 키 › Admin 키 — **비밀값**, FE·로그·저장소 금지).
  없으면 모든 해가 `NONE`, 기동·다른 API는 정상. README 재배포 체크리스트에 추가한다.

## 8-3. 경기일 날씨 (v1.36.0)

모집글의 경기 시각에 예상되는 날씨와 기온을 보여 주는 조회 전용 기능. 기상청 예보를 BE가 대신 받아 두고, 글·신청 응답에 실어
내린다. 따로 부르는 API는 없다.

### Weather 오브젝트

`PostSummary`·`PostDetail`·`RequestResponse`의 `weather` 필드(nullable).

```json
{ "condition": "CLEAR", "kind": "HOURLY",
  "temperature": 3, "temperatureMin": null, "temperatureMax": null,
  "precipitationProbability": 20, "area": "서울 강서구",
  "announcedAt": "2026-11-16T17:00:00+09:00" }
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `condition` | WeatherCondition | 날씨. FE가 이모지와 한글 이름으로 바꾼다 |
| `kind` | WeatherKind | `HOURLY`면 `temperature`가, `DAILY`면 `temperatureMin`·`temperatureMax`가 채워진다 |
| `temperature` | number \| null | 경기 시각의 기온(℃, 정수). `HOURLY`만 |
| `temperatureMin`·`temperatureMax` | number \| null | 그날의 최저·최고 기온(℃, 정수). `DAILY`만. 둘 중 하나만 온 예보면 온 것만 채운다 |
| `precipitationProbability` | number \| null | 강수확률(0~100). 예보에 없으면 `null` |
| `area` | string | 예보의 기준 지역(v1.37.1) — 표에서 찾은 "시/도 시군구"(짧은 이름, 예 "서울 강서구"·"강원 춘천시"). 시군구를 못 찾아 시/도 대표 지점을 썼으면 시/도만("서울") |
| `announcedAt` | string | 이 값이 나온 예보의 발표 시각 |

**`weather`가 `null`인 경우** (FE는 아무것도 그리지 않는다 — 자리 표시·오류 문구 없음):

- 경기 시각이 이미 지났다(서버 시계 기준 `matchAt` ≤ 지금).
- 경기일이 예보가 나오는 기간(오늘부터 약 10일) 밖이다.
- 글의 `region`에서 시/도를 알아볼 수 없다.
- 서버에 보관된 예보가 없다 — 인증키 없음, 기상청 호출 실패, 아직 받아 오기 전(방금 쓴 글의 지역을 처음 받는 동안 등).

응답은 **서버 메모리에 보관된 예보만** 읽는다. 글·신청 API의 요청 처리 중에는 기상청을 부르지 않는다 — 날씨 때문에 목록이
느려지거나 500이 나는 일이 없어야 한다(§8-2 공휴일과 같은 생각: 날씨가 빠지는 쪽이 화면이 안 뜨는 쪽보다 낫다).

### 값을 정하는 규칙 (BE)

1. **단기예보가 그 시각을 덮으면 `HOURLY`**: 경기 시각을 KST 정시로 내린 칸(07:40 → 07시)의 기온·하늘 상태·강수 형태·강수확률을
   쓴다. 그 칸이 없고(끝 구간은 3시간 간격일 수 있다) 앞뒤 **2시간 안**에 칸이 있으면 가장 가까운 칸(같은 거리면 앞 칸)을 쓴다.
   - `condition`: 강수 형태(PTY)가 0이 아니면 그것으로 — 1 `RAIN`, 2 `RAIN_SNOW`, 3 `SNOW`, 4 `SHOWER`. 0이면 하늘 상태(SKY)로 —
     1 `CLEAR`, 3 `PARTLY_CLOUDY`, 4 `CLOUDY`. 표에 없는 숫자는 `UNKNOWN`.
2. **아니고 중기예보가 그 날을 덮으면 `DAILY`**: 그날의 날씨 글자와 최저·최고 기온을 쓴다. 오전/오후로 나뉜 날은 경기 시각이
   12시 전이면 오전, 아니면 오후 값을 쓴다(강수확률도 같은 쪽).
   - `condition`: 글자에 "소나기"가 있으면 `SHOWER`, "비/눈"(또는 "비 또는 눈"·"눈/비")이면 `RAIN_SNOW`, "눈"이면 `SNOW`,
     "비"면 `RAIN`, 그 밖에 "맑음" `CLEAR` / "구름많음" `PARTLY_CLOUDY` / "흐림" `CLOUDY`. 위에서부터 먼저 맞는 것. 어느 것도
     아니면 `UNKNOWN`(기온은 그대로 내린다).
3. 둘 다 없으면 `null`. 단기와 중기가 같은 날을 함께 덮으면 **단기가 먼저**다.
4. 받아 온 지 오래된 값은 내리지 않는다 — 발표 시각 기준 단기 12시간, 중기 36시간이 지나도록 새 발표를 받지 못했으면 그
   지역은 `null`이다(낡은 예보를 최신인 것처럼 보이지 않는다). 그 안에서는 호출이 실패해도 직전 값을 계속 내린다.

### 지역 → 예보 지점 (BE)

- 글의 `region`은 자유 문자열이다("서울 강서구", "강원특별자치도 춘천시", "경기 수원시 장안구"). 공백으로 나눠 **첫 토막을
  시/도**(긴 정식 이름·옛 이름을 짧은 이름 17개로 맞춘다 — "서울특별시"·"서울시" → 서울, "강원특별자치도"·"강원도" → 강원,
  "전북특별자치도"·"전라북도" → 전북 등), **다음 토막을 시군구**로 읽는다("수원시 장안구"처럼 두 토막짜리가 표에 있으면 그쪽이 먼저).
- BE는 `(시/도, 시군구) → 단기예보 격자(nx, ny), 중기 육상예보 구역 코드, 중기 기온 지점 코드` 표를 리소스로 가진다. 출처는
  기상청 API 활용 가이드의 첨부 표(격자 위경도 표, 중기예보 구역 코드표)다. **코드를 지어내지 않는다** — 공식 표에서 확인하지
  못한 줄은 넣지 않는다.
- 시군구를 표에서 못 찾으면 그 **시/도의 대표 지점**(시청·도청 소재지)으로 대신한다. 시/도도 못 찾으면 `null`.
- 중기 기온 지점은 시군구마다 있지 않다 — 표에서 그 시군구에 가장 가까운(같은 예보 구역의) 지점을 미리 정해 둔다.

### 받아 오기 (BE 내부 규약, FE 무관)

- 출처: 공공데이터포털의 기상청 단기예보 조회서비스 `getVilageFcst`, 중기예보 조회서비스 `getMidLandFcst`(날씨·강수확률)·
  `getMidTa`(최저·최고 기온). 응답은 JSON으로 받는다. 공공누리 제1유형(출처 표시).
- **무엇을 받나**: 지금부터 11일 안에 경기가 있는 글(삭제된 글 제외)의 지역만. 단기는 격자별, 중기는 구역·지점 코드별로 묶어
  같은 곳을 두 번 부르지 않는다.
- **언제 받나**: 30분마다 도는 작업이 "새 발표가 나왔을 시각인데 아직 그 발표를 못 받은 곳"만 다시 받는다(단기 발표 02·05·08·
  11·14·17·20·23시, 중기 06·18시 — 발표 뒤 15분은 기다린다). 기동 직후에도 한 번 돈다(요청을 막지 않게 따로). 글이 새로 생기거나
  `matchAt`·`region`이 바뀌어 보관분이 없는 곳이 생기면 그 요청과 **따로**(비동기) 바로 받는다 — 그 응답 자체는 `weather: null`일 수 있다.
- 보관은 서버 메모리(재기동하면 다시 받는다). DB에 넣지 않는다.
- 호출당 타임아웃 5초. 실패·한도 초과(결과 코드 22·23 등)·이상한 응답은 그 곳만 건너뛰고 다음 주기에 다시 한다. 예외가
  작업 전체나 다른 API를 멈추면 안 된다. 하루 호출 수가 개발 계정 한도(서비스별 10,000건)의 절반을 넘으면 경고 로그를 남긴다.
- 인증키: env `KMA_SERVICE_KEY`(공공데이터포털의 **일반 인증키(Decoding)** — 비밀값, FE·로그·저장소 금지. 호출 주소를 로그에
  남길 때는 키를 가린다). 두 서비스의 활용 신청을 같은 계정으로 하면 키는 하나다. 없으면 받아 오기 작업을 돌리지 않고 모든
  `weather`가 `null`이다. 기동과 다른 API는 정상.
- 개발용 가짜 값: `kickoff.weather.fake`(env `WEATHER_FAKE`, 기본 `false`)가 `true`이고 **dev 프로필일 때만**, 기상청을 부르지 않고
  경기일·지역으로 정해지는 고정 값을 내린다(화면 확인용 — 오늘~3일 `HOURLY`, 4~10일 `DAILY`, 그 밖은 `null`). 운영 프로필에서는
  이 값을 무시한다.
- 중기예보 API는 기상청의 개편(2026년 11월 예고)으로 형식이 바뀔 수 있다. 중기 쪽 해석이 실패하면 그 구간만 `null`이고 단기는
  그대로 동작해야 한다.

### FE 규칙

- 이모지: `CLEAR` ☀️ / `PARTLY_CLOUDY` 🌤️ / `CLOUDY` ☁️ / `RAIN` 🌧️ / `RAIN_SNOW` 🌨️ / `SNOW` ❄️ / `SHOWER` 🌦️. `UNKNOWN`과
  **모르는 값**(나중에 추가될 값)은 이모지 없이 기온만 보인다.
- 카드(홈 목록·내 글·매칭 관리): 경기 일시 옆에 짧게 — `HOURLY` "☀️ 3°", `DAILY` "🌧️ 2°/9°"(최저/최고).
- 글 상세: 한 줄로 풀어서 — "☀️ 맑음 · 3° · 강수확률 20%"(`DAILY`는 "🌧️ 비 · 최저 2° / 최고 9° · 강수확률 60%"), 강수확률이
  `null`이면 그 토막을 뺀다. 그 아래 작은 글씨로 **"기상청 예보(서울 강서구 기준) · 11월 16일 17시 발표"**(`area`·`announcedAt`) — 출처 표시다(공공누리
  제1유형). 예보는 바뀔 수 있으므로 확정된 사실처럼 쓰지 않는다("예상" 또는 "예보"라는 말을 붙인다).
- `weather`가 `null`이면 아무것도 그리지 않는다. 글 상세에서 경기일이 10일보다 멀 때만 "경기 10일 전부터 날씨 예보가
  보여요"를 보일 수 있다(FE가 날짜로 판단, 선택).
- 화면 낭독용 이름을 단다(예: "예상 날씨 맑음, 기온 3도") — 이모지만으로 뜻을 전하지 않는다.
- **기상청 값에 없는 판단을 덧붙이지 않는다**(v1.37.1) — "경기하기 좋은 날씨", "우천 취소 가능성 높음" 같은 문구 금지. 킥오프는
  기상청 예보를 그대로 옮겨 보여 줄 뿐이다(예보는 기상청만 할 수 있다 — 기상법 제17조).
- **데이터 출처 표기**(v1.37.1): 앱의 고정된 자리 한 곳(마이 탭 하단, 웹은 바닥글)에 "데이터 출처"를 둔다 — "날씨: 기상청
  단기예보·중기예보(공공누리 제1유형) / 구장 정보: 서울시 공공서비스예약 / 지도·장소 검색: 카카오". 목록 카드에는 출처를 붙이지 않는다.
- 따로 새로 고치지 않는다. 글·신청을 다시 받을 때 함께 바뀐다.

## 9. v1 범위 밖 (구현하지 말 것)

실명 본인인증(PASS·본인확인기관 연동 — 사업자 등록 후 v2),
계정 통합(한 계정에 복수 로그인 수단 연결, 기존 계정 병합·빈 소셜 계정 정리 — v2),
만 14세 미만의 법정대리인 동의 절차·생년월일 수집(v1은 "만 14세 이상" 자기 확인만 — v1.25.0),
이메일 찾기의 전체 이메일 공개 응답(v1은 마스킹만 — §3-5),
팀 검색 오타 유사도(편집거리·trigram),
구장 시간대별 빈자리 조회·예약 대행(공공·사설 모두 슬롯 데이터를 공개하지 않음 — §8-1은 링크 연결만),
구장 관리자용 등록 API(v1은 수동 시드만 — §8-1),
WebSocket으로 메시지 보내기(v1.31.0은 받기만 — 보내기는 REST), 채팅 서버 다중화, 고객센터 문의의 실시간 전송, 채팅 읽음 표시·안읽음 배지,
채팅 메시지 수정·삭제, 채팅 이미지 첨부, 채팅 푸시 묶음·스로틀,
이미지 업로드(팀 로고 포함), 스쿼드의 서버 이미지 렌더링·링크 공유·경기(매칭) 연결(v1은 제목만 — §4-4),
기록 수정, 소유권 이전·소유자 탈퇴, 가입 초대(팀→사용자 방향),
경기 종료 리마인드 푸시(기록·리뷰 유도 알림),
리뷰 수정·삭제(작성자가 직접 고치는 기능 — 운영자 삭제는 §7-2), 제재 계정의 재가입 방지, 소셜 계정 연동 해제, GOOGLE·APPLE 로그인 활성화(값만 예약),
다중 기기 push token, 알림 히스토리 화면, 알림 설정(끄기/켜기),
홈 목록 지도 뷰, 좌표 반경 검색, 정적 지도 이미지, 모집글 좌표 저장(v1.37.0 폐지 — §5-1), 공공 데이터로 좌표 구하기,
날씨 알림 푸시(비 예보 알림 등), 시간대별 예보 목록, 미세먼지·자외선·바람·체감온도, 지난 경기의 실제 날씨 기록, 구장 좌표 기준의 날씨(v1은 시군구 — §8-3)

## 10. 서버 간 내부 규약 (v1.31.0, FE 무관)

BE(`kickoff_be`)와 채팅 서버(`kickoff_chat`) 사이의 약속. FE는 이 절의 주소를 부르지 않는다.

**분리의 원칙**: 채팅 서버의 장애·부하가 BE의 동작을 실패시키면 안 된다. 반대 방향(BE가 죽으면 채팅의 새 입장·전송이
503)은 받아들인다 — BE가 죽으면 로그인·토큰 갱신부터 안 된다.

### 공통

- 경로는 `/internal/**`(`/api` 프리픽스 없음). 사용자 JWT 인증을 타지 않는다.
- 인증: 헤더 `X-Internal-Secret: <공유 비밀>`. 양 서버 env **`CHAT_INTERNAL_SECRET`**(같은 값, 비교는 상수 시간).
  없거나 틀리면 401 `UNAUTHORIZED`. **값이 비어 있으면 `/internal/**` 전체가 401이고 이벤트 전달도 멈춘다**(닫힌
  쪽으로 실패). 개발(dev) 프로파일에만 기본값이 있다. 비밀값은 문서·저장소·로그에 남기지 않는다.
- `/internal/**`는 **외부에서 닿지 않아야 한다**(사설 통신 또는 공개 경로에서 차단 — 인프라). 공유 비밀은 그 위의 두 번째 방어다.
- 서로의 주소: BE env `CHAT_INTERNAL_URL`(dev 기본 `http://localhost:8090`), 채팅 서버 env `BE_INTERNAL_URL`(dev 기본
  `http://localhost:8080`).
- 사용자 토큰: 채팅 서버는 BE와 같은 `JWT_SECRET`(HS256)으로 access token을 직접 검증한다. 클레임은 `sub`(userId)·`exp`만 쓴다.
- 데이터: 채팅 테이블(`chat_messages`, `chat_leaves`)은 채팅 서버 소유 — 자체 마이그레이션, **별도 데이터베이스**
  (운영은 같은 PostgreSQL 인스턴스 안의 `kickoff_chat` DB + 전용 계정). 다른 서버 테이블을 가리키는 FK는 없다 —
  `request_id`·`team_id`·`sender_team_id`·`sender_user_id`는 숫자 칸이다. 채팅 서버는 BE DB를 읽지 않는다.

### BE가 제공하는 것

#### GET /internal/chat/rooms/{requestId}

200 → `RoomContext` / 없는 신청 404 `REQUEST_NOT_FOUND`. **신청 상태와 상관없이** 돌려준다 — 권한·상태 판정(§6-1의
순서: 권한 먼저)은 채팅 서버가 한다.

```json
{ "requestId": 7, "status": "ACCEPTED", "postId": 12, "postTitle": "토요일 아침 상대 구합니다",
  "matchAt": "2026-08-30T07:00:00+09:00", "updatedAt": "2026-08-28T10:00:00+09:00", "blocked": false,
  "postTeam":      { "ownerUserId": 1, "team": "<TeamSummary>" },
  "applicantTeam": { "ownerUserId": 2, "team": "<TeamSummary>" } }
```

- `status`: §1 `RequestStatus`. `updatedAt`: 신청의 마지막 상태 변경(§6-1 방 목록 정렬의 보조값).
- `team`: §2 `TeamSummary`(평점 포함 — 방 목록의 `otherTeam`으로 그대로 나간다).
- `blocked` (v1.35.0): 두 팀의 감독 중 누구라도 상대 팀을 차단했으면 `true`(§7-2). 채팅 서버는 이 값이 `true`면 그 방을
  **보낼 수 없는 방**으로 다룬다 — `chatOpen: false`, `POST /chat`은 409 `CHAT_CLOSED`, 푸시 요청 없음. 읽기·나가기·방 목록은
  그대로다. 차단·해제로 `chatOpen`이 **실제로 바뀐 방에 한해**(v1.35.1 — 이미 닫힌 지난 경기 방에는 보내지 않는다) 구독 중이던
  연결에 `ROOM_STATE`를 보내되, 사유를 드러내지 않도록 `reason: "SCHEDULE_CHANGED"`로 보낸다(차단 사실을 상대에게 알리지
  않는다). 값이 없으면(옛 BE) `false`로 본다.
- 채팅 서버는 이 응답을 `requestId` 기준 **최대 15초** 캐시한다. 호출 타임아웃 2초, 무응답·5xx면 사용자에게 503
  `CHAT_UPSTREAM_UNAVAILABLE`.

#### GET /internal/chat/users/{userId}/rooms

200 → `RoomContext[]` — 그 사용자가 OWNER인 팀이 당사자인 **`ACCEPTED`** 신청 전부(§6-1 방 목록의 범위). 없으면 `[]`.
없는 사용자도 `[]`(404 아님 — 탈퇴 직후 토큰이 남아 있을 수 있다). 채팅 서버는 이 응답을 캐시하지 않는다.

#### POST /internal/chat/push

```json
{ "recipientUserId": 2, "requestId": 7, "postId": 12, "senderTeamName": "FC강서", "content": "그럼 7시까지 뵙겠습니다!" }
```

202 (본문 없음). BE는 받자마자 응답하고 §6-1 `CHAT_MESSAGE` 푸시를 비동기로 보낸다(title = 보낸 팀 이름, body = 내용 앞
50자, data는 §6-1). 받을지 말지(상대가 나갔는지, 소켓으로 구독 중인지)는 **채팅 서버가 판단한 뒤** 부른다 — BE는
다시 따지지 않는다(나가기 정보가 BE에 없다). 토큰이 없거나 푸시가 꺼져 있으면 조용히 버린다. 검증 실패는 400
`VALIDATION_FAILED` — 한도는 `senderTeamName` 30자, `content` 500자(v1.31.2). 채팅 서버는 `content`를 원문 그대로
보내고 50자 자르기는 BE가 한다. 채팅 서버는 요청 결과를 로그에 한 줄씩 남긴다(완료·실패, 그리고 받는 사람이 구독 중이라
생략한 경우 — requestId만. 상대가 나갔거나 SYSTEM 안내라 요청하지 않는 경우는 남기지 않는다).

### 채팅 서버가 제공하는 것

#### GET /health (v1.35.1)

공개 상태 확인 — 인증 없이 200 `{ "status": "UP" }`. 프로세스가 요청을 받는지만 알린다(버전·DB 상태·내부 정보를 싣지 않는다).
배포 스크립트와 외부 감시가 쓴다. BE의 같은 용도는 `GET /api/auth/signup-policy`다(§0).

#### POST /internal/events

```json
{ "eventId": 31, "type": "ROOM_INVALIDATED", "requestIds": [7] }
{ "eventId": 32, "type": "PURGE", "requestIds": [7, 9, 15], "teamIds": [3] }
```

204. 한 번에 이벤트 하나. 처리는 **멱등**이다(같은 이벤트를 여러 번 받아도 결과가 같다) — 전달은 "최소 한 번"이다.

| type | BE에서 일어난 일 | 채팅 서버가 할 일 |
|---|---|---|
| `ROOM_INVALIDATED` | 매칭 취소(§6-2), 수락된 신청이 있는 글의 `matchAt` 수정, 두 팀 사이의 차단·차단 해제(v1.35.0) | 그 방들의 `RoomContext` 캐시를 버리고, 구독 중인 연결에 `ROOM_STATE`를 보낸다(바뀐 것이 있을 때만). 메시지는 지우지 않는다 |
| `PURGE` | 회원 탈퇴로 팀이 삭제됨(§3-4), 그 밖에 신청 행이 지워지는 모든 경우 | `requestIds`의 메시지·나가기 기록 전부 삭제, `teamIds`의 나가기 기록 삭제, 그 방을 구독 중인 연결의 구독 해제 |

- 매칭 수락은 이벤트가 없다 — 방은 암묵적으로 존재하고 첫 접근 때 `RoomContext`로 확인한다.
- 팀 이름·평점 변화도 이벤트가 없다 — 방 목록은 매번 BE에서 읽는다.
- 수락된 글의 `matchAt` 수정은 현재 막지 않는다(v1.30.1까지의 동작 그대로 — 허용 여부는 별도 제품 결정). 그래서 채팅
  서버는 `matchAt`을 15초 넘게 믿지 않는다.

#### GET /internal/chat/rooms/{requestId}/report-context (v1.35.0 — 채팅 서버가 제공, BE가 부른다)

쿼리: `messageId`(필수), `reporterUserId`(필수), `before`·`after`(기본 20, 최대 50).

```json
{ "reportedMessage": "<InternalChatMessage>",
  "messages": [ "<InternalChatMessage>", "..." ] }
```

- `InternalChatMessage` = §6-1 `ChatMessage` + `senderUserId`(nullable). `messages`는 신고된 메시지와 그 앞 `before`개·뒤 `after`개,
  오름차순.
- **신고자에게 보이던 것만** 준다 — 신고자 팀이 방을 나간 적이 있으면 그 시점 이전은 빼고 준다(§6-1 나가기 규칙).
- 검사(채팅 서버, 이 순서 — v1.35.1): BE에 신청이 없으면 404 `REQUEST_NOT_FOUND` → 그 방의 `RoomContext`로 신고자가 당사자 팀의
  감독인지, 아니면 403 `FORBIDDEN` → 메시지가 없거나 그 방의 것이 아니거나 **신고자가 방을 나가기 전의 것이면**(신고자에게
  보이지 않던 메시지) 404 `NOT_FOUND` → `SYSTEM` 메시지이거나 신고자 팀이 보낸 메시지면 400 `VALIDATION_FAILED` → 보관 기간이
  지난 방은 410 `CHAT_EXPIRED`. 신청 상태(수락 여부)는 따지지 않는다 — 취소된 매칭의 대화도 지워지기 전에는 신고할 수 있다.
- BE는 호출 타임아웃 2초, 무응답·5xx면 신고 API가 503 `CHAT_UPSTREAM_UNAVAILABLE`로 답한다(채팅 메시지 신고에 한해 채팅 서버에
  기댄다 — 다른 대상의 신고는 채팅 서버와 무관하다).
- BE는 받은 문맥을 신고 기록에 **사본으로** 저장한다(운영자가 채팅 서버에 다시 묻지 않는다). 사본의 보관·삭제는 §7-2 「기록의 보관」.
  채팅 서버의 삭제 보류 표는 이 흐름에서 쓰지 않는다(사본이 증거다).

### 이벤트 전달 (BE 내부 규약)

- BE는 위 일이 일어나는 **같은 트랜잭션 안에서** `chat_outbox` 테이블에 한 줄을 넣는다 — 채팅 서버가 꺼져 있어도 BE
  동작(취소·탈퇴 등)은 성공한다.
- 스케줄러가 5초마다 미전송 행을 오래된 순으로 최대 50개 보낸다. 전송 완료 후 7일 지난 행은 지운다.
- **응답별 처리 (v1.31.2)**: 2xx → 전송 완료 / 5xx·타임아웃·연결 실패·408·429·그 밖의 예상 못 한 상태 → 지수 백오프
  (5·10·20·40초 … 최대 5분) 재시도, 횟수 제한 없음 / 401 → 공유 비밀 불일치(설정 사고)이므로 WARN을 남기고 재시도 유지 /
  그 밖의 4xx(408·429를 뺀 **모든** 4xx) → **5회 시도 후 실패(dead)로 표시하고 더 보내지 않는다**(ERROR 로그, `PURGE`면
  "수동 조치 필요"). "예상 못 한 상태"는 3xx처럼 4xx·5xx가 아닌 것을 뜻한다.
  dead 행은 7일 정리 대상이 아니다. 한 행의 실패가 뒤 행을 막지 않되, 채팅 서버가 죽어 있을 때 스케줄러가 묶이지
  않도록 한 바퀴는 20초를 넘기지 않는다(남은 행은 다음 바퀴에).
- **미전송 `PURGE` 경보**: 생성 후 1시간(`chat.outbox.purge-alert-after`)이 지나도록 전달되지 않은 `PURGE`는 행마다 ERROR
  로그를 남긴다(같은 행은 그 간격마다 한 번, dead가 된 행도 계속). 로그에는 이벤트 id·경과·시도 횟수만 적는다 —
  사용자·신청 식별값을 적지 않는다. 늦어도 5일 안에 지워지게 조치한다(개인정보 보호법 제21조 "지체 없이").
- `CHAT_INTERNAL_URL`이나 공유 비밀이 비어 있으면 전달을 멈춘다(행은 쌓인다).
- 탈퇴한 사용자의 대화는 채팅 서버가 살아 있으면 수 초 안에, 꺼져 있었으면 복구 직후에 지워진다.
- **탈퇴 뒤 접근이 막히는 시점 (v1.31.2)**: 탈퇴한 사용자(와 상대)의 그 방 접근은 `PURGE`가 도착하는 즉시(통상 5초
  안팎) 막히고, `PURGE`가 오지 않아도 `RoomContext` 캐시가 만료되는 **최대 15초** 뒤에는 막힌다. 그 사이에는 캐시에 남은
  판정(예: 이미 취소된 방이면 409)이 나갈 수 있다.
- **파기 기록**: 채팅 서버는 `PURGE`와 보관 기간 정리로 대화를 지울 때마다 "언제·어떤 사유로·방 몇 개·메시지 몇 건·
  나가기 기록 몇 건"을 INFO 로그 한 줄로 남긴다(내용·사용자 식별값 없음, 0건이면 남기지 않는다).
- **보관 기간 정리 (v1.33.0 — 기본 켜짐)**: 채팅 서버가 하루 한 번(04:30 KST) 기간이 지난 방의 메시지·나가기 기록을
  지운다. 지우기 전에 **반드시 BE에 그 방의 지금 상태를 확인**한다(캐시를 쓰지 않는다. 확인할 수 없으면 건너뛰고 다음 날).
  - 수락 상태이고 경기가 끝남 → `matchAt` + `CHAT_RETENTION_DAYS`(기본 **90**)일이 지났으면 지운다. **마지막 활동 시각은
    보지 않는다** — 기한이 지난 방은 통상 다음 정리(24시간 안)에 지워져야 하고, 경기 뒤의 나가기 때문에 후보에서
    빠져 있으면 안 된다.
  - 수락 상태가 아님(매칭 취소 등) → 신청의 마지막 상태 변경(`RoomContext.updatedAt` — 취소 시각) +
    `CHAT_RETENTION_CANCELED_DAYS`(기본 **30**)일이 지났으면 지운다.
  - BE에 신청이 없음(404) → 마지막 활동 + `CHAT_RETENTION_CANCELED_DAYS`일이 지났으면 지운다(정상이라면 `PURGE`가 먼저 지운다).
  - 경기 전인 방은 지우지 않는다.
  - env `CHAT_RETENTION_ENABLED`(기본 **true**). 끄는 것은 장애 대응용이고, 끄면 §6-1의 `deleteAt`·410도 함께 꺼진다.
  - §6-1의 `deleteAt`·410 판정은 같은 설정값(`CHAT_RETENTION_DAYS`)과 `RoomContext.matchAt`(15초 캐시)으로 한다.
  - 방 단위 **삭제 보류** 표(`chat_retention_holds`)에 있는 방은 정리에서 빠진다(신고·분쟁 대응 — 거는 수단은 지금 운영자의
    SQL뿐). `PURGE`는 보류보다 우선한다.
- 소유권 이전(§9 범위 밖)을 만드는 날에는 `PURGE`에 사용자 식별값을 실어 `sender_user_id`를 정리해야 한다(메모).

### 전환 순서 (v1.31.0) — **2026-11-15 네 단계 모두 완료 (v1.31.2)**

1. BE: 내부 API·`chat_outbox`·이벤트 기록을 **추가만** 한다(옛 채팅 API는 그대로 — 앱은 옛 방식으로 정상 동작).
2. 채팅 서버: §6-1의 REST 4개 + WebSocket + 이벤트 수신 + 푸시 요청.
3. FE: 채팅 Base URL로 전환. 이때부터 채팅 데이터는 채팅 서버에만 쌓인다(옛 데이터 이관 없음 — 운영 데이터가 없다).
4. BE: 옛 채팅 코드·테이블 제거(V25). `/internal/**`는 `X-Internal-Secret`이 맞을 때만 열리고, 그 필터가 빠지면 닫히는 쪽으로 고장 난다.
