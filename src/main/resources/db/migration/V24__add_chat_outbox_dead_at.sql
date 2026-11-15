-- 더 보내지 않기로 한 채팅 이벤트 (계약서 §10, v1.31.0 전달 규칙 보강).
--
-- V1~V23 은 이미 배포됐으므로 수정하지 않는다.
--
-- <b>왜 필요한가.</b> V23 은 "2xx 가 아니면 재시도"뿐이었다. 그런데 채팅 서버는 모르는
-- type·깨진 본문에 400 을 준다 — 그건 기다린다고 달라지지 않는다. 그대로 두면 그 행 하나가
-- 영원히 재시도되면서 5초마다 400 을 받아 온다.
--
-- 그래서 "보낼 수 없는 것으로 판명된" 행을 따로 표시한다. delivered_at 과 나누는 이유는
-- 둘이 반대 사실이라서다 — 하나는 "전달됐다", 하나는 "전달되지 않았고 앞으로도 안 된다".
-- 한 칸에 담으면 7일 정리가 둘을 같이 지운다.
--
-- <b>dead 행은 지우지 않는다.</b> 7일 정리는 delivered_at 을 기준으로 도는데 dead 행은 그
-- 칸이 비어 있어 자동으로 빠진다. 남겨 두는 것이 목적이다 — 특히 PURGE 가 dead 면
-- <b>탈퇴한 사람의 대화가 채팅 서버에 남아 있다는 뜻</b>이라, 행이 없으면 무엇을 지워야
-- 하는지조차 알 수 없다(개인정보 보호법 제21조).
alter table chat_outbox add column dead_at timestamp(6);

-- 미전송을 고르는 조건에 dead 가 더해진다. V23 의 인덱스를 대체한다.
drop index idx_chat_outbox_pending;
create index idx_chat_outbox_pending on chat_outbox (delivered_at, dead_at, next_attempt_at, id);

-- 1시간 넘게 못 보낸 PURGE 를 찾는 경로 (개인정보 삭제가 늦어지고 있다는 경보).
-- event_type 을 앞에 두는 것은 PURGE 만 보기 때문이다.
create index idx_chat_outbox_purge_age on chat_outbox (event_type, delivered_at, created_at);
