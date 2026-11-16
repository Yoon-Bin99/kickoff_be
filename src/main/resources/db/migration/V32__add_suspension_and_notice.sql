-- 이용 제한과 알림 메시지 (계약서 §7-2, v1.35.0).
--
-- V1~V31 은 이미 배포됐으므로 수정하지 않는다.

-- 이용 제한이 끝나는 시각. 미래면 제한 중이고, 지나면 저절로 풀린다 — 해제 배치를 두지
-- 않는다(배치가 도는 시각까지 늦게 풀리고, 안 도는 날이 있다).
alter table users add column suspended_until timestamp(6);

-- 고객센터 메시지에 NOTICE 를 더한다 (계약서 §7-1).
--
-- 서버가 넣는 알림이다. 사용자에게는 "킥오프 고객센터"로 보이지만 방을 운영자 모드로
-- 바꾸지 않는다 — 바꾸면 알림 한 줄 때문에 AI 응대가 멈추고 아무도 답하지 않는 방이 된다.
alter table support_messages drop constraint ck_support_messages_sender;
alter table support_messages add constraint ck_support_messages_sender
    check (sender in ('USER', 'AI', 'OPERATOR', 'NOTICE'));
