-- 매칭 취소 (계약서 §6-2, v1.20.0). match_requests.status 에 MATCH_CANCELED 를 허용한다.
--
-- V1~V12 는 이미 배포됐으므로 수정하지 않는다. 고치면 Flyway 체크섬이 깨져 기동이 막힌다.
--
-- ⚠️ V13 은 보류 중인 유니크 제약 마이그레이션(docs/pending/)이 가져갔다. 그쪽이 사용자
--    중복 점검 결과만 기다리는 상태라 먼저 나갈 가능성이 커서 낮은 번호를 줬다.
--
--    배포 규칙(supervisor 확정): <b>V13 이 먼저 나가거나, 둘을 같은 푸시로 묶는다.</b>
--    이것만 지키면 "건너뛴 번호가 뒤늦게 나타나 Flyway 가 기동을 막는" 상황이 아예
--    생기지 않는다. 이 파일이 먼저 준비되더라도 V13 게이트가 풀릴 때까지 들고 있을 것.
--    (로컬·테스트는 매번 빈 DB 에서 전부 순서대로 적용하므로 V13 이 없어도 문제없다.)
--
-- ─────────────────────────────────────────────────────────────────────────────
-- 왜 이름을 두 개 지우는가
--
-- V1 이 이 제약을 <b>이름 없이</b> 인라인으로 만들었다:
--   status varchar(20) not null check (status in ('PENDING','ACCEPTED','REJECTED','CANCELED'))
--
-- 이름을 안 주면 DB 가 알아서 붙이는데 <b>규칙이 서로 다르다</b>:
--   PostgreSQL -> match_requests_status_check  (테이블_컬럼_check)
--   H2         -> CONSTRAINT_CE                (생성 순서로 매겨지는 이름)
--
-- 그래서 한 줄로는 못 지운다. 양쪽 이름을 `if exists` 로 각각 지우면, 자기 DB 에 없는
-- 이름은 조용히 넘어가고 있는 것만 지워진다 — 한 파일로 두 DB 를 다 덮는다.
--
-- 이번에는 <b>이름을 명시해서</b> 다시 만든다. 다음에 값을 하나 더 넣을 때는 이 수고가
-- 없다. V1 헤더가 "값을 추가할 때 check 제약만 고치면 된다"고 적어 뒀는데, 이름이
-- 없으면 그 '고치기'가 두 DB 에서 갈린다는 것까지는 안 적혀 있었다.
--
-- H2 이름이 틀리면 조용히 넘어가지 않는다: drop 이 no-op 이 되어 옛 제약이 남고,
-- MATCH_CANCELED 를 쓰는 순간 H2 테스트가 제약 위반으로 깨진다.
-- ─────────────────────────────────────────────────────────────────────────────

alter table match_requests drop constraint if exists match_requests_status_check;
alter table match_requests drop constraint if exists CONSTRAINT_CE;

alter table match_requests add constraint ck_match_requests_status
    check (status in ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELED', 'MATCH_CANCELED'));
