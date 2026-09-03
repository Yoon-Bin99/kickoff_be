---
name: kickoff-project
description: "킥오프(조기축구 매칭 앱) 프로젝트 구조 — supervisor 3세션 체제, 계약서 위치, 운영 방침"
metadata: 
  node_type: memory
  type: project
  originSessionId: 5d07d63a-a7cb-4ad4-b4b6-8f920908b17b
  modified: 2026-09-03T00:00:01.454Z
---

**[인수인계 — 새 supervisor 세션에게 (2026-08-27 갱신)]**
이 메모를 읽는 세션이 새 supervisor다.
1. 즉시 할 일: ListAgents로 BE·FE 세션 확인(이름은 재시작마다 바뀜 — kickoff-be-*/kickoff-fe-* 패턴). 각각에 새 supervisor 인사 + 상태 보고 요청. 세션이 없으면 사용자에게 kickoff_be/kickoff_fe 폴더로 생성 요청
1-b. 세션이 죽으면 그 세션의 백그라운드 서버(8080 bootRun, 8081 metro)도 함께 죽는다 — 세션 교체·앱 재시작 후에는 두 서버 기동 여부부터 확인. H2라 8080 재기동 시 시드 외 데이터 초기화. LAN IP도 매번 ipconfig로 재확인(2026-08-27 현재 192.168.35.148 — 가상 어댑터 3개가 있어 게이트웨이와 같은 대역인 주소를 골라야 함)
2. 통신 함정: 앱 재시작 후 한방향 통신이 된 적 있음 → 그 경우 지시는 각 저장소 docs/supervisor-instruction.md 파일로 우회 전달(이행 후 삭제), 보고는 SendMessage로 받기. 앱 완전 재시작이 근본 해결
3. 원칙 요약: 계약서(원본 킥오프/docs) 변경은 supervisor만·변경 시 양 사본 동기화·양 세션 통지, git 푸시는 보고받고 supervisor가 승인/실행(BE main 푸시=Railway 자동 재배포 주의 — 사용자 부재 중 BE 푸시 승인 지시는 권한 분류기에 차단된 전례 있음, 사용자 있을 때 진행), 사용자에게 무조건 존댓말, md 수정 승인 위임받음, 방향성 결정은 사용자에게. 세션들의 "사용자 직접 지시" 원칙(전달 승인 거부)은 존중할 것

**가입 푸시 실기기 검증 완결 (2026-08-27 밤)**: dev 빌드(b7845777) 폰 재설치, metro 192.168.35.148:8081. JOIN_REQUEST_RECEIVED 수신·문구·탭 라우팅을 콜드 스타트/백그라운드/포그라운드 3상태 전부 통과. 과정에서 버그 2개 수정·푸시 완료: ① FE readPushData가 teamId 추출 누락 → 항상 /teams 폴백 (484aea8, asNumber 문자열 허용 포함) ② **콜드 스타트에서 Root Layout 마운트 전 router.push → 크래시** (v1.4.0부터 잠복, 매칭 푸시도 동일했음. 210cad1 — 탭 목적지를 보류했다가 내비게이터 마운트+복원 완료 후 단일 관문에서 1회 이동, 구독/콜드 스타트 공통). BE payload는 계약대로임을 JoinPushPayloadTest로 고정(cfe3616에 포함).
미검증 잔여: JOIN_ACCEPTED/REJECTED의 신청자 폰 수신 시나리오는 안 밟음(라우팅 관문 공통이라 위험 낮음 — 필요 시 테스트 계정이 팀을 만들고 사용자가 신청하는 구성으로 가능). FE metro 로그 캡처 함정: 파이프(| tail)는 버퍼링으로 로그가 안 흐름 — 파일 직접 리다이렉트로 할 것.
**RecordSummary 이중 생성자 버그 종결(2026-08-27)**: Hibernate 생성자 선택이 JVM 실행마다 달라져 재시작 시 기록 0건 팀의 팀 페이지·글 상세가 500 날 수 있던 잠재 버그. 생성자 단일화 + 가드 테스트로 수정, 전체 스위트 3회·실PG 2회 전부 278/278, cfe3616 푸시·재배포·프로브 6회 정상. (주의: 이 수정은 API 표면 무변경이라 신빌드 반영 자체는 프로브로 증명 불가 — 실질 반영으로 판단하고 종결.)
**푸시 승인 위임 확대(2026-08-27)**: 사용자가 "앞으로 승인 너가해"로 BE 푸시(=Railway 재배포) 포함 모든 푸시 승인을 supervisor에게 일괄 위임함. 2026-08-28 취침 전 "나 올때까지는 너가 승인권자"로 재확인 — 부재 중 supervisor가 커밋·푸시·배포 승인 전담. 단, 스키마 파괴·데이터 손실 위험 배포와 방향성 변경은 여전히 사용자 확인 대기로 남긴다.
테스트 계정: push-join-tester@kickoff.test / testpass1234 (userId 9, 운영). PENDING 잔여 없이 정리됨. 사용자 계정은 팀 6(인천 스트라이커즈) OWNER.

