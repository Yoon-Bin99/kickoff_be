-- V13 (닉네임·전화번호 유니크) 배포 전 운영 중복 점검 — PostgreSQL 전용, 읽기 전용.
-- Railway 대시보드의 쿼리 창에 붙여 넣고 실행한다. 데이터를 바꾸지 않는다.
--
-- 왜 필요한가: 중복이 있는 채로 유니크 제약을 올리면 Flyway 가 실패하고,
-- Flyway 실패는 곧 기동 실패다 — 배포가 통째로 멈추고 롤백 말고는 손쓸 방법이 없다.
--
-- 판정 기준은 앱과 같아야 한다 (계약서 §3, v1.16.0):
--   닉네임·이메일 = 대소문자 무시(lower)   전화번호 = 정확 일치, null 제외

-- ── [1/2] 먼저 이걸 돌린다. 아래 [2/2] 의 "0행"이 진짜 0행인지 확인하는 대조군이다.
select count(*)      as 전체_사용자수,
       count(phone)  as 전화번호_있는_계정,
       count(email)  as 이메일_있는_계정
  from users;

-- ── [2/2] 중복 점검. 결과가 0행이면 깨끗한 것이고 V13 을 올려도 된다.
select '닉네임'   as 항목,
       lower(nickname) as 값,
       count(*)        as 건수,
       string_agg(id::text || ':' || nickname, ' | ' order by id) as 해당_행
  from users
 group by lower(nickname)
having count(*) > 1

union all

select '전화번호',
       phone,
       count(*),
       string_agg(id::text, ' | ' order by id)
  from users
 where phone is not null
 group by phone
having count(*) > 1

union all

select '이메일',
       lower(email),
       count(*),
       string_agg(id::text || ':' || email, ' | ' order by id)
  from users
 where email is not null
 group by lower(email)
having count(*) > 1;

-- email/phone 에 "is not null" 이 붙은 이유: PostgreSQL 은 group by 에서 null 들을
-- 한 그룹으로 묶는다. 소셜 가입자는 전화번호·이메일이 없을 수 있어서, 빼지 않으면
-- "null 이 여럿"이 중복으로 잡혀 <b>없는 문제</b>가 보고된다. 유니크 제약에서 null 은
-- 서로 다른 값이라 실제로는 아무 문제가 없다.
