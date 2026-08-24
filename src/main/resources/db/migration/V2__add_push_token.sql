-- 푸시 알림 (계약서 §8, v1.4.0). 사용자당 Expo push token 하나.
-- 다중 기기는 v2 범위라 별도 테이블 대신 컬럼으로 둔다 — 마지막 등록이 이긴다.
--
-- V1 은 이미 배포됐으므로 수정하지 않는다. 고치면 Flyway 체크섬이 깨져 기동이 막힌다.
alter table users add column expo_push_token varchar(200);
