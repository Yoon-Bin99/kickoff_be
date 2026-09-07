# 킥오프 — 새 컴퓨터 인수인계 안내서 (2026-09-02 작성)

이 폴더는 이전 노트북의 supervisor Claude 세션이 환경 이전을 위해 만든 백업이다.
**새 컴퓨터에서 Claude Code를 열고 "kickoff_be/handover/HANDOVER.md 읽고 인수인계 받아"
라고 하면 된다.**

## 이 백업에 들어 있는 것

| 위치 | 내용 |
|---|---|
| `handover/memory/` | supervisor의 메모리 3파일 — 프로젝트 전체 이력·규칙·백로그·체크리스트 |
| `kickoff_be/docs/` | 문서 원본 전부 — api-contract.md(단일 진실 공급원, v1.24.1), 약관, 개인정보처리방침, 고객센터 FAQ·AI 지식, 지인 테스트 안내문, 로고 요청서 |

## 새 컴퓨터 부트스트랩 순서

0. **JDK 17 + Node 22 설치** (가장 먼저 — 둘 다 없으면 아무것도 안 돈다)
   - JDK: `winget install --id EclipseAdoptium.Temurin.17.JDK -e`
   - Node: winget의 Node LTS 채널은 이제 24.x만 준다. **FE가 SDK 54라 Node 24+ 에서는
     @expo/cli 가 undici 오류로 크래시**하므로 nvm-windows 로 22 를 쓴다:
     `winget install --id CoreyButler.NVMforWindows -e` → `nvm install 22.19.0` → `nvm use 22.19.0`
     (nvm 함정: 설치 직후 낡은 셸에서는 `NVM_HOME`이 없어 `ERROR open \settings.txt`로 죽는다)
   - **Git identity**: 새 PC는 `user.name`/`user.email`이 비어 첫 커밋이 `Author identity unknown`으로
     막힌다. 각 저장소에서 `--local`로만 설정한다 (global은 이 PC의 다른 저장소까지 바꾸므로 건드리지 않음):
     `git config --local user.name "Yoon-Bin99"` / `git config --local user.email "shp06135@naver.com"`
   - 자세한 함정은 BE README 「요구사항」 참고.
1. **폴더 구성**: 바탕화면(또는 원하는 곳)에 루트 폴더를 만들고 그 안에 클론:
   ```
   git clone https://github.com/Yoon-Bin99/kickoff_be
   git clone https://github.com/Yoon-Bin99/kickoff_fe
   ```
   폴더 이름은 무관하다(현재 PC 는 `kickoff`). 메모리와 이 문서의 `킥오프/docs`는
   **이 루트 폴더 아래의 `docs`**를 뜻한다.
2. **원본 docs 복원**: `킥오프/docs` 폴더를 만들고 `kickoff_be/docs`의 6개 md/txt 파일을
   복사한다 (원본=킥오프/docs, 사본=각 저장소 docs — 이 구조가 계약서 동기화 규칙의 전제).
3. **Claude 메모리 복원**: 새 supervisor 세션에게 `handover/memory/`의 3파일을 자기
   메모리 디렉토리(`~/.claude/projects/<프로젝트>/memory/`)로 옮겨 적으라고 지시한다.
   kickoff-project.md가 본체다 — 이것만 있으면 이력·규칙·잔여 작업이 전부 복원된다.
4. **세션 3개 구성**: supervisor(킥오프 폴더) + BE 담당(cwd kickoff_be) + FE 담당(cwd
   kickoff_fe). BE/FE 세션에는 각 저장소의 CLAUDE.md가 역할을 알려준다.
5. **BE 로컬 키 파일 재작성**: `kickoff_be/local.yaml`은 gitignore라 **이 백업에 없다.**
   저장소의 `local.yaml.example`을 복사(`cp local.yaml.example local.yaml`)하고 Railway
   대시보드 → Variables의 값을 채우면 된다. **빈 값으로 둬도 서버는 정상적으로 뜬다**(해당
   기능만 비활성). 운영은 Railway에 다 있으므로 로컬 개발 전까지는 급하지 않다.
6. **로컬 서버 기동**(개발 재개 시):
   - BE: `./gradlew.bat bootRun` (8080)
   - FE: 두 개의 metro를 띄운다. 절차·명령 전문은 FE README 참고.
     - **8082** = 원본 `kickoff_fe`, 작업 중 코드 검증용
     - **8081** = `kickoff_fe_preview` worktree (`git worktree add --detach ../kickoff_fe_preview <운영 커밋>`),
       폰이 붙는 고정 스냅샷. **worktree는 `node_modules`를 공유하지 않으므로 그 폴더에서 `npm ci`를
       따로 돌려야 하고, gitignore된 `.env.local`도 따로 만들어야 한다.**
     - 둘 다 `EXPO_PUBLIC_API_URL`은 운영 Railway로 (로컬 BE를 붙이면 카카오 지도 Referer가
       미등록 도메인이 되어 지도가 안 뜬다 — FE 수정 전까지의 제약)
     - `REACT_NATIVE_PACKAGER_HOSTNAME=<LAN IP>` 필수, 첫 기동은 `--clear`
7. **아침 보고서 재등록**: 매일 09:07 스케줄 작업(kickoff-morning-report)은 구 노트북에
   있던 것이라 죽었다. 필요하면 새 세션에서 다시 등록.

## 살아 있는 것 (컴퓨터와 무관)

- **운영 서버**: https://kickoffbe-production-7275.up.railway.app (Railway, PostgreSQL,
  자동 재배포 — main 푸시 주의)
- **환경변수·키 전부**: Railway Variables에 있음 (JWT, 소셜 4종, SMS/솔라피, SMTP,
  BREVO_API_KEY, PHONE_VERIFICATION_REQUIRED=true, SUPPORT_OPERATOR_EMAIL 등)
- **앱 빌드·키스토어**: Expo EAS 계정 yoonbin99 (projectId 19482b5c-a2cd-4e22-a060-02dcf3e5908d)
- **외부 콘솔들**: 카카오/네이버 개발자 콘솔, 솔라피, Brevo, Anthropic — 각 사이트 계정
- **사용자 폰의 dev 빌드 APK** — 새 PC의 metro(8081)에 LAN IP만 맞으면 그대로 붙는다

## 죽는 것 (구 노트북과 함께)

- 로컬 H2 개발 DB (시드라 무손실), 로컬 서버 2개, metro, worktree
- `kickoff_be/local.yaml` (위 5번으로 재작성)
- `C:/Users/User/Downloads/fcm.json` 사본 (EAS에 이미 등록돼 있어 불필요)
- 아침 보고서 스케줄 작업 (위 7번으로 재등록)

## 지금 운영 상태 요약 (2026-09-02 저녁)

- 계약 v1.24.1, 운영 = BE 3094e51 / FE b144c3e. 전 기능 배포·검증 완료.
- 사용자 계정 = shp06135@naver.com (이메일 가입, 010-8513-7975, 운영자 지정됨).
  팀은 탈퇴 때 삭제돼 재생성 대기.
- 잔여 작업·백로그·배포 체크리스트는 `handover/memory/kickoff-project.md` 하단 참고.
  큰 대기 항목: AI 챗봇 가동($5 충전), 지인 테스트 준비(사용자 지시 대기 — preview 재빌드 필요).
