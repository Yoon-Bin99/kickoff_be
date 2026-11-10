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

코드를 고치면 **서버를 직접 재시작해야 한다.** devtools 자동 재시작은 꺼놨다 —
gradle이 클래스를 다시 쓰는 도중에 재시작이 걸리면 컨트롤러가 일부 빠진 채로 뜨는
일이 있어서다(자세한 내용은 커밋 `8dc8749`).

## 시드 계정

비밀번호는 전부 `pass1234`. 각 계정이 팀을 하나씩 소유한다.

| 이메일 | 닉네임 | 팀 | 지역 |
|---|---|---|---|
| kim@example.com | 김주장 | FC 새벽 | 서울 강서구 |
| lee@example.com | 이감독 | 마포 유나이티드 | 서울 마포구 |
| park@example.com | 박캡틴 | 송파 FC | 서울 송파구 |
| choi@example.com | 최총무 | 고양 킥커스 | 경기 고양시 |
| jung@example.com | 정주장 | 성남 레인저스 | 경기 성남시 |
| yoon@example.com | 윤코치 | 인천 스트라이커즈 | 인천 남동구 |

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

리뷰는 끝난 경기의 수락된 매칭에만 달 수 있어서(계약서 §7), 시드에 지난 경기 매칭 4건을
따로 넣어 뒀다. 지난 경기라 모집글 목록에는 안 잡히고 매칭관리·팀 상세에서만 보인다.

> **시드를 바꾸면 이 표도 같이 고칠 것.** 표가 실제와 어긋나면 FE가 "미작성"인 줄 알고 검증한
> 매칭에 이미 리뷰가 들어 있는 식의 오탐이 난다. 실제로 그렇게 한 번 헛돌았다.

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

현재 운영(`https://kickoffbe-production-7275.up.railway.app`) 기준 등록값:

| 제공자 | 콘솔 위치 | 등록할 값 |
|---|---|---|
| 카카오 | 제품 설정 > 카카오 로그인 > Redirect URI | `https://kickoffbe-production-7275.up.railway.app/api/auth/oauth/kakao/callback` |
| 카카오 | 앱 설정 > 플랫폼 > Web > 사이트 도메인 | `https://kickoffbe-production-7275.up.railway.app` |
| 네이버 | 애플리케이션 > API 설정 > Callback URL | `https://kickoffbe-production-7275.up.railway.app/api/auth/oauth/naver/callback` |

**도메인이 바뀌면 세 줄을 다 다시 등록해야 한다.** Railway 는 프로젝트를 다시 만들면
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

## 테스트

```bash
./gradlew test
```

테스트 515개. 매칭 수락 흐름, 계좌 비공개, 권한, 지난 경기 규칙, 인증, 리뷰·평점,
소셜 로그인, 푸시 알림에 더해 전화번호 인증·비밀번호 재설정·회원 탈퇴·채팅·고객센터·
장소 검색·약관 문서를 다룬다. 소셜 로그인·푸시·SMS·메일·장소 검색은 외부 호출을
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

### 현재 배포 — 2026-11-10 중단됨

