-- 매칭에서 만든 경기 기록 (계약서 §4-1, v1.10.0).
--
-- 기록이 어느 매칭에서 나왔는지를 가리킨다. 손으로 넣은 기록은 null 이다. 스코어는 여전히
-- 사람이 적고 자동으로 채워지는 건 상대 팀 이름과 경기 날짜뿐이라, "자동 연동"이 아니라
-- "연결"이다 — 앱은 경기 결과를 알 방법이 없다.
--
-- 타입은 Hibernate 가 PostgreSQLDialect 로 뽑은 DDL 그대로다 (README 절차).
--
-- 유니크는 (request_id, team_id) 하나면 충분하다. **부분 인덱스가 필요 없다** — 표준 SQL 에서
-- NULL 은 서로 같지 않은 것으로 보므로, request_id 가 null 인 수동 기록은 같은 팀에 몇 건이
-- 있어도 이 제약에 걸리지 않는다. PostgreSQL 과 H2 둘 다 그렇게 동작한다. 부분 인덱스
-- (where request_id is not null)를 쓰면 같은 결과를 얻지만 H2 가 지원하지 않아 개발·테스트
-- 에서만 제약이 사라진다 — 그러면 운영에서만 터지는 종류의 차이가 생긴다.
-- 이 "수동 기록은 여러 건이어도 통과한다"는 성질은 테스트로 고정했다.
--
-- 서비스가 409 RECORD_ALREADY_EXISTS 로 먼저 걸러내지만, 같은 팀이 같은 매칭에 동시에 두 번
-- 요청하는 경합은 검사만으로 막히지 않아서 DB 에도 건다.
--
-- V1~V6 은 이미 배포됐으므로 수정하지 않는다. 고치면 Flyway 체크섬이 깨져 기동이 막힌다.
alter table team_records add column request_id bigint;

alter table team_records
    add constraint fk_team_records_request foreign key (request_id) references match_requests;

alter table team_records
    add constraint uk_team_records_request_team unique (request_id, team_id);
