# kickoff_be

조기축구 팀 매칭 앱 "Kickoff"의 REST API 서버. 프론트엔드는 옆 저장소
`kickoff_fe`(Expo / React Native)가 담당한다.

- API 명세: **[docs/api-contract.md](docs/api-contract.md)** — 엔드포인트, DTO, 에러 코드, 열거형이 전부 여기 있다
- 코드 규칙과 설계 배경: [CLAUDE.md](CLAUDE.md)

## 요구사항

**JDK 17**이 필요하다. Gradle은 wrapper가 들어 있어 따로 설치할 필요 없지만,
**wrapper 자체가 JVM 위에서 돌기 때문에 JDK는 반드시 먼저 깔아야 한다.**
JRE만으로는 빌드되지 않는다.

새 PC라면 이것부터 한다.

```bash
winget install --id EclipseAdoptium.Temurin.17.JDK -e
```

설치되면 MSI가 `JAVA_HOME`을 시스템 수준으로 잡아준다. 확인:

```bash
./gradlew --version   # Launcher JVM 이 17.x 로 나오면 정상
```

> **`java -version`으로 확인하지 말 것.** 윈도우에 Oracle Java 8이 함께 깔려 있으면
> PATH의 `java`가 `C:\Program Files (x86)\Common Files\Oracle\Java\java8path\java.exe`
> shim을 가리켜 **8이 찍힌다.** Gradle은 `JAVA_HOME`을 우선하므로 빌드는 멀쩡한데,
> `java -version`만 보면 JDK가 없다고 오판하게 된다. 실제로 이 상태를 한 번 겪었다.
>
> 설치 **이전에 열어둔 셸**은 환경변수가 낡아 `JAVA_HOME`이 비어 있을 수 있다.
> 그때는 셸을 새로 열거나 명령 앞에 붙인다:
> `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"`

## 실행

```bash
./gradlew bootRun
```

Windows cmd/PowerShell에서는 `gradlew.bat bootRun`.

`dev` 프로파일이 기본이라 별도 설정 없이 뜬다. H2 인메모리 DB를 쓰고, 기동할 때마다
시드 데이터(팀 6개, 모집글 34개, 신청 13건, 리뷰 4건)가 새로 들어간다. 서버가 내려가면 데이터도 사라진다.

- 기본 주소: <http://localhost:8080>
- `0.0.0.0`에 바인딩하므로 안드로이드 에뮬레이터에서는 `http://10.0.2.2:8080`으로 붙는다

> **매칭 채팅은 이 서버에 없다** (계약서 §6-1·§10, v1.31.0). 별도 서버 `kickoff_chat`이
> 답한다 — BE에는 `/api/requests/{id}/chat`도 `/api/users/me/chats`도 더 이상 없다.
> **화면에서 채팅을 쓰려면 `../kickoff_chat`에서 그 서버(8090)도 함께 띄워야 한다.**
> 안 띄워도 BE는 정상이고 다른 기능도 멀쩡하다 — 채팅 화면만 503이 되고, BE가 보낼
> 이벤트(매칭 취소·탈퇴 알림)는 `chat_outbox`에 쌓였다가 채팅 서버가 살아나면 따라간다.
>
> 두 서버를 잇는 설정은 `chat.internal.secret`·`chat.internal.url`이고 dev 기본값이
> 양쪽에 같게 박혀 있어 로컬에서는 손댈 것이 없다(아래 env 표 참고).

코드를 고치면 **서버를 직접 재시작해야 한다.** devtools 자동 재시작은 꺼놨다 —
gradle이 클래스를 다시 쓰는 도중에 재시작이 걸리면 컨트롤러가 일부 빠진 채로 뜨는
일이 있어서다(자세한 내용은 커밋 `8dc8749`).

## 시드 계정

비밀번호는 전부 `pass1234`. 마지막 한 계정을 빼고 각 계정이 팀을 하나씩 소유한다.

| 이메일 | 닉네임 | 팀 | 지역 |
|---|---|---|---|
| kim@example.com | 김주장 | FC 새벽 | 서울 강서구 |
| lee@example.com | 이감독 | 마포 유나이티드 | 서울 마포구 |
| park@example.com | 박캡틴 | 송파 FC | 서울 송파구 |
| choi@example.com | 최총무 | 고양 킥커스 | 경기 고양시 |
| jung@example.com | 정주장 | 성남 레인저스 | 경기 성남시 |
| yoon@example.com | 윤코치 | 인천 스트라이커즈 | 인천 남동구 |
| consent@example.com | 동의대기 | 없음 | 서울 |

