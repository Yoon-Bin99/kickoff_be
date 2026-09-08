-- 시드 계정의 전화번호를 도달 불가 대역으로 바꾼다 (2026-09-08 감사).
--
-- V1~V17 은 이미 배포됐으므로 수정하지 않는다.
--
-- 배경. 시드가 010-1234-5678, 010-2345-6789 처럼 <b>실제로 배정될 수 있는 번호</b>를
-- 쓰고 있었다. 코드는 6063958 에서 010-0000-000X 로 고쳤지만, 시드는 빈 DB 에서만 돌기
-- 때문에 <b>이미 들어간 운영 행은 그대로 남는다.</b> 그래서 데이터 마이그레이션이 필요하다.
--
-- 왜 급한가. 개인정보 문제이기도 하지만 더 즉각적인 피해가 있다 — v1.17.0 의 "전화번호
-- 1인 1계정" 때문에 <b>그 번호의 실제 주인이 가입하려 하면 PHONE_ALREADY_EXISTS 로
-- 막힌다.</b> 본인 번호인데 이미 쓰이고 있다는 답을 받고, 우회할 방법이 없다.
--
-- 각 UPDATE 에 NOT EXISTS 가드를 붙인 이유가 핵심이다. users.phone 은 V13 에서 유니크가
-- 걸렸다. 목표 번호가 이미 다른 계정에 있으면 UPDATE 가 제약 위반으로 실패하고, 그러면
-- <b>Flyway 가 멈춰 배포 전체가 죽는다.</b> 가드가 있으면 그 줄만 0행으로 조용히 넘어간다.
-- 데이터 마이그레이션은 어떤 상태의 DB 에서 돌지 알 수 없으므로, 실패하지 않는 쪽이 맞다.
--
-- 빈 DB(테스트·새 배포)에서는 전부 0행이라 무해하고, 이미 바뀐 DB 에서 다시 돌아도
-- 가드에 걸려 0행이다 — 멱등이다.
--
-- 소셜 시드 계정은 email 이 null 이라(소셜 계정은 이메일을 저장하지 않는다, v1.3.4)
-- 닉네임으로 특정한다.

update users set phone = '010-0000-0001'
 where email = 'kim@example.com'
   and not exists (select 1 from users u2 where u2.phone = '010-0000-0001');

update users set phone = '010-0000-0002'
 where email = 'lee@example.com'
   and not exists (select 1 from users u2 where u2.phone = '010-0000-0002');

update users set phone = '010-0000-0003'
 where email = 'park@example.com'
   and not exists (select 1 from users u2 where u2.phone = '010-0000-0003');

update users set phone = '010-0000-0004'
 where email = 'choi@example.com'
   and not exists (select 1 from users u2 where u2.phone = '010-0000-0004');

update users set phone = '010-0000-0005'
 where email = 'jung@example.com'
   and not exists (select 1 from users u2 where u2.phone = '010-0000-0005');

update users set phone = '010-0000-0006'
 where email = 'yoon@example.com'
   and not exists (select 1 from users u2 where u2.phone = '010-0000-0006');

update users set phone = '010-0000-0007'
 where email is null and nickname = '카카오가입자'
   and not exists (select 1 from users u2 where u2.phone = '010-0000-0007');
