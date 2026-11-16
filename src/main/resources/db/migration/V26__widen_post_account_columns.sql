-- 모집글 입금 계좌 칸을 암호문 길이에 맞춰 넓힌다 (계약서 §5).
--
-- V1~V25 는 이미 배포됐으므로 수정하지 않는다.
--
-- 세 칸을 암호화해 저장하게 되면서 저장되는 문자열이 길어졌다. 형식은
-- `v1:` + base64(12바이트 IV ‖ 암호문 ‖ 16바이트 태그)다. 평문 기준 상한(계약서 §5 의
-- 20/30/20자)이 한글이면 바이트로 세 배가 되므로, 최악(한글 20자 = 60바이트)에
-- 태그·IV·base64 를 더하면 120자 남짓이다. 255 로 두면 다음에 알고리즘이 바뀌어도 여유가 있다.
--
-- <b>평문 상한은 그대로다.</b> 넓어진 것은 저장 칸이고, 입력 검증은 엔티티 쪽 평문
-- 값에서 돈다 — 이 숫자를 보고 "계좌번호를 255자까지 받는다"고 읽지 말 것.
--
-- 기존 행의 평문은 이 마이그레이션이 건드리지 않는다. 암호화에는 키가 필요해서 SQL 로
-- 할 수 없고, 기동 때 AccountEncryptionBackfill 이 옮긴다(여러 번 돌아도 안전하다).
alter table match_posts alter column bank_name      set data type varchar(255);
alter table match_posts alter column account_number set data type varchar(255);
alter table match_posts alter column account_holder set data type varchar(255);
