-- 수동 구장 시드 (계약서 §8-1, v1.27.0).
--
-- V19 와 나눈 이유: V19 는 스키마, 여기는 데이터다. 한 파일에 섞으면 시드를 고치려고
-- 새 마이그레이션을 쓸 때 스키마 이력과 데이터 이력이 같은 번호에 묶여 읽기 어려워진다.
--
-- 계약서 §8-1 은 경기·인천·사설을 운영자가 직접 넣게 했고, v1 에는 관리 API 가 없다.
-- 그래서 여기가 유일한 입력 경로다.
--
-- **전부 실제로 열려 있는 예약 페이지다.** 넣기 전에 각 주소를 직접 열어 200 을 확인했다.
-- 이 기능의 전부가 "외부 예약 페이지로 내보내기"라 죽은 링크는 기능이 없는 것보다 나쁘다 —
-- 사용자는 우리 앱이 고장 난 것으로 본다.
--
-- **개인정보는 넣지 않는다.** 시설 대표번호도 넣지 않았다. 계약서 §8-1 응답에 전화번호
-- 필드가 없어 넣을 자리도 없고, 없는 편이 나중에 지울 것도 없다.
--
-- address 가 null 인 행은 **주소를 확인하지 못한 것**이다. 예약 페이지가 주소를 적어 두지
-- 않은 곳이라, 그럴듯한 값을 지어 넣느니 비워 둔다 — 틀린 주소는 없는 주소보다 나쁘다.
--
-- 성남 세 곳이 같은 주소를 가리키는 것은 오타가 아니다. 성남도시개발공사는 구장별 딥링크
-- 없이 목록 한 장에서 전부 예약하게 해 두었다.
--
-- accept_status·use_period 는 넣지 않는다 (SEOUL_PUBLIC 전용 — 계약서 §8-1).
-- created_at·updated_at 은 not null 이라 채운다. JPA 감사는 엔티티로 저장할 때만 도는데
-- 여기는 SQL 직행이라 기본값이 없다.
insert into stadiums (external_id, name, region, address, reservation_url, source,
                      created_at, updated_at)
values
    (null, '인조잔디구장(성남종합운동장)', '경기 성남시', '경기 성남시 중원구 제일로 60',
     'https://res.isdc.co.kr/facilityList.do?facType=28', 'MANUAL',
     current_timestamp, current_timestamp),
    (null, '황송공원인조잔디구장', '경기 성남시', '경기 성남시 중원구 금빛로2길 35',
     'https://res.isdc.co.kr/facilityList.do?facType=28', 'MANUAL',
     current_timestamp, current_timestamp),
    (null, '탄천변축구장A', '경기 성남시', '경기 성남시 중원구 여수동 7-17',
     'https://res.isdc.co.kr/facilityList.do?facType=28', 'MANUAL',
     current_timestamp, current_timestamp),
    (null, '공촌유수지 체육시설 축구장', '인천 서구', null,
     'https://res.insiseol.or.kr/rent/info_09', 'MANUAL',
     current_timestamp, current_timestamp),
    (null, '연수체육공원 풋살장A', '인천 연수구', null,
     'https://www.ysfsmc.or.kr/business/culture/park_field2.jsp', 'MANUAL',
     current_timestamp, current_timestamp);
