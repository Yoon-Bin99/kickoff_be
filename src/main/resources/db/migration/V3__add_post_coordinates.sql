-- 모집글 지도 좌표 (계약서 §5-1, v1.5.0).
--
-- 타입은 손으로 고르지 않고 Hibernate 가 PostgreSQLDialect 로 뽑은 DDL 을 그대로 옮겼다
-- (README "스키마 마이그레이션" 절차). Double 은 float(53) 으로 나가며, 이는 PostgreSQL 의
-- double precision 과 같은 타입이다. 임의로 numeric 같은 걸 쓰면 ddl-auto=validate 가
-- 어긋나 기동이 막힌다.
--
-- 둘 다 nullable 이다. 좌표 없이도 글을 쓸 수 있고(장소 직접 입력), 기존 글은 전부 null 이다.
-- "쌍으로만 존재한다"는 규칙은 DB 제약이 아니라 서비스에서 지킨다 — 한쪽만 채우는 경로가
-- 애초에 없기 때문이고, check 제약을 걸면 기존 행 마이그레이션만 번거로워진다.
--
-- V1·V2 는 이미 배포됐으므로 수정하지 않는다. 고치면 Flyway 체크섬이 깨져 기동이 막힌다.
alter table match_posts add column latitude float(53);
alter table match_posts add column longitude float(53);