마지막 `consent@example.com` 은 **동의 기록만 없는 계정**이다 (계약서 §3 「동의」).
로그인은 되고 그 뒤의 인증 API 가 전부 403 `CONSENT_REQUIRED` 로 막히므로, FE 가 동의
화면을 로컬에서 밟을 때 쓴다. 다른 여섯 계정은 세 항목 + 연락처 제공 동의까지 채워져
있어 그 화면이 뜨지 않는다. 팀이 없어서 목록·검색에는 나오지 않는다.

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"kim@example.com","password":"pass1234"}'
```

응답의 `accessToken`을 `Authorization: Bearer <token>` 헤더에 넣어 쓴다.

### 로그인하면 보이는 것

갓 적재된 시드 기준이다. 화면을 붙여보기 전에 어느 계정으로 들어갈지 여기서 고르면 된다.

| 계정 | 내 글 | 받은 신청 | 보낸 신청 | 받은 리뷰 | 쓸 수 있는 리뷰 |
|---|---|---|---|---|---|
| kim (FC 새벽) | 6 | 대기 2, 수락 1 | 대기 1 | 1건 · 4.0 | 0 |
| lee (마포 유나이티드) | 6 | 대기 1, 수락 1 | 대기 1, 수락 1 | 0건 · 평가 없음 | 0 |
| park (송파 FC) | 5 | 없음 | 대기 2, 수락 5 | 3건 · 4.3 | **3** |
| choi (고양 킥커스) | 6 | 대기 3, 수락 1 | 대기 1 | 0건 · 평가 없음 | 0 |
| jung (성남 레인저스) | 6 | 수락 2 | 대기 1 | 0건 · 평가 없음 | **1** |
| yoon (인천 스트라이커즈) | 5 | 대기 1, 수락 1 | 대기 1 | 0건 · 평가 없음 | 0 |

"쓸 수 있는 리뷰"는 FE가 리뷰 버튼을 띄우는 조건, 즉 `status === 'ACCEPTED' && matchAt < now
&& !myReviewWritten`을 만족하는 건수다.

화면별로 어느 계정이 좋은지:

- **매칭 수락** — `choi`. 글 하나에 대기 신청 3건이 몰려 있어, 하나를 수락하면 나머지가 자동
  거절되고 글이 매칭완료로 바뀌는 흐름이 한 화면에서 보인다
- **입금 안내를 받는 쪽** — `lee`(입금 전), `park`(입금 확인됨). 수락된 신청이라 상대 연락처와
  계좌가 열려 있다
- **입금 확인을 누르는 쪽** — `jung`. 받은 신청이 수락 상태이고 아직 입금 확인 전이다
- **팀 평점** — `park`. 받은 리뷰 3건 평균 4.3이 팀 상세에 뜬다. 나머지 팀은 0건이라
  `averageRating: null` 케이스도 같이 보인다
- **리뷰 작성** — `park`(3건) 또는 `jung`(1건). 특히 "지난 주 성남 풋살" 매칭은 **양 팀 다
  아직 안 쓴 상태**라, `jung`과 `park`으로 번갈아 들어가면 같은 매칭에 두 팀이 각각 한 번씩
  쓰는 규칙을 빈 상태에서 확인할 수 있다
- **팀 없는 상태** — 시드에 없다. 앱에서 새로 회원가입하면 그 상태다
- **차단** — `kim`. 고양 킥커스를 차단해 둔 상태라, 모집글 목록·팀 찾기에서 그 팀이 빠져
  있고 팀 페이지를 열면 `blockedByMe: true`가 온다. 해제하면 바로 돌아온다
- **가려진 리뷰** — `kim`. FC 새벽이 받은 리뷰 중 하나가 임시 가림 상태라, 목록에
  별점·내용 없이 자리 표시만 남고 팀 평점 집계에서도 빠져 있다
- **신고함(운영자)** — `choi`. 처리 전 신고 2건이 들어 있다 — 마포 유나이티드의 모집글
  하나(`lee`), 인천 스트라이커즈 팀 하나(`yoon`). 운영자 세션을 먼저 열어야 한다
  (아래 「운영자 화면」)
- **이용 제한 안내** — 시드에 없다. 운영자로 위 신고 하나를 `SUSPEND_ONLY`로 처리하면
  그 계정(`lee` 또는 `yoon`)이 바로 그 상태가 된다. **신고 대상을 운영자가 아닌 팀으로
  둔 것은 의도다** — 운영자 계정은 이용 제한 대상이 될 수 없고(400), 그렇지 않았다면
  이 안내를 따라가다 운영자 API 가 통째로 잠긴다

리뷰는 끝난 경기의 수락된 매칭에만 달 수 있어서(계약서 §7), 시드에 지난 경기 매칭 4건을
따로 넣어 뒀다. 지난 경기라 모집글 목록에는 안 잡히고 매칭관리·팀 상세에서만 보인다.

신고·가림·차단도 하나씩 들어 있다(v1.35.0) — 처리 전 신고 2건, 임시 가림된 리뷰 1건,
차단 1쌍. 어느 계정으로 무엇이 보이는지는 위 목록에 적었다.

> **시드를 바꾸면 이 표도 같이 고칠 것.** 표가 실제와 어긋나면 FE가 "미작성"인 줄 알고 검증한
> 매칭에 이미 리뷰가 들어 있는 식의 오탐이 난다. 실제로 그렇게 한 번 헛돌았다.

## 운영자 화면 (v1.35.1)

운영자 계정은 **`choi@example.com`**(고양 킥커스)이다 — dev 기본값이고
`SUPPORT_OPERATOR_EMAIL`로 바꿀 수 있다. 이 계정으로 로그인하면 문의함과 신고함이 열린다.

로그인만으로는 부족하다. 운영자 API(문의함 `/api/support/rooms*`, 신고함 `/api/admin/**`)는
**일회용 비밀번호로 연 세션**을 함께 요구한다(계약서 §7-2 — 안전성 확보조치 기준 제6조
제2항). 세션이 없으면 403이 아니라 **401 `OPERATOR_SESSION_REQUIRED`**가 오고, FE는 그걸
보고 OTP 입력 화면을 띄운다.

로컬에서 6자리를 만드는 법 — dev 기본 비밀키(`KICKOFFDEVSECRET`)로 지금 코드를 계산한다:

```bash
node -e "const c=require('crypto');const s='KICKOFFDEVSECRET';const a='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';let b='';for(const ch of s)b+=a.indexOf(ch).toString(2).padStart(5,'0');const k=Buffer.from(b.match(/.{8}/g).map(x=>parseInt(x,2)));const t=Buffer.alloc(8);t.writeBigInt64BE(BigInt(Math.floor(Date.now()/30000)));const h=c.createHmac('sha1',k).update(t).digest();const o=h[19]&15;console.log(String((h.readUInt32BE(o)&0x7fffffff)%1000000).padStart(6,'0'))"
```

그 값으로 세션을 열고, 받은 토큰을 `X-Operator-Token` 헤더에 넣어 쓴다:

```bash
curl -X POST http://localhost:8080/api/admin/session -H "Authorization: Bearer <accessToken>" -H 'Content-Type: application/json' -d '{"otp":"123456"}'
```

- 토큰은 **발급 후 30분**이고 연장되지 않는다. 서버 메모리에만 있어서 재시작하면 사라진다
- 같은 코드는 그 30초 안에 다시 받지 않는다. 연속 5번 틀리면 15분 잠긴다
- **`OPERATOR_TOTP_SECRET`이 비어 있으면 운영자 API가 전부 403이다.** 운영에는 기본값이
  없다 — 설정을 빠뜨린 배포가 남의 문의와 신고된 대화를 열어 주지 않게 닫힌 쪽으로 실패한다
- 운영자 API 호출은 전부 접속기록(`operator_audit_logs`)에 남는다. 내용은 적지 않고
  1년 뒤 지워진다. 조회 API는 없다(DB에서 본다)

실제 인증 앱에 등록할 때는 같은 비밀키를 Base32로 넣으면 된다(SHA-1 · 30초 · 6자리 —
Google Authenticator 기본값 그대로다).

## 웹과 앱의 토큰 전달 (v1.32.0)

access token은 둘 다 본문으로 받는다. **갈리는 것은 refresh token 하나다.**

| | 앱 (네이티브) | 웹 |
|---|---|---|
| 자기를 밝히는 법 | 아무것도 안 보낸다 | 요청 헤더 `X-Kickoff-Client: web` |
| refresh token | 응답 본문 (`refreshToken`) | **HttpOnly 쿠키** `kickoff_rt` (본문은 `null`) |
| refresh 부를 때 | 본문에 토큰 | 본문 없이 — 쿠키가 자동으로 간다 |
| 저장 | 기기 보안 저장소 | 어디에도 없다 (스크립트가 읽을 수 없다) |

웹에서 refresh token을 스크립트가 읽을 수 있는 곳(localStorage)에 두면, 페이지에 악성
스크립트가 한 번 심기는 것으로 30일짜리 토큰을 통째로 잃는다. 앱은 기기 보안 저장소를
쓰므로 그 문제가 없고, 그래서 **앱 경로는 한 글자도 바뀌지 않았다** — 헤더를 보내지
않으면 v1.31.4와 똑같이 동작한다.

쿠키는 `Path=/api/auth`로 좁혀 둔다. 일반 API 요청에는 따라붙지 않는다 — 그쪽 인증은
계속 `Authorization` 헤더뿐이고, 쿠키로 인증되는 경로는 `refresh`·`logout` 둘이다.

그 둘은 **`Origin` 검사**를 한 겹 더 탄다(`CORS_ALLOWED_ORIGINS` 기준). 쿠키는 브라우저가
알아서 붙이므로, 없으면 남의 페이지가 사용자를 로그아웃시킬 수 있다. 허용 목록이 비어
있는 개발에서는 판정 기준이 없어 검사하지 않는다.

관련 설정은 `AUTH_COOKIE_SECURE`·`AUTH_COOKIE_SAMESITE`·`AUTH_COOKIE_DOMAIN`이고,
규칙 전체는 계약서 §3 「웹의 토큰 전달」에 있다.

## 소셜 로그인

카카오·네이버 로그인은 **키가 있어야 켜진다.** 키 없이도 서버는 정상적으로 뜨고, 키가 없는
제공자로 로그인을 시도하면 400 `UNSUPPORTED_PROVIDER`가 나간다. 기동 로그의
`활성 소셜 제공자: ...` 줄로 지금 어떤 제공자가 켜져 있는지 확인할 수 있다.

키는 저장소에 커밋하지 않는다. `application.yaml`에는 바인딩만 있고 값은 없다.

| 값 | 설명 |
|---|---|
| `KAKAO_CLIENT_ID` | 카카오 **REST API 키** (JavaScript 키가 아니다) |
| `KAKAO_CLIENT_SECRET` | 카카오 Client Secret. 콘솔에서 "사용함"이면 **필수** — 없으면 토큰 교환이 `KOE010`으로 막힌다 |
| `NAVER_CLIENT_ID` | 네이버 애플리케이션 Client ID |
| `NAVER_CLIENT_SECRET` | 네이버 Client Secret |

### 로컬 개발: `local.yaml`

저장소 루트의 **`local.yaml`**에 넣어두면 `bootRun`이 매번 자동으로 읽는다. 이 파일은
`.gitignore`에 걸려 있어 커밋되지 않는다.

템플릿이 저장소에 들어 있으니 복사해서 값만 채우면 된다.

```bash
cp local.yaml.example local.yaml
```

**여기 템플릿을 다시 적지 말 것.** 예전에 이 자리에 인라인으로 적어 두었다가
`place.kakao.rest-key`가 빠진 채로 굳었다. 키가 늘면 `local.yaml.example` 한 곳만
고치면 되게 두 벌을 두지 않는다.

**빈 값으로 두어도 된다** — 빈 값은 그 기능만 비활성이고 서버는 정상적으로 뜬다.
다만 **아무 문자열이나 넣어두지는 말 것.** 활성 여부를 "비어 있지 않은가"로만
판정하므로(`OAuthProperties.isConfigured`), 엉뚱한 값이 들어 있으면 깨끗한
`UNSUPPORTED_PROVIDER` 대신 카카오 `KOE101` 같은 오류로 실패한다.

`application.yaml`이 `spring.config.import: "optional:file:./local.yaml"`로 읽는다.
**optional이라 파일이 없어도 서버는 정상적으로 뜬다** — 소셜 제공자만 비활성이 될 뿐이다.
CI와 운영에는 이 파일이 없는 게 정상이다.

> **주의: `local.yaml`이 환경변수를 이긴다.** `spring.config.import`로 들여온 값이
> `application.yaml`의 `${KAKAO_CLIENT_ID:}` 자리보다 나중에 얹히기 때문이다. 실제로
> `KAKAO_CLIENT_ID=`를 빈 값으로 주고 띄워도 `local.yaml`의 키로 카카오가 활성화되는 것을
> 확인했다. 그래서 **운영 서버에는 이 파일을 절대 올리지 말 것** — 올리면 환경변수로
> 넣은 운영 키가 무시된다.

### 운영: 환경변수

파일 없이 환경변수로 넣는다. 이름은 위 표와 같다.

```bash
KAKAO_CLIENT_ID=... KAKAO_CLIENT_SECRET=... NAVER_CLIENT_ID=... NAVER_CLIENT_SECRET=... ./gradlew bootRun
```

### 제공자 콘솔에 등록할 Redirect URI

개발 중에는 PC에서도 폰에서도 붙으므로 **두 개를 다 등록**한다. `<LAN-IP>`는 PC의 Wi-Fi
주소이고, 공유기를 바꾸면 달라지므로 그때마다 다시 등록해야 한다.

```
http://localhost:8080/api/auth/oauth/kakao/callback
http://<LAN-IP>:8080/api/auth/oauth/kakao/callback
```

네이버도 `kakao` 자리를 `naver`로 바꿔 같은 두 개를 등록한다.

**카카오는 Redirect URI만으로 부족하다.** 플랫폼 > Web > 사이트 도메인에 아래 두 개를 먼저
등록해야 한다. 이걸 빠뜨리는 바람에 실제로 한 번 막혔다.

```
http://localhost:8080
http://<LAN-IP>:8080
```

콜백 주소는 authorize 요청이 들어온 주소에서 그대로 만들어 쓴다. 그래서 localhost로 열면
localhost 콜백이, LAN IP로 열면 LAN IP 콜백이 제공자에게 전달된다. **PC 브라우저와 폰이
서로 다른 주소를 쓰게 되므로 둘 다 등록해야 하고, 한쪽만 등록하면 그쪽에서만 로그인된다.**
고정하고 싶으면 `OAUTH_CALLBACK_BASE_URL`(예: `http://192.168.0.10:8080`)로 덮어쓸 수 있다.

### 운영 배포 시 콘솔에 등록할 값

**로컬용 등록만으로는 운영에서 로그인이 안 된다.** 이 절이 없어서 실제로 한 번 겪었다 —
2026-08-31 실기기에서 카카오 로그인이 "앱 관리자 설정 오류"로 막혔고, 원인은 운영 콜백이
콘솔에 등록된 적이 없다는 것이었다. 네이버는 등록돼 있어 정상 동작해서, 두 제공자의
차이가 곧 원인을 가리켰다.

운영 서버가 제공자에게 실제로 넘기는 값은 아래처럼 직접 확인할 수 있다. 302 를 돌려주는
읽기 전용 요청이라 아무것도 만들지 않는다 — **콘솔에 무엇을 넣을지 추측하지 말고 이걸 볼 것.**

```bash
curl -s -o /dev/null -w '%{redirect_url}' 'https://<운영도메인>/api/auth/oauth/kakao/authorize?redirect=kickoff%3A%2F%2Fauth'
```

**운영 호스팅은 이전 준비 중이다** — 운영 도메인이 정해지지 않았으므로 아래는 모양만
적는다. `<운영도메인>` 자리에 실제 주소를 넣어 등록한다.

| 제공자 | 콘솔 위치 | 등록할 값 |
|---|---|---|
| 카카오 | 제품 설정 > 카카오 로그인 > Redirect URI | `https://<운영도메인>/api/auth/oauth/kakao/callback` |
| 카카오 | 앱 설정 > 플랫폼 > Web > 사이트 도메인 | `https://<운영도메인>` |
| 네이버 | 애플리케이션 > API 설정 > Callback URL | `https://<운영도메인>/api/auth/oauth/naver/callback` |

**도메인이 바뀌면 세 줄을 다 다시 등록해야 한다.** PaaS 는 프로젝트를 다시 만들면
서브도메인이 바뀌므로, 그때 로그인만 조용히 죽는다 — 다른 API 는 전부 멀쩡해서 눈치채기
어렵다.

카카오는 **Redirect URI 와 Web 사이트 도메인이 별개**다. 하나만 넣으면 막힌다.

콜백 오리진은 기본적으로 authorize 요청이 들어온 주소에서 만든다. Railway 같은 프록시
뒤에서도 `https` 로 제대로 나가는데(`server.forward-headers-strategy: framework`),
도메인을 못박고 싶으면 `OAUTH_CALLBACK_BASE_URL` 로 덮으면 된다.

> ⚠️ **자동 테스트는 여기까지 검증하지 못한다.** OAuth 테스트는 전부 스텁을 쓰고
> `kauth.kakao.com` 을 호출하는 테스트는 하나도 없다. 검증되는 것은 우리 쪽 흐름(state
> 발급·검증, 콜백 처리, 복귀 URL 화이트리스트)뿐이고 **콘솔 등록 상태는 검증 대상이
> 아니다.** 그래서 이 절이 곧 검증이다 — 배포하고 실기기로 한 번 눌러 보는 것까지가
> 소셜 로그인의 완료 조건이다.

### 로그인이 안 될 때

**카카오 로그인 화면이 떴다고 해서 등록이 맞다는 뜻이 아니다.** 카카오는 redirect_uri를
로그인 *이후에* 검증한다. 등록되지 않은 주소는 물론 아무 상관 없는 도메인을 넣어도 로그인
화면까지는 그대로 넘어가고, 로그인을 마친 뒤에야 `KOE006`이 뜬다. 이 착각으로 한 번
엉뚱한 데를 뒤졌다.

어느 단계에서 끊겼는지는 서버 로그로 가른다. authorize와 callback 진입에 한 줄씩 남는다.

| 로그 | 의미 |
|---|---|
| 진입만 있고 콜백 없음 | 제공자 화면에서 끊겼다 — 콘솔 등록값 문제(KOE006 등) |
| 콜백에 `error=...` | 사용자가 동의를 거부했거나 제공자가 거부했다 |
| 콜백 뒤 `토큰 교환 실패` WARN | 키·시크릿 문제. 제공자가 준 사유가 같은 줄에 찍힌다 |
| 콜백만 있고 WARN 없음 | 성공 |

진입 로그의 `origin=`이 콜백 주소를 만든 재료다. 이 값과 콘솔 등록값을 대조하면 된다.

로그인 완료 후 앱으로 돌아갈 주소는 **허용 목록**에 있는 것만 쓴다(open redirect 방지).
개발 기본값은 `exp://*`, `kickoff://*`, `http://localhost:*`이고 `application.yaml`의
`oauth.allowed-redirects`에 있다. 운영에 올리기 전에 앱 스킴만 남기고 좁힐 것.

## H2 콘솔

서버가 떠 있는 상태에서 <http://localhost:8080/h2-console>

| 항목 | 값 |
|---|---|
| JDBC URL | `jdbc:h2:mem:kickoff;MODE=PostgreSQL;DB_CLOSE_DELAY=-1` |
| User Name | `sa` |
| Password | (비움) |

JDBC URL은 기본값이 아니라 위 값으로 바꿔 넣어야 붙는다.

**dev 프로파일에서만 열린다.** `spring.h2.console.enabled`가 dev 블록에만 `true`이고,
거기에 더해 시큐리티가 `/h2-console/**`를 dev에서만 `permitAll`로 두고 그 밖에서는
`denyAll`로 막는다([SecurityConfig](src/main/java/com/kickoff/be/config/SecurityConfig.java)).
두 겹인 이유는 **H2와 h2console 모듈이 운영 이미지에도 그대로 들어가기** 때문이다 —
막고 있는 것이 프로퍼티 한 줄뿐이면, 그 줄이 실수로 켜지는 날 DB 콘솔이 공개된다.

## 테스트

```bash
./gradlew test
```

매칭 수락 흐름, 계좌 비공개, 권한, 지난 경기 규칙, 인증, 리뷰·평점, 소셜 로그인,
푸시 알림에 더해 전화번호 인증·비밀번호 재설정·회원 탈퇴·고객센터·장소 검색·공휴일·
약관 문서, 동의 기록·동의 게이트(§3), 신고·차단·운영 조치(§7-2 — 가려진 콘텐츠, 자동 임시 가림,
운영자 2단계 인증과 접속기록, 이용 제한, 금칙어), 그리고 채팅 서버와의 내부 규약(§10 —
내부 API·`chat_outbox` 전달)을 다룬다.
**매칭 채팅 자체의 테스트는 여기 없다** — 채팅 서버 저장소로 갔다. 소셜 로그인·푸시·SMS·메일·장소 검색은 외부 호출을
스텁으로 갈아끼워 **실제 키 없이** 돈다. 테스트는 별도 `test` 프로파일과 별도 DB를 쓰므로 실행 중인 개발
서버에 영향을 주지 않는다.

### 실제 PostgreSQL로 한 번 더 (`postgresTest`)

```bash
./gradlew postgresTest   # Docker 필요
```

**같은 테스트를 PostgreSQL 컨테이너에서 다시 돌린다.** 별도 테스트를 쓰지 않고 같은 클래스를
재실행하는 게 핵심이다 — PG 전용 테스트를 따로 두면 시간이 지나며 커버리지가 벌어지고,
정작 새로 추가한 쿼리는 H2 에서만 검증되는 상태가 된다.

기본 `test` 는 H2 그대로다. Docker 가 없어도 개발이 막히지 않아야 하고, 빠른 피드백도
지켜야 한다. 대신 **JPQL 에 함수를 쓰는 쿼리를 추가했거나 배포 전이라면 `postgresTest` 를
한 번 돌린다.**

왜 필요한지는 실제로 겪었다. `lower(concat('%', :region, '%'))` 에서 `region` 이 `null` 이면
PostgreSQL 은 타입을 `bytea` 로 추론해 `function lower(bytea) does not exist` 로 죽는데,
H2 는 그냥 넘어가서 **테스트 전체가 통과하는데도 운영에서는 목록 API 가 500** 이었다.

Flyway V1·V2 도 이 실행에서 실제 PostgreSQL 에 적용되므로, 마이그레이션이 PG 에서 도는지도
함께 검증된다.

> **Testcontainers 는 2.x다.** Boot 4 가 관리하는 버전이 `2.0.5`라, 인터넷에 흔한 1.x 예제를
> 그대로 옮기면 의존성 해석부터 실패한다. 바뀐 것 세 가지:
> - 모듈 이름에 접두가 붙는다 — `org.testcontainers:postgresql` 이 아니라
>   **`org.testcontainers:testcontainers-postgresql`** (junit 모듈도 `testcontainers-junit-jupiter`)
> - 클래스가 옮겨졌다 — `org.testcontainers.containers.PostgreSQLContainer` 는 deprecated 이고
>   **`org.testcontainers.postgresql.PostgreSQLContainer`** 를 쓴다
> - **더 이상 제네릭이 아니다** — `new PostgreSQLContainer<>(...)` 가 아니라 `new PostgreSQLContainer(...)`

## 스키마 마이그레이션

스키마는 **Flyway**가 만든다. `src/main/resources/db/migration/`의 SQL이 유일한 경로이고,
Hibernate는 `ddl-auto: validate`로 대조만 한다 — 개발·테스트·운영이 모두 그렇다.

엔티티를 바꾸면 **마이그레이션도 같이 추가**해야 한다(`V2__...sql`). 안 그러면 기동이
`Schema validation` 오류로 실패한다. 통합 테스트도 같은 마이그레이션 위에서 돌기 때문에,
빠뜨리면 배포가 아니라 테스트에서 먼저 걸린다.

마이그레이션 SQL을 손으로 지어내지 말 것. Hibernate가 기대하는 DDL을 뽑아 쓰면 어긋나지 않는다.

```bash
./gradlew bootRun --args='--server.port=8098 --spring.jpa.hibernate.ddl-auto=none \
  --spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect \
  --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create \
  --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-source=metadata \
  --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=build/schema-pg.sql'
```

## 운영 배포

### 현재 — 운영 호스팅 이전 준비 중 (AWS 서울 예정)

> **운영 서버가 없다.** 당분간 전부 로컬에서 돌린다(`./gradlew bootRun`, dev 프로파일 + H2).
> 다시 올릴 때는 **[재배포 체크리스트](#재배포-체크리스트)** 를 따를 것 — 환경변수·외부
> 콘솔·키 재발급·프로브 순서가 거기 모여 있다. 이전 설계는
> [`docs/aws-migration-plan.md`](docs/aws-migration-plan.md) 에 있다.

**옛 운영 주소가 카카오·네이버 콘솔과 FE 소스에 등록·하드코딩돼 있다.** 새 도메인이
정해지면 그 자리들이 전부 작업 대상이 된다(체크리스트 ② 참고) — 주소 자체는 비공개 문서에
적어 두었다.

`Dockerfile`이 있다. 멀티스테이지로 `bootJar`까지 만들고 JRE 이미지에 얹는다.
`SPRING_PROFILES_ACTIVE=prod`가 이미지에 박혀 있다 — 프로파일을 안 줘서 dev(H2)로 뜨는
사고를 막기 위해서다. 실행 사용자는 **비root(uid 10001)**이고 베이스 이미지는
`eclipse-temurin:17.0.21_9-*-noble`로 패치 수준까지 고정돼 있다 — 채팅 서버와 같은
이미지·uid다(두 컨테이너가 같은 호스트 로그 폴더를 쓴다).

### 앞단 프록시와 접속 IP

**`X-Forwarded-For`의 첫 값을 접속 IP로 쓴다**(문자 발송 레이트리밋, 운영자 접속기록).
이게 안전한 것은 **앞단 프록시가 그 헤더를 하나로 덮어쓸 때뿐이다** — 계획된 구성
(Cloudflare → Caddy → 컨테이너)에서는 Caddy가 그렇게 한다.

> **다른 프록시 뒤에 두거나 프록시 없이 직접 노출하면 이 값은 위조할 수 있다.** 그때는
> 클라이언트가 보낸 `X-Forwarded-For`가 그대로 믿기므로, 레이트리밋을 IP마다 우회할 수
> 있고 접속기록의 IP도 의미를 잃는다. 구성이 바뀌면 `ClientIp`를 함께 봐야 한다.

파일 로그는 `LOG_FILE`을 줄 때만 쓴다(기본은 콘솔만). 보관은 기본 90일·파일 50MB·
전체 3GB이고, 환경변수 이름은 채팅 서버와 같다(`LOG_MAX_HISTORY`·`LOG_MAX_FILE_SIZE`·
`LOG_TOTAL_SIZE_CAP`).

```bash
docker build -t kickoff-be .
docker run -p 8080:8080 -e DB_URL=... -e DB_USERNAME=... -e DB_PASSWORD=... -e JWT_SECRET=... kickoff-be
```

| 변수 | 필수 | 설명 |
|---|---|---|
| `DB_URL` | O | PostgreSQL JDBC URL (`jdbc:postgresql://host:5432/db`) |
| `DB_USERNAME` | O | DB 사용자 |
| `DB_PASSWORD` | O | DB 비밀번호 |
| `JWT_SECRET` | O | JWT 서명 키. HS256이라 **32바이트 이상**. 안 주면 개발용 기본값이 그대로 쓰인다 |
| `ACCOUNT_ENC_KEY` | **O** | 모집글 입금 계좌 암호화 키. 32바이트 난수를 base64로. **없거나 길이가 틀리면 서버가 뜨지 않는다.** 한 번 저장한 뒤 키를 잃으면 그 계좌들을 영구히 읽을 수 없다 |
| `PORT` | | 없으면 8080. PaaS가 주입한다 |
| `CORS_ALLOWED_ORIGINS` | **웹은 O** | 웹 도메인을 콤마로. **비우면 웹 로그인이 새로고침에서 끊긴다** — 쿠키를 허용할 수 없다(§3, v1.32.0). 앱은 영향 없다 |
| `AUTH_COOKIE_SECURE` | | 운영 기본 `true`. **끄지 말 것** — 평문 구간이 한 번이라도 있으면 30일짜리 refresh token이 네트워크에 흐른다 |
| `AUTH_COOKIE_SAMESITE` | | 기본 `Strict`. 웹과 API를 같은 사이트에 두면 바꿀 일이 없다 |
| `AUTH_COOKIE_DOMAIN` | | 비우면 요청 호스트에만 붙는다(기본). 서브도메인 공유가 필요할 때만 |
| `OAUTH_ALLOWED_REDIRECTS` | △ | 로그인 후 복귀 허용 주소. 기본 `kickoff://*` — **아래 경고 참고** |
| `OAUTH_CALLBACK_BASE_URL` | △ | 콜백 오리진 고정 (`https://<도메인>`). 비우면 요청 오리진에서 만든다 |
| `KAKAO_CLIENT_ID` 등 | | 소셜 로그인 키 4종. 없으면 해당 제공자만 비활성 |
| `KAKAO_MAP_REST_KEY` | △ | 장소 검색용 카카오 REST 키. **FE의 지도 JS SDK 키와 같은 카카오 앱의 키여야 한다**(계약서 §5-1, v1.37.0) — 저장하는 장소 ID가 같은 앱 안에서 쓰여야 기기가 그 장소를 찾을 수 있다. `KAKAO_CLIENT_ID`와 같은 앱이어도 되지만, 정책 문제로 앱이 멈춰도 로그인이 같이 멈추지 않게 **지도용 앱을 로그인용과 나누기를 권한다**. 그 앱에 카카오맵/로컬 API가 켜져 있어야 한다 — 안 켜져 있으면 검색만 502가 되고 나머지는 멀쩡해서 눈치채기 어렵다. 없으면 `/api/places/search`만 502로 나가고 나머지는 정상 |
| `SEED_DATA` | | `true`면 시드 투입. **기본 off. 운영(prod)에서는 절대 켜지 않는다** — 시드 계정의 이메일·비밀번호가 이 공개 저장소에 적혀 있다. 켠 채로 prod 로 뜨면 기동이 거부된다(`SeedDataGuard`) |
| `LEGAL_EFFECTIVE_AT` | △ | 현행 약관·방침의 시행일. 기본 `2026-09-08T00:00:00+09:00`. **재동의가 필요한 개정**(제3자 제공의 상대·목적·항목·기간 변경, 받는 항목 추가)일 때만 올린다 — 일반 개정은 공지로 충분하다(계약서 §3). 아직 오지 않은 날짜를 넣어도 되고, 그날부터 그 전에 동의한 계정이 동의 화면을 지난다 |
| `MATCH_CONTACT_VISIBLE_DAYS` | | 경기 뒤 연락처·계좌가 상대 팀에게 보이는 기간. 기본 14. **바꾸면 FE 안내 문구와 방침의 보유 기간도 함께 고쳐야 한다**(계약서 §5) |
| `OPERATOR_TOTP_SECRET` | **운영자 쓰면 O** | 운영자 일회용 비밀번호의 Base32 비밀키 (계약서 §7-2). **비밀값이다** — 이 값 하나로 운영자 화면 전체가 열린다. **비우면 운영자 API가 전부 403** (닫힌 쪽으로 실패). dev 기본값은 `KICKOFFDEVSECRET` |
| `PUSH_ENABLED` | | `true`면 푸시 발송. **기본 off** — 실기기에 실수로 알림이 나가지 않게 |

> **`OAUTH_ALLOWED_REDIRECTS`에 `exp://*`를 빠뜨리면 증상이 보이지 않는다.**
> 허용 목록 밖 주소는 302가 아니라 JSON 400으로 끊긴다(계약서 §3-1, v1.3.1). 그런데 이
> 엔드포인트는 인앱 브라우저가 여는 자리라 **JSON이 앱까지 가지 않는다.** 사용자에게는
> "버튼을 눌러도 아무 일이 없다"로 보이고 FE 로그에도 안 남는다. Expo Go로 접속하는 동안은
> `OAUTH_ALLOWED_REDIRECTS=kickoff://*,exp://*`처럼 명시적으로 넣을 것.
> 기동 로그의 `허용된 복귀 주소: [...]` 한 줄로 눈으로 확인할 수 있다.

배포 후 로그에서 확인할 네 줄:

```
Successfully applied N migration(s)      ← Flyway 적용
허용된 복귀 주소: [kickoff://*, exp://*]   ← 복귀 주소 설정
활성 소셜 제공자: [KAKAO, NAVER]           ← 소셜 키 주입
장소 검색 키 설정됨                        ← 지도 검색 키 주입
```

키가 없으면 마지막 줄이 `장소 검색 키 없음 — 검색이 502 로 나간다`로 뜬다. 기동은
정상이고 검색만 죽으므로, 로그를 안 보면 배포가 성공한 것처럼 보인다.

주의할 점 두 가지.

- **`local.yaml`을 배포 환경에 올리지 말 것.** 환경변수를 이긴다(위 소셜 로그인 절 참고).
  `.dockerignore` 첫 줄에서 빼고 있다
- CORS는 **기본값이 모든 오리진 허용이다**([CorsConfig](src/main/java/com/kickoff/be/config/CorsConfig.java)).
  좁히지 못한 이유는 웹 미리보기를 운영 서버에 붙여 확인하는 개발 관례 때문이다 —
  지금 좁히면 그 작업이 끊긴다. **웹을 정식 배포하는 시점에 `CORS_ALLOWED_ORIGINS`로
  도메인을 좁힐 것** (콤마 구분, `*` 와일드카드 가능. 빈 값이면 전부 허용이라 현행 유지).

  > **v1.32.0부터 이것이 웹 로그인의 전제가 됐다.** 웹은 refresh token을 쿠키로 받는데
  > (아래 「웹과 앱의 토큰 전달」), 자격증명 허용은 **허용 목록이 있을 때만** 켜진다 —
  > 목록이 비면 스프링은 요청 Origin을 그대로 반향하고, 거기에 쿠키까지 허용하면 아무
  > 사이트나 사용자의 쿠키를 태워 우리 API를 부를 수 있다. 그래서 운영에서 목록이 비어
  > 있으면 쿠키를 주지 않고 기동 로그에 WARN 한 줄을 남긴다. **앱은 영향이 없다**

### PostgreSQL로 한 번은 띄워보고 배포할 것

테스트는 H2에서 돈다. **H2가 통과시키는 SQL을 PostgreSQL이 거부하는 경우가 있다.**
실제로 `lower(concat('%', :region, '%'))`가 그랬다 — `region`이 `null`이면 PostgreSQL은
타입을 `bytea`로 추론해 `function lower(bytea) does not exist`로 죽는데, H2는 그냥 넘어가서
테스트 91개가 전부 통과했다. 목록 API 전체가 500이 되는 문제였다.

**평소에는 위 [`postgresTest`](#실제-postgresql로-한-번-더-postgrestest)로 충분하다** — 같은
테스트를 실제 PostgreSQL 컨테이너에서 한 번 더 돌리므로 쿼리가 행을 상대로 실제 실행된다.
아래는 그것으로도 안 잡히는 것(기동 자체·Flyway·`ddl-auto: validate`)을 운영과 같은
프로파일로 확인하고 싶을 때다.

```bash
docker run -d --name pg-check -e POSTGRES_PASSWORD=pw -e POSTGRES_USER=kickoff \
  -e POSTGRES_DB=kickoff -p 55432:5432 postgres:16-alpine

SPRING_PROFILES_ACTIVE=prod PORT=8097 \
DB_URL=jdbc:postgresql://localhost:55432/kickoff DB_USERNAME=kickoff DB_PASSWORD=pw \
JWT_SECRET=check-only-secret-0123456789-0123456789 ./gradlew bootRun

docker rm -f pg-check   # 확인 끝나면 정리
```

> 여기에 `SEED_DATA=true`를 붙이지 말 것. **`prod` 프로파일에서는 기동이 거부된다**
> (`SeedDataGuard`) — 이 레시피에 예전에는 그 줄이 있었다. 쿼리를 행에 대고 돌려 보려면
> `postgresTest`를 쓴다.

## 재배포 체크리스트

Railway 배포가 2026-11-10 미결제로 중단됐다. **사용자 결정은 재결제 보류 · 실서비스 전
AWS 서울(`ap-northeast-2`) 이전**이다. 어디로 어떻게 갈지는
**[docs/aws-migration-plan.md](docs/aws-migration-plan.md)** 에 있고, 이 절은 **가는 날
빠뜨리지 않을 것**을 모은 목록이다. **코드를 읽어 만들었다** — 값은 하나도 적지 않았고,
적으면 안 된다.

순서가 있다. **0으로 방침을 먼저 고치고** → ①로 서버를 띄우고 → ②로 외부 콘솔·FE를 새
주소에 맞추고 → ③으로 노출된 키를 갈고 → ④로 확인한다. ②를 건너뛰면 **서버는 200인데
로그인만 안 되는** 상태가 되고, 그게 가장 찾기 어려운 실패다.

### ⓞ 선행 조건 — 개인정보처리방침 §5 개정

**서버를 옮기기 전에, 또는 옮기는 것과 동시에 시행해야 한다.** 방침 §5 수탁 표에 호스팅
업체가 이름으로 박혀 있어서(`Railway Corp. (미국)`), 서버만 옮기면 **방침이 거짓인 기간**이
생긴다.

- 수탁 표의 호스팅 행을 **AWS(한국)** 로 교체
- **국외 이전 문구에서 호스팅을 제외** — 서울 리전은 국내다. 남는 국외 이전은
  Anthropic(미국), Expo·Google FCM(미국), Brevo(프랑스)뿐이다

**방침 본문은 supervisor가 고친다. BE는 손대지 않는다.** 고지 방식은 국외 → 국내 이동이라
불리하지 않은 개정으로 보여 방침 자체의 규칙(시행일 고지)이 적용될 여지가 있으나, **법률
판단이므로 변호사 검토 항목에 병기**하고 확정 전에는 7일 고지를 기본으로 잡는다.

리전을 서울이 아닌 곳에 만들면 이 항목이 되살아난다 — **`ap-northeast-2` 고정.**

### ⓞ-0 선행 조건 — `ACCOUNT_ENC_KEY`를 만들고 따로 보관한다

모집글의 입금 계좌(계좌번호·은행명·예금주)는 **암호화해 저장한다.** 그 키가
`ACCOUNT_ENC_KEY`이고, **없거나 32바이트가 아니면 서버가 뜨지 않는다** — 키 없이 뜨게
두면 그 배포가 계좌를 평문으로 쌓거나 저장을 조용히 건너뛰기 때문이다.

만드는 법은 32바이트 난수를 base64로 인코딩하는 것뿐이다.

```bash
openssl rand -base64 32
```

> **키를 잃으면 이미 저장된 계좌를 영구히 읽을 수 없다.** 복구 수단이 없다(복호화 실패는
> 그 글의 입금 안내가 안 보이는 것으로 나타나고, 서버 로그에 `postId`와 함께 ERROR가
> 남는다). 환경변수 외의 곳에 **반드시 따로 보관할 것** — 다른 비밀값과 달리 이 키는
> 재발급으로 되돌릴 수 없다.
>
> 키를 바꿔야 하면 저장 형식에 버전 접두어(`v1:`)가 있으니 옛 키로 읽고 새 키로 다시
> 쓰는 작업이 필요하다. 지금은 그 도구가 없다 — 바꿀 일이 생기면 먼저 만들어야 한다.

### ⓞ-1 선행 조건 — 웹 도메인과 `CORS_ALLOWED_ORIGINS`

**웹을 함께 올린다면 `CORS_ALLOWED_ORIGINS`가 반드시 있어야 한다** (계약서 §3, v1.32.0).
웹은 refresh token을 쿠키로 받는데, 목록이 비어 있으면 쿠키를 허용할 수 없어 **로그인이
새로고침에서 끊긴다.** 기동 로그에 WARN 한 줄이 나가는 것이 유일한 단서다 — 브라우저
콘솔의 CORS 오류는 서버 로그에 아무것도 남기지 않는다. 앱만 올린다면 해당 없다.

**웹과 API를 같은 사이트(같은 도메인 아래)에 둔다.** 예: `kickoff.example`(웹) +
`api.kickoff.example`(API). 서로 다른 사이트면 `SameSite=None`이 필요해져 요청 위조
방어가 한 겹 얇아진다. 그 구성으로 간다면 `AUTH_COOKIE_SAMESITE=None`과
`AUTH_COOKIE_SECURE=true`를 **함께** 줘야 한다 — Secure 없이 None이면 브라우저가 쿠키를
아예 거부한다(기동 때 WARN).

### ⓞ-2 선행 조건 — `SEED_DATA`를 주지 않는다

**운영에 시드를 넣지 않는다.** 시드 계정의 이메일과 공통 비밀번호가 이 저장소(공개)의
「시드 계정」 절에 적혀 있다 — 운영에 들어가는 순간 **비밀번호를 누구나 아는 계정이
운영 DB 에 생긴다.** 환경변수 하나를 잘못 준 것이 곧 계정 탈취가 되는 자리다.

- 배포 환경 변수 목록에 `SEED_DATA`가 **아예 없어야 한다**(기본값이 `false`다)
- 켠 채로 `prod`로 뜨면 **기동이 거부된다** — `SeedDataGuard`가 막는다. 건너뛰지 않고
  세우는 이유는, 건너뛰면 ERROR 한 줄만 남고 잘못된 설정이 그대로 굳기 때문이다.
  롤링 배포라 그동안 옛 인스턴스가 계속 응답하므로 정상 경로에서는 잃는 것이 없다
- 화면에 보여 줄 데이터가 필요하면 시드가 아니라 **실제 계정으로 만든다**

### ① 환경변수 전수

코드가 읽는 것 전부다(`application.yaml`의 `${...}`와 `@Value` 전수 조사). 없을 때의 동작을
같이 적었다 — **없어서 기동이 막히는 것은 네 개뿐이고(DB 셋 + `ACCOUNT_ENC_KEY`), 나머지는
조용히 기능만 죽는다.** `SEED_DATA`는 성격이 다르다 — 없는 것이 정상이고 **잘못 켜면** 막힌다.
그래서 "뜨면 성공"이 아니다.

| 변수 | 필수 | 없으면 |
|---|---|---|
| `DB_URL` | **O** | 기동 실패 (prod 프로파일에 기본값 없음) |
| `DB_USERNAME` | **O** | 기동 실패 |
| `DB_PASSWORD` | **O** | 기동 실패 |
| `JWT_SECRET` | **O** | 기동은 되지만 **개발용 기본값이 그대로 쓰인다** — 저장소에 적혀 있는 값이라 누구나 토큰을 위조할 수 있다. HS256이라 32바이트 이상 |
| `ACCOUNT_ENC_KEY` | **O** | **기동 실패** (운영 기본값 없음). 모집글 입금 계좌를 암호화해 저장하는 키 — 32바이트 난수를 base64로. 닫힌 쪽으로 실패하게 둔 것이다: 키 없이 뜨면 계좌를 평문으로 쌓거나 저장을 조용히 건너뛴다 |
| `PORT` | | 8080. PaaS가 주입한다 |
| `SPRING_PROFILES_ACTIVE` | | `Dockerfile`에 `prod`가 박혀 있다. **직접 띄울 때는 반드시 줄 것** — 없으면 dev로 떠서 H2 인메모리에 붙고, 재시작마다 데이터가 사라지는데 기동은 정상이다 |
| `CORS_ALLOWED_ORIGINS` | **웹은 O** | 전부 허용(`*`)이고 **쿠키는 허용되지 않는다** — 그 상태로는 웹 로그인이 새로고침에서 끊긴다(계약서 §3, v1.32.0). 기동 로그에 WARN 한 줄이 나간다. 앱은 영향 없다 |
| `AUTH_COOKIE_SECURE` | | `true`(운영 기본). 끄면 refresh 쿠키가 평문으로 오간다 |
| `AUTH_COOKIE_SAMESITE` | | `Strict`. `None`으로 바꾸면서 `AUTH_COOKIE_SECURE`를 끄면 **브라우저가 쿠키를 거부한다**(기동 WARN) |
| `AUTH_COOKIE_DOMAIN` | | 없음. 요청 호스트에만 붙는다 |
| `OAUTH_ALLOWED_REDIRECTS` | △ | `kickoff://*`만. **Expo Go로 붙는 동안 `exp://*`를 빠뜨리면 증상이 안 보인다** (위 소셜 로그인 절의 경고 참고) |
| `OAUTH_CALLBACK_BASE_URL` | △ | 요청 오리진에서 콜백을 만든다. 프록시 뒤라면 `forward-headers-strategy: framework`가 처리하지만, 도메인을 고정하고 싶으면 `https://<도메인>`으로 줄 것 |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | | 카카오 로그인만 비활성 (기동 로그 `활성 소셜 제공자`에서 빠진다) |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` | | 네이버 로그인만 비활성 |
| `KAKAO_MAP_REST_KEY` | △ | `/api/places/search`만 502. 나머지는 멀쩡해서 **로그를 안 보면 모른다** |
| `SEOUL_OPENDATA_KEY` | | 서울 공공 구장 동기화를 건너뛴다. V20 수동 시드 5건만 노출 — 의도된 기본 상태다 |
| `KAKAO_ADMIN_KEY` | △ | `/api/holidays`가 모든 해에 `source: NONE`을 내린다 — FE가 자체 계산값으로 폴백하니 달력은 뜨지만 **임시공휴일이 안 칠해진다**. 나머지 API·기동은 정상. 카카오 콘솔 › 앱 키 › **Admin 키**이고 로그인용 `KAKAO_CLIENT_ID`와 **다른 값, 비밀값**이다 |
| `CHAT_INTERNAL_SECRET` | △ | **`/internal/**`이 전부 401이고 채팅 서버로 가는 이벤트 전달도 멈춘다** (계약서 §10). 그러면 채팅 서버는 방 정보를 못 읽어 채팅이 통째로 503이고, 취소·탈퇴를 영영 모른다 — 다만 행은 `chat_outbox`에 쌓이므로 값을 넣고 재기동하면 밀린 것이 따라간다. BE·채팅 서버가 **같은 값**을 가져야 한다. **비밀값이다** — 이 값 하나로 내부 API 전체가 열린다 |
| `CHAT_INTERNAL_URL` | △ | 보낼 곳을 모르므로 위와 같이 전달만 멈춘다(들어오는 `/internal/**`은 영향 없음). 예: `https://<채팅 호스트>`. dev 기본값은 `http://localhost:8090` |
| `SOLAPI_API_KEY` / `SOLAPI_API_SECRET` / `SMS_SENDER` | | 셋 중 하나라도 비면 실제 발송 어댑터가 등록되지 않고 인증번호가 **서버 로그로만** 나간다 |
| `SMS_ENABLED` | | `false`. 켜도 위 세 개가 없으면 502 |
| `PHONE_VERIFICATION_REQUIRED` | | `false`. 가입에 전화 인증 토큰을 강제하지 않는다 (롤아웃 순서는 `application.yaml` 주석 참고) |
| `BREVO_API_KEY` | | 메일 어댑터가 Brevo(HTTP)로 뜬다. **SMTP 설정이 함께 있어도 Brevo가 이긴다** — 기동 로그에 어느 쪽이 골라졌는지 찍힌다 |
| `SMTP_HOST` / `SMTP_USERNAME` / `SMTP_PASSWORD` | | Brevo 키가 없을 때의 경로. **셋이 다 있어야** SMTP 어댑터가 뜬다. 하나라도 비면(그리고 Brevo도 없으면) 어댑터 자체가 등록되지 않고 메일 내용이 로그로만 나간다 |
| `SMTP_PORT` | | 587. 어댑터 등록 조건에는 들어가지 않는다 |
| `EMAIL_ENABLED` | | `false`. 꺼져 있으면 실제로 보내지 않는다 |
| `EMAIL_FROM` | | 발신 주소. 비우면 SMTP 계정을 쓴다. **Brevo는 콘솔에서 인증한 주소만 발신을 허용**하므로, Brevo로 보낼 때는 이 값이 그 주소여야 한다 |
| `ANTHROPIC_API_KEY` | | AI 상담 어댑터 미등록 → FAQ·운영자 연결만 동작 |
| `AI_SUPPORT_ENABLED` | | `false`. **키가 있어도 이 스위치를 켜기 전에는 호출하지 않는다** — 크레딧이 소모되는 자리다 |
| `AI_SUPPORT_MODEL` | | 기본 모델값이 `application.yaml`에 있다 |
| `SUPPORT_OPERATOR_EMAIL` | △ | **문의함 API가 전부 403이 된다.** 운영자가 아무도 없는 상태를 기본값으로 둔 것이다 — 설정을 빠뜨린 배포가 남의 문의를 열어 주지 않게 |
| `OPERATOR_TOTP_SECRET` | △ | **운영자 API(문의함·신고함)가 전부 403이 된다** (계약서 §7-2). 운영자 계정이 설정돼 있어도 세션을 열 수 없다 — 둘 다 있어야 운영자 화면이 열린다. 인증 앱에 등록할 Base32 문자열이고, **비밀값이라 로그·문서에 남기지 않는다** |
| `LEGAL_EFFECTIVE_AT` | △ | `2026-09-08T00:00:00+09:00`(현재 서빙 중인 방침 v2의 시행일). **재동의가 필요한 개정을 하고 이 값을 안 올리면 조용히 틀린다** — 개정된 문서가 나가는데 동의 화면이 아무에게도 뜨지 않고, 그 사실이 화면에 드러나지 않는다 |
| `MATCH_CONTACT_VISIBLE_DAYS` | | 14일. 0 이나 음수를 주면 수락 직후부터 연락처가 안 보인다 — 설정을 빠뜨린 경우(null)만 기본값으로 떨어진다 |
| `SEED_DATA` | | `false`. **운영에서는 켜지 않는다 — 켜면 기동이 거부된다**(`SeedDataGuard`). 시드 계정의 이메일과 공통 비밀번호가 이 공개 저장소에 있어서, 운영에 들어가면 누구나 그 계정으로 로그인할 수 있다 |
| `PUSH_ENABLED` | | `false`. 켜야 실제 푸시가 나간다 |

`local.yaml`은 **배포 환경에 올리지 말 것.** 환경변수를 이긴다. `.dockerignore` 첫 줄에서 빼고 있다.

> **`/internal/**`은 외부에 노출하지 않는다** (계약서 §10). 채팅 서버만 부르는 경로이고,
> FE 는 쓰지 않는다. 공개 도메인·로드밸런서에서 이 접두사를 통째로 막고, 두 서버는 사설
> 통신으로 붙인다. `CHAT_INTERNAL_SECRET` 은 그 **위의 두 번째 방어선**이지 유일한
> 방어선이 아니다 — 공유 비밀 하나는 양쪽 서버에 다 들어 있어 그만큼 샐 자리가 많다.
>
> **BE 는 인스턴스 하나를 전제로 돈다.** `chat_outbox` 를 비우는 스케줄러에 중복 실행
> 방지 잠금이 없어서, 여러 대로 늘리면 같은 이벤트가 두 번 나갈 수 있다(채팅 서버 처리가
> 멱등이라 결과는 같지만 쓸모없는 일이다). 늘릴 때는 행 잠금
> (`select … for update skip locked`)을 먼저 넣을 것. 인메모리 레이트리밋·OAuth state 도
> 같은 전제를 깔고 있다.

### ② 도메인이 바뀌면 함께 바꿀 곳

주소가 바뀌면 BE만 고쳐서는 끝나지 않는다. 아래가 전부 옛 주소를 들고 있다.

계획된 구성은 **한 도메인 아래 서브도메인을 나누는 것**이다 — BE는 `api.`, 채팅은
`chat.`, 웹은 루트. 앞단은 Cloudflare(DNS·프록시) → Caddy(TLS 종료·리버스 프록시)다.
아래 표의 `<새 도메인>`은 **BE 주소(`api.…`)**를 말한다. 실제 주소는 비공개 문서에 있다.

> **`OAUTH_ALLOWED_REDIRECTS`에 웹 주소를 넣을 때 `*` 앞의 `/`를 빠뜨리지 말 것.**
> `https://<웹 도메인>/*`는 안전하지만 `https://<웹 도메인>*`는 **`https://<웹
> 도메인>.evil.example`까지 열어 준다** — 로그인 직후의 토큰이 그쪽으로 넘어간다.
> 루트로 돌아올 일이 있으면 `https://<웹 도메인>`을 한 줄 더 넣는다(끝의 `/*`는 루트에
> 맞지 않는다). 앱 스킴(`kickoff://*`·`exp://*`)은 그대로 둔다.

**외부 콘솔** (여기를 빠뜨리면 서버는 200인데 로그인만 안 된다)

| 콘솔 | 바꿀 것 |
|---|---|
| 카카오 개발자 | Redirect URI `https://<새 도메인>/api/auth/oauth/kakao/callback` |
| 카카오 개발자 | 플랫폼 > Web > **사이트 도메인** `https://<새 도메인>` — Redirect URI만으로는 부족하다. 실제로 이것 때문에 한 번 막혔다 |
| 카카오 개발자 | 카카오맵/JS SDK 허용 도메인 — FE의 `EXPO_PUBLIC_MAP_REFERER`가 가리키는 주소 |
| 네이버 개발자 | 콜백 URL `https://<새 도메인>/api/auth/oauth/naver/callback` |

무엇을 넣을지 추측하지 말고, 서버가 제공자에게 실제로 넘기는 값을 확인할 것 — 위
"운영 배포 시 콘솔에 등록할 값" 절의 `curl ... %{redirect_url}` 한 줄이 그 용도다.

**FE에 하드코딩된 BE 주소** — 고칠 곳은 아래 **세 자리, 한 번**이다. `kickoff_fe`의
`main`에서 고치면 된다.

> 작업 디렉터리가 네 개로 보이지만(`kickoff_fe`, `kickoff_fe_web`, `kickoff_fe_preview`,
> `kickoff_fe_design`) **같은 저장소의 git 워크트리**다 — 각자 다른 브랜치를 체크아웃해
> 둔 것이고 복제본이 아니다. `main`을 고치면 `web/design`·`design/taste`는 머지할 때
> 따라온다. **`kickoff_fe_preview`만 예외다** — 폰용 고정 스냅샷이라 detached HEAD로
> 옛 커밋에 묶여 있어서 머지로 따라오지 않고, FE README의 "스냅샷 갱신" 절차로 다시
> 묶어야 한다.

| 자리 | 무엇 |
|---|---|
| `app/+html.tsx` | `CARD_IMAGE_URL` — 웹 OG 카드 이미지 (`/share-card.png`) |
| `src/lib/kakao.ts` | `CARD_IMAGE_URL` — 카카오톡 공유 카드 이미지 |
| `src/components/post/PlaceMap.tsx` | `EXPO_PUBLIC_MAP_REFERER` 기본값 — 카카오맵 referer |
| `README.md` | 웹 빌드 예시의 `EXPO_PUBLIC_API_URL` |

`EXPO_PUBLIC_API_URL`은 **빌드 인자다** — 웹 번들에 박히므로 BE 주소가 바뀌면 FE 웹을
다시 빌드·배포해야 한다. 앱(Expo Go/실기기)은 런타임에 정해지므로 다시 빌드할 필요가 없다.

약관·방침 주소(`/terms`, `/privacy`)는 하드코딩돼 있지 않다 — FE가 API 베이스에서 만든다
(`src/lib/legal.ts`). 그래서 이쪽은 손댈 것이 없다.

### ③ 키 재발급

재배포 자체와는 별개로 **먼저 해야 하는 일이다.**

> 운영 비밀값의 목록과 발급처는 비공개 문서에 있다. 비밀값은 환경변수로만 주입하고
> 저장소·로그·대화에 남기지 않는다. **배포 전에 전부 새로 발급한다.**

아래는 "어느 값이 비밀이고 어느 값이 아닌가"의 구분이다 — 새로 발급할 때 무엇을 챙기고
무엇을 신경 쓰지 않아도 되는지가 여기서 갈린다.

**비밀값 (새로 발급한다)**

- `JWT_SECRET` — 가장 급하다. 이 값을 아는 사람은 **아무 사용자로든 토큰을 위조**할 수 있다.
  갈면 기존 access·refresh 토큰이 전부 무효가 되어 모든 사용자가 다시 로그인해야 한다
- `KAKAO_CLIENT_SECRET`, `NAVER_CLIENT_SECRET`
- `SOLAPI_API_KEY`, `SOLAPI_API_SECRET` — 문자 발송 비용이 실제로 나가는 자리다
- `BREVO_API_KEY`, `SMTP_PASSWORD`
- `ANTHROPIC_API_KEY`

**비밀값이지만 성질이 다른 것 — 한 번 쓰면 바꿀 수 없다**

- `ACCOUNT_ENC_KEY` — 위의 키들은 새로 발급하면 끝이지만, 이 키는 **저장된 데이터를
  읽는 데 쓰인다.** 갈면 그 전에 저장된 계좌를 못 읽는다(옛 키로 읽고 새 키로 다시 쓰는
  도구가 필요하고, 지금은 없다). 그래서 **처음 만들 때 제대로 만들고 따로 보관하는 것**이
  전부다 — 자세한 것은 위 「ⓞ-0 선행 조건」에 있다

**비밀이 아닌 것 (공개 경로에 그대로 드러난다)**

- `KAKAO_CLIENT_ID`, `NAVER_CLIENT_ID` — 비밀이 아니다. OAuth authorize URL 에 그대로 실린다
- `KAKAO_MAP_REST_KEY` — **`KAKAO_CLIENT_ID`와 같은 값이다**(카카오의 REST 앱 키 하나가
  로그인 client_id 와 Local API 키를 겸한다). 그래서 비밀이 아니고, 갈아도 같은 성질이
  반복된다 — 새 값 역시 로그인 URL 에 드러난다.

  > 다만 이 사실이 [KakaoPlaceSearchClient](src/main/java/com/kickoff/be/place/client/KakaoPlaceSearchClient.java)
  > 의 전제를 무르게 만든다. BE 가 장소 검색을 대행하는 이유로 "키를 앱 번들에 넣지 않기
  > 위해서 — 클라이언트에 넣으면 디컴파일로 새고 남이 우리 쿼터를 쓴다"고 적어 두었는데,
  > 같은 값이 이미 로그인 URL 에 노출되므로 **프록시만으로는 쿼터를 지키지 못한다.**
  > 쿼터를 실제로 지키려면 재발급이 아니라 **지도용 앱을 로그인 앱과 분리**해야 한다
  > (`KAKAO_MAP_REST_KEY` 를 변수로 따로 둔 것이 그때를 위한 준비다). 지금 당장 할 일은
  > 아니고, 검색 호출량이 늘면 그때 처리할 것.
- `DB_PASSWORD` — 새 DB 를 만들면 자동으로 갈린다. 옛 DB 를 그대로 쓸 때만 직접 갈 것
- `SEOUL_OPENDATA_KEY` — 아직 발급받지 않았다
- `CHAT_INTERNAL_SECRET` — 아직 정하지 않았다(§10 서버 간 내부 규약용, 2026-11-15 신설).
  목록의 이 자리에 있지만 **성질은 `JWT_SECRET` 쪽에 가깝다** — 발급받는 키가 아니라
  우리가 정하는 긴 난수 한 줄이고, 그 값 하나로 `/internal/**` 전체가 열린다. 채팅 서버와
  같은 값을 공유해야 하므로 두 곳에 넣게 되는데, **그만큼 새기 쉬운 자리**다.
  `local.yaml`·환경변수에만 두고 FE·로그·저장소에는 절대 넣지 말 것. 새면 양쪽을 동시에
  갈아야 한다(한쪽만 갈면 채팅이 전부 503이 된다)
- `KAKAO_ADMIN_KEY` — 아직 발급받지 않았다(§8-2 공휴일 조회용). 목록의 이 자리에 있지만
  **성질은 위 두 ID 와 정반대다** — 카카오 Admin 키는 비밀값이고
  `KAKAO_CLIENT_ID`/`KAKAO_MAP_REST_KEY` 와 달리 어디에도 드러나지 않는다. 그래서
  **FE·로그·저장소에 넣지 말 것**이고, 한 번이라도 밖으로 새면 그때는 즉시
  재발급 대상이 된다(콘솔에서 재발급 가능)

로컬에서 쓰는 값은 `local.yaml`에 있다(gitignore 돼 있어 커밋되지 않는다). **그 파일의 값을
어디에도 출력하지 말 것** — 로그·메시지로 한 번 새면 저장소에 커밋된 것과 다를 바 없고,
되돌릴 방법은 재발급뿐이다.

### ④ 배포 후 프로브

전용 스크립트는 없다(찾아봤지만 저장소에 없다). 아래 순서로 확인한다 — **앞에서 실패하면
뒤를 볼 필요가 없는 순서**로 놓았다.

```bash
B=https://<새 도메인>

# 1. 떴는가. 인증 불필요 경로 하나로 앱·컨텍스트가 살아 있는지 본다
curl -s "$B/api/auth/signup-policy"                                   # 200 JSON

# 2. CORS 프리플라이트. 여기서 404면 배포가 아직 안 바뀐 것이다
curl -s -o /dev/null -w '%{http_code}\n' -X OPTIONS "$B/api/posts"    # 200

# 3. 인증 없이 되는 조회 — 스키마·시드·목록 쿼리를 한 번에 본다
curl -s "$B/api/posts?size=1"       | head -c 200                     # 200, content 배열
curl -s "$B/api/stadiums?size=50"   | head -c 200                     # 200, 수동 시드 5건
curl -s "$B/api/holidays?year=2026" | head -c 200                     # 200. source 는 KAKAO_ADMIN_KEY 가
                                                                      # 있으면 KAKAO, 없으면 NONE — 어느
                                                                      # 쪽이든 200 이어야 한다(500 금지)

# 4. 정적 문서. 앱 심사에서 요구하는 주소다
curl -s -o /dev/null -w '%{http_code}\n' "$B/terms"                   # 200
curl -s -o /dev/null -w '%{http_code}\n' "$B/privacy"                 # 200
curl -s -o /dev/null -w '%{http_code}\n' "$B/share-card.png"          # 200

# 5. 인증이 필요한 경로가 막혀 있는가 (열려 있으면 그게 사고다)
curl -s -o /dev/null -w '%{http_code}\n' "$B/api/users/me"            # 401

# 6. 내부 API 가 밖에서 안 열려 있는가 (계약서 §10). 비밀 헤더 없이 401 이어야 한다 —
#    200 이 나오면 그 순간 남의 채팅방 정보가 공개돼 있는 것이다. 공개 도메인에서
#    /internal/** 을 아예 차단했다면 404·403 도 정상이다(그쪽이 더 낫다)
curl -s -o /dev/null -w '%{http_code}\n' "$B/internal/chat/users/1/rooms"   # 401 (또는 차단)

# 없는 경로를 "떴는지" 확인용으로 쓰지 말 것. 토큰 없이 부르면 404가 아니라 401이다 —
# anyRequest().authenticated() 가 라우팅보다 먼저 걸리기 때문이다. 404로 기대하고 보면
# "앱이 안 떴다"로 잘못 읽는다.
curl -s -o /dev/null -w '%{http_code}\n' "$B/api/does-not-exist"      # 401 (404 아님)

# 7. H2 콘솔이 막혀 있는가. **토큰을 주고** 불러야 판정이 된다 —
#    403이면 denyAll이 걸려 있고, 404면 규칙이 빠져 "토큰만 있으면 닿는" 상태다.
#    토큰 없이 부르면 둘 다 401이라 구별되지 않는다.
curl -s -o /dev/null -w '%{http_code}\n' \
  -H "Authorization: Bearer <아무 유효 토큰>" "$B/h2-console/login.do"  # 403

# 8. 소셜 로그인이 제공자에게 넘기는 콜백 — 콘솔에 등록한 값과 같아야 한다
curl -s -o /dev/null -w '%{redirect_url}\n' \
  "$B/api/auth/oauth/kakao/authorize?redirect=kickoff%3A%2F%2Fauth"
```

그다음 **기동 로그 네 줄**을 눈으로 볼 것(위 "배포 후 로그에서 확인할 네 줄"). 프로브가
전부 통과해도 `장소 검색 키 없음`이나 `활성 소셜 제공자: []`가 찍혀 있으면 기능이 죽어 있다.

> **배포 뒤에도 계속 지켜볼 ERROR 한 줄** — `탈퇴 대화 삭제(PURGE)가 채팅 서버에 전달되지
> 않았다`. 미전송 `PURGE`가 1시간(`chat.outbox.purge-alert-after`)을 넘기면 행마다 한 번씩
> 찍힌다. **이 로그가 보이면 채팅 서버 상태를 확인하고, 탈퇴한 사용자의 대화가 늦어도
> 5일 안에 지워지도록 조치한다**(개인정보 보호법 제21조 "지체 없이"). 전달은 횟수 제한 없이
> 재시도하므로 채팅 서버만 살아나면 저절로 풀리지만, 문구에 `재시도 포기(dead)`가 함께
> 찍혔다면 자동으로는 끝나지 않는다 — `chat_outbox`의 그 행에 지워야 할 `request_ids`가
> 들어 있으니 채팅 서버에서 직접 지우고 행을 정리할 것. 행은 자동 정리 대상이 아니라 남아
> 있다. **로그에는 사용자·방 정보를 찍지 않는다**(이벤트 번호와 경과 시간뿐) — 지우라는
> 경보가 개인정보를 또 한 벌 만들지 않게.

마지막으로 **12분 규칙**: 배포 직후 404나 502가 나와도 바로 실패로 판정하지 말 것. 빌드가
아직 돌고 있을 수 있다 — 실제로 09:38에 404를 보고 실패로 의심했는데 09:39에 200이었던
일이 있다. 최소 12분은 기다린 뒤에 판정한다.
