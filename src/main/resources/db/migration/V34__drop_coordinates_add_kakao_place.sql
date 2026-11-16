-- 장소 저장 방식 변경 (계약서 §5-1, v1.37.0).
--
-- V1~V33 은 이미 배포됐으므로 수정하지 않는다.
--
-- <b>카카오 로컬 검색의 응답은 저장할 수 없다</b>(카카오 운영자 답변 2026-07~10). 저장해도
-- 되는 것은 장소 ID 와 사용자가 직접 입력한 글자뿐이다. 그래서 좌표 두 칸을 없애고,
-- 대신 "사용자가 고른 장소 ID"와 "그때 사용자가 친 검색어"를 둔다 — 지도는 보는 사람의
-- 기기가 그 둘로 다시 조회해 그린다.
alter table match_posts drop column latitude;
alter table match_posts drop column longitude;

-- 숫자로만 된 문자열이지만 varchar 다. 카카오가 주는 식별자이고 우리가 계산에 쓰지
-- 않으므로, 자릿수가 늘거나 모양이 바뀌어도 그대로 담을 수 있는 쪽이 안전하다.
alter table match_posts add column kakao_place_id varchar(20);
alter table match_posts add column place_query varchar(100);

-- <b>기존 글의 주소를 전부 비운다.</b> v1.29.0 이 검색 결과의 주소를 자동으로 채워 넣고
-- 있었는데, 지금 와서는 그렇게 채워진 값과 사용자가 직접 적은 값을 구별할 방법이 없다.
-- 남겨 두면 "저장하면 안 되는 카카오 응답"이 섞인 채로 남는다 — 지우는 쪽이 맞다.
update match_posts set location_address = null;