조기축구 팀 매칭 앱 "Kickoff" (2026-08-23 시작). 사용자는 한국어로 소통.

**협업 구조**: 이 세션이 supervisor. 같은 데스크톱 앱에 BE 담당(cwd kickoff_be), FE 담당(cwd kickoff_fe) 세션이 있고 SendMessage로 지시/보고를 주고받는다. 세션 이름은 ListAgents로 확인 (예: kickoff-be-b0, kickoff-fe-6d — 재시작하면 바뀔 수 있음).

**저장소**: `C:\Users\User\Desktop\킥오프\` 아래 kickoff_be (Spring Boot 4.1.1/Java 17/Gradle), kickoff_fe (Expo SDK 57/RN/TypeScript). 원격 github.com/Yoon-Bin99/{kickoff_be,kickoff_fe}.

**API 계약서**: 원본 `킥오프/docs/api-contract.md`, 각 저장소 `docs/`에 사본. 계약 변경은 supervisor만 하고, 변경 시 원본 수정 → 두 사본 동기화 → 양 세션에 통지가 규칙.

**운영 방침 (사용자 확정)**:
- 기능 단위 커밋 + origin main 푸시. 푸시 승인은 사용자가 supervisor에게 위임 ("푸시 승인 그냥 너한테 맡길게"). FE 세션은 전언 위임을 안 받으므로 FE 커밋은 supervisor가 대신 푸시한다
- 방향성 판단은 사용자에게, 기술 세부는 supervisor가 결정
- 매일 09:07 아침 보고서 스케줄 작업(kickoff-morning-report) 등록돼 있음
- 브랜드 컬러: 잔디 그린 #1B7F4B (사용자가 직접 확정)

**진행 상태 (2026-08-24 오후 기준)**: 리뷰·평점 기능 완결. 계약서 v1.2.2 (v1.2.0 리뷰 신설 → v1.2.1 RequestResponse.postTeam → v1.2.2 리뷰 에러 규정·2차 정렬 명문화). BE 테스트 68개 통과. iOS 실기기 테스트 진행됨(사용자 아이폰+Expo Go, FE를 SDK 54로 다운그레이드해 성공 — 부채 항목 참조). 개발 서버: BE 8080, Expo 8083 (LAN IP는 Wi-Fi 바뀔 때마다 변동 — 현재 192.168.35.95). FE dev 서버 기동 시 EXPO_NO_DEPENDENCY_VALIDATION=1 + REACT_NATIVE_PACKAGER_HOSTNAME=<LAN IP> 필수 (kickoff_fe CLAUDE.md·README에 기록됨). md 파일 수정 승인도 사용자가 supervisor에게 위임함.

**지도 완결 (2026-08-25)**: 계약 v1.5.0. 장소 검색(BE 프록시, KAKAO_MAP_REST_KEY — 공모전 카카오 앱 키 재사용, kickoff 앱 카카오맵은 결제 필요라 미사용), 좌표 nullable 쌍 검증(PATCH는 요청 본문 기준, 명시적 null로 지우기 — PatchableDouble), 글 상세 지도(WebView+카카오 JS SDK, baseUrl=Railway 도메인, JS 키는 EXPO_PUBLIC_KAKAO_MAP_JS_KEY), 웹은 링크만. 실기기 E2E 성공. 개발 빌드 8ccb7525(webview 포함)가 폰에 설치됨. EAS env에 지도 JS 키·GOOGLE_SERVICES_JSON 등록돼 있음. 운영 배포 정보는 BE README "현재 배포" 절에 문서화됨.

**v1.5.1 완결 (2026-08-25 오후)**: PATCH 지우기 일반화 — Patchable<T> 제네릭, 명시적 null=지움/필드 없음=유지 공통 규칙. depositAmount null이면 계좌 3필드 원자적 동반 삭제, 계좌 개별 null·빈문자열은 400(POST 포함), status null 불가. BE 테스트 157개, 운영 반영·확인 완료. Testcontainers도 완결 — postgresTest가 lower(bytea) 회귀를 실제로 잡는 것 증명(H2 0실패 vs PG 6실패). BE 재기동 통지 규칙이 FE 포함으로 확대됨.

**UX 개선+refresh 완결 (2026-08-25 오후)**: v1.6.0 활동 지역(가입 선택·홈 기본 필터·시/도 드롭다운), 숫자 페이지네이션(홈·리뷰), 메인 개편(브랜드 헤더·카드 위계·모집중 뱃지 제거), 가로모드(orientation default), v1.7.0 refresh token(access 1h/refresh 30d 로테이션, 불투명 난수+SHA-256, users 컬럼 V5, FE 단일비행 인터셉터·자동 로그인). BE 테스트 177개, 전부 운영 반영. 개발 빌드 b7845777(da8da94, webview 포함)이 폰 설치됨 — 서명 2b4c5c00 통일. 사용자 확인: 메인·가로모드 OK, 새 글 지도 OK. 미확인: 1시간 후 자동 로그인 유지, 소셜 네이티브 복귀의 refreshToken. 클라우드 기존 글은 좌표 null(의도) — 소급 채우기는 사용자 보류 중.

**지인 테스트 예정**: 사용자가 예고함. 같은 Wi-Fi면 현 APK로 바로, 원격이면 터널 모드 필요(검증돼 있음 — @expo/ngrok --no-save, EXPO_PUBLIC_API_URL 조합. 명령은 FE README). 아이폰 지인은 불가(안드로이드 빌드만 존재).

**팀 페이지 완결 (2026-08-25 저녁)**: v1.8.0(팀원 명단 30명·수동 전적 TeamRecord·프로필 확장 foundedYear/teamColor 9색 팔레트/formation, V6) + v1.9.0(팀 관리자 — OWNER>ADMIN 이메일 임명 5명, ADMIN=팀 페이지 수정만, 매칭·리뷰는 OWNER 전용, TeamResponse.myRole, TeamAuthz 일원화) + v1.9.1(GET /users/me/teams — 마이탭 소유/관리 팀 진입점). BE 테스트 227개, FE 교차 검증 전 항목 통과, 홈 카드 팀 이름→팀 페이지 동선 추가. 페이지네이션·활동 지역 사용자 폰 확인 완료. 재빌드 불필요(JS만).

**고도화 v1.10.0 완결 (2026-08-26 오전)**: ① 매칭→전적 연동 — POST /api/requests/{id}/record(스코어만 입력, 상대·날짜 서버 유도, 리뷰 패턴 대칭, V7 유니크 request_id+team — 부분 인덱스 대신 NULL 성질 활용해 H2/PG 동일 동작), RequestResponse.myRecordWritten, TeamRecord.requestId, 에러 2종 ② TeamSummary에 averageRating/reviewCount(배치 집계, N+1 쿼리 카운트 테스트, TeamSummary.from 제거로 컴파일러가 전 자리 강제). BE 테스트 245개(H2+실PG 양쪽), FE 교차 검증 전 항목, 운영 배포·확인 완료. 홈 카드 별점은 운영에서 즉시 보임, 전적은 사용자가 채우는 구조. 세션 통신이 앱 재시작 후 한방향이 된 적 있음 — 지시를 docs/supervisor-instruction.md 파일로 우회 전달하는 방법이 통했음(재시작으로 양방향 복구됨, 지시 파일은 검증 후 삭제 예정).

**지인 테스트 개시 가능 (2026-08-26)**: preview 빌드 b030d0bb(커밋 4fa808e, v1.10.0 포함, Railway 직결, 단독 실행) 사용자 폰에서 검증 완료. APK 직링크: https://expo.dev/artifacts/eas/cJSNZFGduNWr9naw9YsAqkS8EheZmnPIxG8IH42tgeo.apk — 안드로이드 전용. 지인 안내문: docs/friend-test-guide.txt. 사용자 폰의 dev 빌드는 preview로 덮임(개발 재개 시 dev 빌드 재설치 필요 — 빌드 b7845777 또는 새로). 지시 파일(docs/supervisor-instruction.md 양쪽) 삭제 승인됨.

**팀 소속 v1.11.0 완결 (2026-08-26 오후)**: MEMBER 역할(조회·소속 표시만, 권한표에 명시적 열거 — TeamAuthz 권한 누수 사전 차단), 가입 신청/수락/거절/취소/탈퇴(매칭 신청 패턴, PENDING 유니크는 서비스 검사 — match_requests 선례), 명단-계정 통합(TeamMember.userId, 수락 시 자동 등재, 연결 항목 name은 닉네임 추종·삭제=강퇴), TeamResponse.myJoinStatus(GET /teams/{id}에서만), 팀 검색 GET /api/teams, 하단 탭 4개(홈/팀/매칭/마이), 가입 푸시 3종(JOIN_*), V8. BE 테스트 272개(H2+실PG), FE 교차 검증 전 항목, 운영 배포·확인 완료. FE 인터셉터 버그 수정(없는 경로 401을 세션 만료로 오인해 로그아웃 — ef83e10). 미검증: 가입 푸시 실기기 라우팅(재빌드 후 항목).

**⚠️ 지인 테스트 준비는 사용자가 명시 지시할 때까지 착수 금지 (2026-09-01 "내가 하랄때까지 하지마"). 재개 시 준비물: ① v1.15~24 전부 포함 preview 재빌드(구 APK는 인증 강제 ON이라 가입 불가) ② 안내문 갱신 ③ 카카오 키 해시 등록 확인 ④ ~~운영자 계정 보안~~ 해결됨(2026-09-02 — 운영자가 사용자 실계정 shp06135@naver.com으로 교체됨).**

**preview 재빌드 완료 (2026-08-28 오후)**: 빌드 80c1d0ed(커밋 ba25277, 오늘까지 전 기능 포함, Railway 직결·단독 실행). APK 직링크: https://expo.dev/artifacts/eas/ZT678ewYdrYJEzUSaZpPyGTDVaWq8znXUeXAxtpl848.apk — 지인 안내문(킥오프/docs/friend-test-guide.txt) 새 링크·기능·제한사항으로 갱신됨. **키 해시는 dev와 동일 확인**(55351Zoek+tS5Jllf8zktk6CORM=, 같은 키스토어 — 카카오 콘솔 추가 등록 불필요. APK 서명 블록 직접 파싱으로 검증, keytool은 v1 서명만 읽어 실패함). 사용자 폰에 preview 설치 시 dev 빌드 덮어씀 주의 — dev 재설치 링크는 ehC_JJ2m...apk(빌드 25d438cf).

**공유하기 (2026-08-28 새벽 지시)**: 모집글 상세에 OS 공유 시트(RN Share API)로 텍스트 요약 공유 — 단톡방 용도, 재빌드 없이. FE 전용, 채팅 다음 순서로 지시됨. 링크는 앱 미배포라 제외(배포 후 추가는 백로그).
**순서 확정(2026-08-28, 사용자)**: v1.13.0 배포 완료 후에 카카오 SDK 착수 — "sdk는 배포 후에 하자".

**카카오 SDK 재빌드 완료 (2026-08-28 오후)**: dev 빌드 25d438cf(APK https://expo.dev/artifacts/eas/ehC_JJ2m6SWHrLU6ynE6o7j1CA4VIfop3H-KkuiQmQQ.apk) 사용자 폰 설치됨. @react-native-kakao/core+share+social(1차 빌드 실패 원인: social gradle 의존 누락), keyboard-controller+reanimated 포함. **채팅 키보드 해결 확정**(5355af0) — keyboard-controller가 높이는 정상 수신(h397)하나 KAV가 미반영 → 측정 높이를 화면 하단 여백으로 직접 적용, "내려가는 느낌"은 isVisible에 나브 바 여백 제거를 묶은 FE 버그였음. 키 해시 55351Zoek+tS5Jllf8zktk6CORM= (getKeyHashAndroid 로그 방식 — eas credentials 불필요, 프로파일별 키스토어 함정 자동 해소). 카드 공유 이미지: 운영 /share-card.png (BE 정적, GET·HEAD 200). ~~사용자 폰 계정 = yoon@example.com~~ → 2026-09-02 계정 이전으로 shp06135@naver.com.
실기기 검증 결과(2026-08-28 오후): **카카오 카드 공유 완전 종결**(카드+이미지+버튼+딥링크 → 글 상세. 콘솔 등록 완료, Unmatched Route는 +native-intent 추가로 해결 — 85f097d). **JOIN_ACCEPTED 푸시·탭 라우팅 정상**. JOIN_REJECTED는 join9로 거절돼 확인됨.
**v1.14.0 완결·배포(2026-08-28 15:27)**: 팀 검색 정규화(공백·대소문자 무시, Locale.ROOT)+정확도 정렬(전방일치 우선)+LIKE 와일드카드 리터럴화(keyword·region, 이스케이프 문자 '!', 정렬 절 escape 누락까지 가드). BE 333개 H2+실PG, FE 교차 검증·운영 프로브 통과(2b802b6). region trim은 자유 입력 도입 시점으로 보류. 지역 필터는 v1.11.0에 이미 있었음(종결). 오타 유사도는 §9 범위 밖. **가입 푸시 3종 실기기 종결**, 푸시 탭 중복 스택 수정(b35edb3) 확인 완료. **용어 통일 완료**: 사용자 노출 "주장"→"감독"(FE 9527bbf·6036baa, BE 해당 없음, 계약서 v1.14.0-용어).

**백로그**: ⑨ BE 시드 자동 스모크 부재 ⑩ 시드가 목록 인덱스로 상태 부여(중간 삽입 시 조용히 틀림 — 구조 개선) ⑪ 심야 필터 "다음날 새벽 포함" 힌트(실사용 혼란 관찰 시) ① 홈 목록 지도 뷰·반경 검색(계약상 범위 밖) ④ 터널 모드 정식 채택 여부 ⑫ 닉네임·이메일 **대소문자 무시 DB 제약** — H2/PG의 함수 인덱스·생성 컬럼 문법이 정반대라 단일 SQL 불가(실측됨). 방침 A(전화번호만 DB 유니크, 닉네임·이메일은 정확일치 unique+앱 409 이중 방어)로 결정(2026-08-31). 방언 분기(Java 마이그레이션/{vendor} 폴더)가 어차피 필요해지는 날 재검토 ⑥ 공유하기에 링크 첨부(앱 배포 또는 공개 웹 생긴 후) ⑦ 카카오 카드형 공유(SDK+재빌드 필요 시 검토) ⑤ FE app/_layout.tsx 인증 라우팅 effect의 segments 의존성 — 이동 실패 시 무한 루프 촉발 함정(2026-08-27 콜드 스타트 버그 때 드러남, 근본 원인은 별도 수정됨. 별건으로 단단히 할지 검토) ⑬ hasPassword 필드(UserResponse) — 계정 통합(v2) 설계 때 함께

**토스 송금·계좌 복사 완료 (2026-08-28 새벽)**: 입금 안내(PaymentBox)에 토스 딥링크(supertoss://send, 은행·계좌·금액 프리필, openURL try/catch 폴백 — 재빌드 불필요)·복사 버그 수정(피드백 2초 복귀). 작성자·웹에는 토스 버튼 숨김. c041013 푸시 완료. **실기기 검증 완료(2026-08-28 오전)**: supertoss://send?bank&accountNo&amount 형식 실증됨. **카카오페이 송금 연동은 사용자 결정으로 안 함(2026-08-28 "냅두자")** — 공식 API 부재, 비공식 스킴은 부채라 현행(토스+복사) 유지.

**매칭 채팅 v1.12.0·v1.12.1 완결(2026-08-28)**: 수락된 매칭의 두 팀 감독(OWNER) 1:1 채팅, matchAt까지 전송·이후 읽기 전용, 폴링(after 커서)+CHAT_MESSAGE 푸시, §6-1. METHOD_NOT_ALLOWED 405 신설(§0). 운영 배포·실기기 E2E 통과.

**v1.13.0 채팅 탭·나가기 운영 배포 완료 (2026-08-28 10:42)**: BE 556d4ed(V10 — ChatMessage.type/SYSTEM, GET /users/me/chats, leave 워터마크+hidden, 복귀), FE 4353e7a까지 전부 푸시. 테스트 314개 H2+실PG, 교차 검증 5항목 통과, 운영 프로브 통과(V10 백필 실증). 확인 대화상자 헬퍼 통합(confirm.ts) 포함. 사용자 실기기 확인 완료 — **v1.13.0 종결**.

**v1.15.0 전화번호 문자 인증 완결·배포 (2026-08-29 21:04)**: 계약 §3-2 — verifications(발송 6자리·3분·레이트리밋 1분1회/시간5회) → confirm(→verificationToken 10분·1회용·5회 실패 무효) → signup·PATCH phone에 토큰. **PHONE_VERIFICATION_REQUIRED 강제 스위치(기본 false)** + FE 구버전 감지 — 어느 배포 순서든 가입 안 막힘. BE d2e6723(V11 phone_verifications), FE 27cceab. PASS 실명인증은 §9 범위 밖(사업자 후).
**v1.16.0 가입 폼 강화 완결(2026-08-29~30)**: ① 이메일·닉네임·전화번호 유니크(V12)+GET /api/auth/availability 사전 확인 ② 비밀번호 8~64+영문·숫자·특수문자 각 1+(비소급) ③ FE 이메일 분리 입력(도메인 드롭다운+직접입력) ④ v1.16.1 GET /api/auth/signup-policy(서버 강제 여부를 FE가 따름 — 존재 기반 감지 폐기).

**SMS 롤아웃 완결(2026-08-30)**: 솔라피(발신번호 010-8513-7975, 잔액 ~280원), BE 어댑터(SMS_ENABLED 검사 서비스 레벨, 실발송 시 코드 비로깅), Railway env 4종, **운영 실발송 엔드투엔드 확정**. 운영에 프로브 계정 3개 잔존(probe-v15@·fe-prod-<epoch>@·baseline-probe@example.com — 전부 팀 없어 무해, 유지). **규칙 확립: 운영 쓰기가 필요한 검증 항목은 실행 전 supervisor 승인 필수, 비생성 대안 우선 검토. 운영에서 우리 소유 아닌 대상(번호·메일)으로 발송 절대 금지 — 시드 번호 실문자 사고의 교훈. 프로브 수신자는 .invalid 같은 도달 불가 주소로.**

**v1.17.0 전화번호 기반 1인 1계정 완결(2026-08-30)**: confirm 응답 existingAccount(번호 소유 증명 후에만, EMAIL/KAKAO/NAVER + 마스킹 이메일), 소셜 게이트(policy true+phone null → 등록 강제), 로그인된 화면에서는 "로그아웃 필요+두 출구" 안내. **진짜 계정 통합은 §9 v2 보존**. **카카오 로그인 완결(2026-09-01)** — 연쇄 원인: KOE101(무효 클라이언트ID) → KOE004(로그인 비활성) → KOE205(동의항목 닉네임) → KOE010(시크릿 불일치). 새 콘솔: 리다이렉트 URI="플랫폼 키>REST API 키" 안, Web 도메인="일반>앱 대표 도메인". 진단: 무헤더 authorize 302 Location, 가짜 code 토큰 교환(KOE010=자격 불일치/KOE320=자격 정상). FE 관례: 사용자 확인 중·임박 시 8082에서 작업, 완성분만 8081 반영.

**⚡ 운영 인증 강제 ON (2026-08-30 저녁, 사용자 직접)**: PHONE_VERIFICATION_REQUIRED=true — **운영 가입은 문자 인증 필수, v1.15~17 전부 활성**.

**v1.18.0+v1.19.0 완결·배포(2026-08-30 저녁, 02c685b)**: ① 홈 날짜·시간대 필터 — dates(YYYY-MM-DD 콤마, 최대 14, KST, OR)·times(TimeSlot: DAWN 05-08/MORNING 08-12/AFTERNOON 12-18/EVENING 18-22/NIGHT 22-05, 경계 시작포함·끝제외, OR), 서로·타 필터와 AND ② **제품 결정: 11대11 전용** — FieldType 폐지. JVM Asia/Seoul 고정 운영 실증. 백로그 추가: UTF-8 무효 쿼리 바이트 → 500(400이어야).

**v1.20.0 매칭 취소 완결(2026-08-31)**: POST /requests/{id}/cancel-match, MATCH_CANCELED 상태(기존 CANCELED=수락 전 철회와 구분), 글 OPEN 복구, 연락처·채팅 닫힘, depositPaid 보존, MATCH_CANCELED 푸시. 운영 실사용 확인(2026-09-02 계정 이전 때).

**v1.21.0 약관·개인정보 동의 완결(2026-09-01)**: 이용약관·개인정보처리방침(킥오프/docs — 국외 이전·솔라피 위탁·연락처 공개·베타 고지), BE GET /terms·/privacy 정적 서빙 + signup termsAgreed 필수. 사고 1건: Docker 이미지 docs 미포함으로 500(18분) → .dockerignore 예외+COPY docs+빌드 시 복사 검증 가드. 교훈: "Gradle from()은 원본 없어도 조용히 성공".

**v1.22.0 고객센터 완결(2026-09-01)**: 3단 응대(FAQ 퀵버튼 → AI 상담사(Claude API) → 운영자 연결 — 비동기 문의 접수 UX). §7-1 사용자↔운영자 1:1 채팅, sender enum(USER/AI/OPERATOR), operatorMode, SUPPORT_RATE_LIMITED 429. FAQ·AI 지식 원본 = 킥오프/docs/support-knowledge.md(supervisor 관리, BE 리소스 빌드 복사). **AI는 크레딧 미충전으로 미가동 — 켤 때 Railway: ANTHROPIC_API_KEY(확보됨)+AI_SUPPORT_ENABLED=true, $5 충전 후 실호출 스모크.**

**v1.23.0 비밀번호 재설정+회원 탈퇴 완결·배포(2026-09-02 11:39)**: §3-3 password-reset(항상 204 존재 비노출·비동기 발송·이메일 문자열 기준 레이트리밋·confirm 시 refresh 전폐기·5회 잠금 noRollbackFor 함정 재발→테스트로 잡음), §3-4 DELETE /users/me(이메일 계정 password 재확인 PASSWORD_MISMATCH 400, 예정 매칭 양방향 차단 ACTIVE_MATCH_EXISTS 409, 소유 팀 전체 삭제·상대 TeamRecord는 requestId null 보존, phone 유니크 해제, 탈퇴 후 토큰 401). 팀 나가기는 기능이 이미 있었고 발견성 문제 — "팀 나가기" 문구+버튼화로 해결. 프로브 교훈: .invalid 주소 사용(도달 불가 보장), 감지/프로브 주소 분리(한도 오염 방지).

**v1.24.0 계정 편의 3종 완결·배포(2026-09-02)**: ① §3-5 아이디(이메일) 찾기 — FE 전용, §3-2 confirm existingAccount 재사용, 마스킹만 ② §3-3 개정: 재설정 전 availability 사전 존재 확인 → 미가입 "계정이 없습니다"(사용자 결정) ③ PATCH /api/users/me/password(currentPassword 재확인, 무형식 — 옛 규칙 비밀번호도 통과해야 함, 세션 유지) ④ §0 승격: "인증 불필요 경로 401=미배포 서버" + "401은 필터 단계, 404·405는 라우팅 단계" + "새 API 낼 때 BE는 미배포 응답을 함께 알린다". FE "최근 로그인" 배지(자동 로그인은 기록 안 덮음, 가입 성공도 EMAIL 기록).

**📍 2026-09-02 저녁 — 계정 이전·메일 발송 전면 종결**: ① 사용자 계정 이전 완료 — yoon 탈퇴(운영 첫 실탈퇴+매칭 취소 실사용), **재가입 = shp06135@naver.com**(이메일 가입·실번호 010-8513-7975, 지메일 아님 주의!) = 새 운영자(SUPPORT_OPERATOR_EMAIL 교체 적용). 인천 스트라이커즈 삭제됨, 팀 재생성은 사용자 몫. ② **Gmail SMTP는 Railway 아웃바운드 차단으로 불가 확정**(587 Connection timed out, 인증 이전 단계 — 465도 같은 정책) → **Brevo HTTP API 전환**(무료 300통/일, 발신자 yb85137975@gmail.com 인증됨). 팩토리 단일 @Primary 구조(Brevo→SMTP→Logging 우선순위, 키 동시 존재 시 기동 사망 지뢰 사전 재현으로 배제), adapter= 주입 로깅. **실발송 스모크 성공**(네이버 실도착). Railway env: BREVO_API_KEY(**대화 노출 — 재발급 목록**, 지메일 앱 비밀번호도 동일 — 값은 Railway에만 있음), SMTP 4종 잔존(무해, Brevo 우선). **EMAIL_FROM 규칙: SMTP env 지우면 EMAIL_FROM 필수 유지**(비면 SMTP_USERNAME 폴백 — Brevo 인증 발신자와 일치해야 함). ③ 배포 판정 기법: 인증 필요 신설 엔드포인트 = OPTIONS 프로브(구 404/신 200, allow 헤더로 메서드 매핑까지, 부작용 0·자격증명 0); 로그만 바뀌는 배포는 기동 로그 한 줄을 사용자가 판독.

**v1.24.1 완결·배포(2026-09-02 저녁)**: 운영 = BE 3094e51 / FE b144c3e, 8081 worktree b144c3e. ① 용어 "아이디 찾기"("아이디(이메일)" 병기, 소셜 분기는 "계정" 유지) ② 계정 관리 진입 비밀번호 게이트(POST /api/users/me/verify-password, fail-closed라 BE 선배포 필수였음, 진입마다·캐시 없음, 소셜 무게이트, 옛 규칙 비밀번호 통과 실증). 시드 uid=8(email 있는 소셜 — v1.3.4 위반 픽스처) 정정, 운영엔 그 행 없음 확인. 참고: 탈퇴는 비밀번호 2회(게이트+확인) — 번거로움 피드백 가능 자리.

**잔여(2026-09-02 저녁 기준)**: 사용자 재설정 완주(메일 코드로), 새 계정 문의함 노출 확인, 팀 재생성(사용자), AI 가동($5 충전 대기), 도메인 구입(메일 스팸함 회피 — 배포 체크리스트). 재설정 반쪽 상태는 해소됨(Brevo 실발송 가동 중). SMTP 없이 지인 테스트 시나리오 대비책이던 signup-policy passwordResetEnabled 확장은 불필요해짐.

**📍 2026-09-01 종료 시점 상태**: 운영 = v1.22.0(FE 60f0d26 / BE 2b71e1b+5bc9ee9 docs). 고객센터 완결: FAQ 8종·문의 접수 UX(비동기)·운영자 문의함.

**사기 방지 논의(2026-09-01)**: 에스크로(중간 보관 후 경기 뒤 지급)는 **사업자+PG 제휴 단계 1순위 백로그**(법적으로 지금 불가). 신고 기능은 사용자가 취소("만들지마, 그냥 고객센터 문의하게 하면 되겠다"). 사기 억지력은 현행(리뷰·전화인증 1인1계정·입금확인·매칭취소)+고객센터 문의로 유지.

**회비 기능 보류 결정 (2026-08-28)**: 실제 자금 보관·이체는 전자금융거래법상 불가, 순수 장부 방식도 사용자가 "하지 말자". 재론 시 장부 방식(모임통장 + 앱은 기록만)이 출발점.

**Remote Control 주의**: "새 세션을 Remote Control에 연결" 토글은 토글 이후 **새로 만든** 세션에만 적용됨. 데스크톱 앱에는 /rc 명령 없음. 폰에서 보이려면 세션을 새로 생성해야 함.

**포트 정리**: FE 세션은 프로세스 종료가 권한상 차단됨. supervisor 대행은 권한 우회라 FE가 거절(원칙 맞음). supervisor 자신의 조율 판단으로 고아 프로세스 정리는 가능. 정공법은 사용자가 FE 세션에서 "kill-port 명령 허용해줘"라고 직접 지시.

관련: [[kickoff-user-prefs]]

**소셜 로그인 (2026-08-24 완성)**: 카카오·네이버 OAuth, 계약 v1.3.x. 리다이렉트 방식(BE가 제공자 교환 전부 처리). 소셜 계정은 **항상 별개 계정**(자동 연동 없음, email 항상 null — 사용자 결정·계약 불변식), 전화번호는 팀 생성 시 보완(PHONE_REQUIRED). 구글·애플은 enum만 예약. 키 4종(KAKAO_CLIENT_ID/SECRET, NAVER_CLIENT_ID/SECRET)은 gitignore된 로컬 파일(kickoff_be/local.yaml)로 관리 — **이 파일은 git에 없음, 새 PC에서는 Railway Variables 값을 보고 재작성** (BE README 참고). **카카오 새 콘솔 함정**: Redirect URI 등록 위치가 [앱 > 플랫폼 키 > REST API 키] 안(2026 개편). LAN IP가 바뀌면 카카오·네이버 콘솔의 Redirect/Callback URL 재등록 필요.

**배포 1단계 완료 (2026-08-24 오후)**: BE+PostgreSQL이 Railway에 올라감. 도메인 https://kickoffbe-production-7275.up.railway.app (고정). Flyway 마이그레이션(현재 V13), Dockerfile 배포, 환경변수는 Railway Variables(DB는 ${{Postgres.*}} 참조, JWT_SECRET, 소셜 키 4종, OAUTH_CALLBACK_BASE_URL, OAUTH_ALLOWED_REDIRECTS=kickoff://*,exp://*, SEED_DATA=true, PUSH_ENABLED, SMS 4종, PHONE_VERIFICATION_REQUIRED=true, SUPPORT_OPERATOR_EMAIL=shp06135@naver.com, SMTP 4종+EMAIL_ENABLED+EMAIL_FROM, BREVO_API_KEY). 주의: BE main 푸시가 Railway 자동 재배포 트리거. Railway 변수 추가 시 "Deploy/Apply changes" 배너를 눌러야 반영.

**푸시 알림 완결 (2026-08-24 밤)**: 알림 4종 실기기 수신 검증. EAS: 계정 yoonbin99, projectId 19482b5c-a2cd-4e22-a060-02dcf3e5908d. FCM V1 키 등록됨(사본 C:/Users/User/Downloads/fcm.json — **구 노트북에만 있음, EAS에 등록돼 있어 재다운로드 불필요**). Railway PUSH_ENABLED=true. eas-cli는 npx eas-cli@latest로 실행.

**재부팅 후 복구 체크리스트**: BE/FE 세션 살아있는지 ListAgents로 확인(죽었으면 kickoff_be/kickoff_fe cwd로 재생성 요청), 로컬 서버 재기동 — BE는 `./gradlew.bat bootRun`(키는 local.yaml 자동), FE는 EXPO_NO_DEPENDENCY_VALIDATION=1 REACT_NATIVE_PACKAGER_HOSTNAME=<LAN IP> EXPO_PUBLIC_API_URL=https://kickoffbe-production-7275.up.railway.app npx expo start --lan --port 8081. LAN IP는 ipconfig로 재확인. 클라우드(Railway)는 재부팅 무관하게 살아있음. 8081은 kickoff_fe_preview worktree(운영 연결 고정 스냅샷), 편집·커밋은 원본 kickoff_fe — 검증은 8082.

**로고·아이콘 완료 (2026-08-24 밤)**: 01-ball-sunrise(새벽 공) 확정. icon/adaptive-icon/splash/favicon 적용(배경 #1B7F4B), 재생성 스크립트 assets/logo-drafts/generate.cjs. 미채택 시안·요청서(docs/logo-design-brief.md) 보관.

**배포 체크리스트 누적**: ① FE SDK 57+ 재업그레이드 ② 소셜 키 재발급·분리(채팅 노출 — 카카오 네이티브 앱 키 91376de0...도 app.json 커밋 포함) ③ 솔라피 API 키 재발급(노출) ④ PASS 실명인증(사업자 후) ⑤ 알림톡 전환 검토(건당 6~8원, 사업자+채널) ⑥ 약관·방침의 문의처·책임자를 회사 정보로 교체 + 법률 검토 ⑦ iOS 지원(애플 개발자 계정 연 $99) ⑨ 수익화(스토어 출시 후): AdMob(축구 위주 카테고리) + 지역 직판 광고(구장·유니폼 업체 배너 — 사용자 관심) ⑩ 에스크로(PG 예치·정산 — 사업자 후 1순위) ⑪ Anthropic API 키 재발급(노출) ⑫ Brevo API 키·지메일 앱 비밀번호 재발급(노출) ⑬ 메일 발신 도메인 구입(스팸함 회피) ⑭ 카카오 비즈 앱 전환(이메일 동의 필요 시) ⑮ OAuth 토큰 전달 쿼리→일회용 코드 교환 ⑯ redirect 허용 목록 운영값 축소 ⑰ **안드로이드 키스토어 백업 필수**(eas credentials에서 다운로드, 마법사에서 keystore 항목 건드리지 말 것 — 과거 서명 불일치 충돌 전례)

**SDK 다운그레이드 부채 (2026-08-24)**: iOS Expo Go 테스트를 위해 FE SDK 57→54. **배포(스탠드얼론) 단계에서 57+ 재업그레이드 약속.**
