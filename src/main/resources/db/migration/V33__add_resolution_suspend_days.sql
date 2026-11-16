-- 조치에 붙은 이용 제한 날수 (계약서 §7-2 — ReportDetail.resolution.suspendDays, v1.37.2).
--
-- V1~V32 는 이미 배포됐으므로 수정하지 않는다.
--
-- 운영자가 나중에 그 건을 다시 열었을 때 "며칠 제한했는지"가 보여야 한다. 사용자의
-- suspended_until 로는 알 수 없다 — 그 값은 기한이 지나면 의미를 잃고, 여러 건이
-- 겹치면 어느 조치의 결과인지 가려지지 않는다.
alter table reports add column resolution_suspend_days integer;
