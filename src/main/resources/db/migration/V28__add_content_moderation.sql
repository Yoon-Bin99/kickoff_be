-- 운영 조치로 가려진 콘텐츠 (계약서 §7-2 「가려진 콘텐츠」, v1.35.0).
--
-- V1~V27 은 이미 배포됐으므로 수정하지 않는다.
--
-- <b>표를 따로 만들지 않고 콘텐츠에 칸을 붙인다.</b> 가려진 것을 목록에서 빼는 일이
-- 모집글·리뷰 조회의 where 절이라서, 별도 표로 두면 조회마다 조인이 붙고 빠뜨리는 자리가
-- 생긴다. 조치의 "이력"은 신고 기록(뒤 단계)이 들고, 여기 있는 것은 지금 상태뿐이다.
--
-- 기한(until)은 HIDDEN 에만 있다. 지나면 자동으로 풀리는데, 그 판정은 조회할 때 하고
-- 배치로 칸을 비우지 않는다 — 배치가 도는 시각까지 늦게 풀리면 안 되기 때문이다.

alter table match_posts add column moderation_status varchar(20);
alter table match_posts add column moderation_until timestamp(6);

alter table reviews add column moderation_status varchar(20);
alter table reviews add column moderation_until timestamp(6);

-- 팀은 <b>소개</b>만 조치 대상이다(팀 이름·팀 자체를 가리는 조치는 없다 — 계약서 §7-2 표).
-- 칸 이름에 introduction 을 붙여 그 범위를 못 박는다.
alter table teams add column introduction_moderation_status varchar(20);
alter table teams add column introduction_moderation_until timestamp(6);

-- 가려진 모집글은 목록에서 빠진다. 목록은 이미 matchAt 범위로 크게 좁혀지므로 이 칸만
-- 따로 인덱스를 두지 않는다 — 대부분의 행이 null 이라 선택도도 낮다.
