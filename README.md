# kickoff_be

조기축구 팀 매칭 앱 "Kickoff"의 REST API 서버. 프론트엔드는 옆 저장소
`kickoff_fe`(Expo / React Native)가 담당한다.

- API 명세: **[docs/api-contract.md](docs/api-contract.md)** — 엔드포인트, DTO, 에러 코드, 열거형이 전부 여기 있다
- 코드 규칙과 설계 배경: [CLAUDE.md](CLAUDE.md)

## 요구사항

Java 17. Gradle은 wrapper가 들어 있어 따로 설치할 필요 없다.

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

시드에는 매칭이 성사된 글과 입금 확인까지 끝난 글이 하나씩 들어 있어서,
매칭·입금 화면을 로그인만 하면 바로 볼 수 있다.

리뷰는 **송파 FC(park@example.com)** 로 로그인하면 가장 잘 보인다. 받은 리뷰 3건(평균 4.3)이
팀 상세에 뜨고, 끝난 경기 중 아직 안 쓴 리뷰가 2건 남아 있어 작성까지 눌러볼 수 있다.
리뷰는 끝난 경기의 수락된 매칭에만 달 수 있어서(계약서 §7), 시드에 지난 경기 매칭 4건을
따로 넣어 뒀다. 그중 하나(성남 레인저스전)는 양 팀 다 아직 안 써서, 같은 매칭에 두 팀이
각각 한 번씩 쓰는 규칙을 빈 상태에서 확인할 수 있다.

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

테스트 65개. 매칭 수락 흐름, 계좌 비공개, 권한, 지난 경기 규칙, 인증, 리뷰·평점을 다룬다.
테스트는 별도 `test` 프로파일과 별도 DB를 쓰므로 실행 중인 개발 서버에 영향을 주지 않는다.

## 운영 배포

`prod` 프로파일로 띄우고 아래 환경변수를 준다.

| 변수 | 설명 |
|---|---|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 |
| `JWT_SECRET` | JWT 서명 키. HS256이라 **32바이트 이상** 필요 |

```bash
./gradlew build   # build/libs/be-0.0.1-SNAPSHOT.jar 생성

SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:postgresql://... DB_USERNAME=... DB_PASSWORD=... JWT_SECRET=... \
java -jar build/libs/be-0.0.1-SNAPSHOT.jar
```

주의할 점 두 가지.

- `JWT_SECRET`을 주지 않으면 `application.yaml`의 개발용 기본값이 그대로 쓰인다. 반드시 넘길 것
- `prod`는 `ddl-auto: validate`라 **스키마가 미리 만들어져 있어야** 기동된다. dev처럼 테이블을 자동 생성하지 않는다

CORS는 개발에서 모든 오리진을 허용하고 있다. 운영에 올리기 전에
[CorsConfig](src/main/java/com/kickoff/be/config/CorsConfig.java)에서 좁힐 것.