> **Railway 배포가 미결제로 중단됐다.** 아래 주소는 응답하지 않는다. 당분간 전부 로컬에서
> 돌린다(`./gradlew bootRun`, dev 프로파일 + H2). 다시 올릴 때는 **[재배포 체크리스트](#재배포-체크리스트)**
> 를 따를 것 — 환경변수·외부 콘솔·키 재발급·프로브 순서가 거기 모여 있다.

| | |
|---|---|
| 주소 | https://kickoffbe-production-7275.up.railway.app (중단) |
| 플랫폼 | Railway (origin `main` 푸시 시 자동 재배포) |
| DB | Railway PostgreSQL |

`main`에 푸시하면 재배포가 돈다. 그래서 푸시 전에 `./gradlew test`를 통과시킬 것.

주소를 지우지 않고 남겨 둔다. **카카오·네이버 콘솔과 FE 소스에 이 주소가 등록·하드코딩돼
있어서**, 무엇을 바꿔야 하는지 추적하려면 옛 주소를 알아야 한다(체크리스트 ② 참고).

`Dockerfile`이 있다. 멀티스테이지로 `bootJar`까지 만들고 JRE 이미지에 얹는다.
`SPRING_PROFILES_ACTIVE=prod`가 이미지에 박혀 있다 — 프로파일을 안 줘서 dev(H2)로 뜨는
사고를 막기 위해서다.

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
| `PORT` | | 없으면 8080. Railway 같은 PaaS가 주입한다 |
| `OAUTH_ALLOWED_REDIRECTS` | △ | 로그인 후 복귀 허용 주소. 기본 `kickoff://*` — **아래 경고 참고** |
| `OAUTH_CALLBACK_BASE_URL` | △ | 콜백 오리진 고정 (`https://<도메인>`). 비우면 요청 오리진에서 만든다 |
| `KAKAO_CLIENT_ID` 등 | | 소셜 로그인 키 4종. 없으면 해당 제공자만 비활성 |
| `KAKAO_MAP_REST_KEY` | △ | 장소 검색용 카카오 REST 키. **`KAKAO_CLIENT_ID`와 같은 앱의 키여도 된다** — 현재 운영이 그렇다. 변수를 따로 둔 것은 나중에 앱을 분리할 때 이 값만 바꾸면 되게 하려는 것이다. 다만 **로그인 키를 여기 넣을 때는 그 앱에 카카오맵/로컬 API가 켜져 있어야 한다** — 안 켜져 있으면 검색만 502가 되고 나머지는 멀쩡해서 눈치채기 어렵다. 없으면 `/api/places/search`만 502로 나가고 나머지는 정상 |
| `SEED_DATA` | | `true`면 시드 투입. **기본 off** — 첫 배포 직후 화면 확인용으로만 켠다 |
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
  자격증명 허용(`allowCredentials`)은 운영에서 이미 꺼 두었다. 인증이 `Authorization`
  헤더라 켤 이유가 없고, `*` 오리진과 함께 두면 나중에 세션 쿠키를 하나라도 도입할 때
  아무 사이트나 그 쿠키를 태워 부를 수 있는 상태가 된다

### PostgreSQL로 한 번은 띄워보고 배포할 것

테스트는 H2에서 돈다. **H2가 통과시키는 SQL을 PostgreSQL이 거부하는 경우가 있다.**
실제로 `lower(concat('%', :region, '%'))`가 그랬다 — `region`이 `null`이면 PostgreSQL은
타입을 `bytea`로 추론해 `function lower(bytea) does not exist`로 죽는데, H2는 그냥 넘어가서
테스트 91개가 전부 통과했다. 목록 API 전체가 500이 되는 문제였다.

JPQL에 함수를 쓰는 쿼리를 추가했다면 배포 전에 한 번 확인한다.

```bash
docker run -d --name pg-check -e POSTGRES_PASSWORD=pw -e POSTGRES_USER=kickoff \
  -e POSTGRES_DB=kickoff -p 55432:5432 postgres:16-alpine

SPRING_PROFILES_ACTIVE=prod PORT=8097 SEED_DATA=true \
DB_URL=jdbc:postgresql://localhost:55432/kickoff DB_USERNAME=kickoff DB_PASSWORD=pw \
JWT_SECRET=check-only-secret-0123456789-0123456789 ./gradlew bootRun

docker rm -f pg-check   # 확인 끝나면 정리
```

## 재배포 체크리스트

Railway 배포가 2026-11-10 미결제로 중단됐다. 다시 올릴 때(Railway 재생성이든 AWS 서울이든)
빠뜨리기 쉬운 것을 모아 둔다. **코드를 읽어 만든 목록이다** — 값은 하나도 적지 않았고,
적으면 안 된다.

순서가 있다. ①로 서버를 띄우고 → ②로 외부 콘솔·FE를 새 주소에 맞추고 → ③으로 노출된 키를
갈고 → ④로 확인한다. ②를 건너뛰면 **서버는 200인데 로그인만 안 되는** 상태가 되고, 그게
가장 찾기 어려운 실패다.

### ① 환경변수 전수

코드가 읽는 것 전부다(`application.yaml`의 `${...}`와 `@Value` 전수 조사). 없을 때의 동작을
같이 적었다 — **기동이 막히는 것은 세 개뿐이고, 나머지는 조용히 기능만 죽는다.** 그래서 "뜨면
성공"이 아니다.

| 변수 | 필수 | 없으면 |
|---|---|---|
| `DB_URL` | **O** | 기동 실패 (prod 프로파일에 기본값 없음) |
| `DB_USERNAME` | **O** | 기동 실패 |
| `DB_PASSWORD` | **O** | 기동 실패 |
| `JWT_SECRET` | **O** | 기동은 되지만 **개발용 기본값이 그대로 쓰인다** — 저장소에 적혀 있는 값이라 누구나 토큰을 위조할 수 있다. HS256이라 32바이트 이상 |
| `PORT` | | 8080. PaaS가 주입한다 |
| `SPRING_PROFILES_ACTIVE` | | `Dockerfile`에 `prod`가 박혀 있다. **직접 띄울 때는 반드시 줄 것** — 없으면 dev로 떠서 H2 인메모리에 붙고, 재시작마다 데이터가 사라지는데 기동은 정상이다 |
| `CORS_ALLOWED_ORIGINS` | | 전부 허용(`*`). 웹 정식 배포 시 좁힐 것 |
| `OAUTH_ALLOWED_REDIRECTS` | △ | `kickoff://*`만. **Expo Go로 붙는 동안 `exp://*`를 빠뜨리면 증상이 안 보인다** (위 소셜 로그인 절의 경고 참고) |
| `OAUTH_CALLBACK_BASE_URL` | △ | 요청 오리진에서 콜백을 만든다. 프록시 뒤라면 `forward-headers-strategy: framework`가 처리하지만, 도메인을 고정하고 싶으면 `https://<도메인>`으로 줄 것 |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | | 카카오 로그인만 비활성 (기동 로그 `활성 소셜 제공자`에서 빠진다) |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` | | 네이버 로그인만 비활성 |
| `KAKAO_MAP_REST_KEY` | △ | `/api/places/search`만 502. 나머지는 멀쩡해서 **로그를 안 보면 모른다** |
| `SEOUL_OPENDATA_KEY` | | 서울 공공 구장 동기화를 건너뛴다. V20 수동 시드 5건만 노출 — 의도된 기본 상태다 |
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
| `SEED_DATA` | | `false`. `true`면 시드 투입. **실사용자가 쓰기 시작하면 절대 켜지 말 것** |
| `PUSH_ENABLED` | | `false`. 켜야 실제 푸시가 나간다 |

`local.yaml`은 **배포 환경에 올리지 말 것.** 환경변수를 이긴다. `.dockerignore` 첫 줄에서 빼고 있다.

### ② 도메인이 바뀌면 함께 바꿀 곳

주소가 바뀌면 BE만 고쳐서는 끝나지 않는다. 아래가 전부 옛 주소를 들고 있다.

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

재배포 자체와는 별개로 **먼저 해야 하는 일이다.** 2026-09-07 에 Railway 환경변수 전체가
대화창에 붙여졌다. 그래서 아래 목록은 "노출됐을 수 있는 것"이 아니라 **노출된 것**이다.
값이 남아 있을 수 있는 곳은 대화 기록과 `handover/` 스냅샷이다.

**확정 재발급 대상**

- `JWT_SECRET` — 가장 급하다. 이 값을 아는 사람은 **아무 사용자로든 토큰을 위조**할 수 있다.
  갈면 기존 access·refresh 토큰이 전부 무효가 되어 모든 사용자가 다시 로그인해야 한다
- `KAKAO_CLIENT_SECRET`, `NAVER_CLIENT_SECRET`
- `SOLAPI_API_KEY`, `SOLAPI_API_SECRET` — 문자 발송 비용이 실제로 나가는 자리다
- `BREVO_API_KEY`, `SMTP_PASSWORD`
- `ANTHROPIC_API_KEY`

**재발급 대상이 아닌 것**

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

갈기 전에 `local.yaml`과 대조해 **무엇이 지금 쓰이는 값인지** 확인할 것. 다만 그 파일의
값을 어디에도 출력하지 말 것 — gitignore 돼 있어 커밋되지는 않지만, 로그·메시지로 새면
같은 일이 반복된다. 2026-09-07 이 그렇게 생긴 일이다.

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

# 4. 정적 문서. 앱 심사에서 요구하는 주소다
curl -s -o /dev/null -w '%{http_code}\n' "$B/terms"                   # 200
curl -s -o /dev/null -w '%{http_code}\n' "$B/privacy"                 # 200
curl -s -o /dev/null -w '%{http_code}\n' "$B/share-card.png"          # 200

# 5. 인증이 필요한 경로가 막혀 있는가 (열려 있으면 그게 사고다)
curl -s -o /dev/null -w '%{http_code}\n' "$B/api/users/me"            # 401

# 없는 경로를 "떴는지" 확인용으로 쓰지 말 것. 토큰 없이 부르면 404가 아니라 401이다 —
# anyRequest().authenticated() 가 라우팅보다 먼저 걸리기 때문이다. 404로 기대하고 보면
# "앱이 안 떴다"로 잘못 읽는다.
curl -s -o /dev/null -w '%{http_code}\n' "$B/api/does-not-exist"      # 401 (404 아님)

# 6. 소셜 로그인이 제공자에게 넘기는 콜백 — 콘솔에 등록한 값과 같아야 한다
curl -s -o /dev/null -w '%{redirect_url}\n' \
  "$B/api/auth/oauth/kakao/authorize?redirect=kickoff%3A%2F%2Fauth"
```

그다음 **기동 로그 네 줄**을 눈으로 볼 것(위 "배포 후 로그에서 확인할 네 줄"). 프로브가
전부 통과해도 `장소 검색 키 없음`이나 `활성 소셜 제공자: []`가 찍혀 있으면 기능이 죽어 있다.

마지막으로 **12분 규칙**: 배포 직후 404나 502가 나와도 바로 실패로 판정하지 말 것. 빌드가
아직 돌고 있을 수 있다 — 실제로 09:38에 404를 보고 실패로 의심했는데 09:39에 200이었던
일이 있다. 최소 12분은 기다린 뒤에 판정한다.
