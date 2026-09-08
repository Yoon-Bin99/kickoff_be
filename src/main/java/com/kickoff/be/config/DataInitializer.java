package com.kickoff.be.config;

import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.repository.MatchRequestRepository;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.repository.MatchPostRepository;
import com.kickoff.be.review.entity.Review;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.team.entity.AgeGroup;
import com.kickoff.be.team.entity.SkillLevel;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.Position;
import com.kickoff.be.team.entity.TeamAdmin;
import com.kickoff.be.team.entity.TeamMember;
import com.kickoff.be.team.entity.TeamJoinRequest;
import com.kickoff.be.team.entity.TeamRecord;
import com.kickoff.be.team.repository.TeamAdminRepository;
import com.kickoff.be.team.repository.TeamMemberRepository;
import com.kickoff.be.team.repository.TeamJoinRequestRepository;
import com.kickoff.be.team.repository.TeamRecordRepository;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.oauth.entity.AuthProvider;
import com.kickoff.be.oauth.entity.SocialAccount;
import com.kickoff.be.oauth.repository.SocialAccountRepository;
import com.kickoff.be.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시드 데이터. FE 가 목록/필터/무한스크롤/매칭·입금 화면을 바로 붙여볼 수 있게
 * 팀 6개, 모집글 34개, 신청 몇 건, 리뷰 4건을 넣는다. 비밀번호는 전부 pass1234.
 *
 * 프로파일이 아니라 kickoff.seed-data 로 켠다. 개발에서는 기본 on 이고, 배포 환경에서는
 * SEED_DATA=true 를 줄 때만 돈다 — 첫 배포 직후 볼 게 아무것도 없는 상태를 피하려는 것이고,
 * 실제 사용자가 쓰기 시작하면 꺼야 한다. 이미 데이터가 있으면 어느 쪽이든 건너뛴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kickoff.seed-data", havingValue = "true")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final String RAW_PASSWORD = "pass1234";

    /*
     * 지도용 좌표 (계약서 §5-1). 카카오 로컬 키워드 검색으로 실제 조회한 값이라 지도에
     * 찍으면 진짜 그 구장에 마커가 선다 — FE 가 눈으로 검증할 수 있어야 의미가 있다.
     *
     * 강서구민운동장만 예외다. 카카오 검색이 부산 화명동을 유사 매칭으로 돌려줘서
     * 계약서 §5-1 예시에 적힌 값을 그대로 썼다.
     *
     * 좌표를 주지 않은 글이 훨씬 많다. 좌표 있는 글과 없는 글 양쪽을 FE 가 다 봐야 한다.
     */
    private static final double[] GANGSEO = {37.5586, 126.8351};
    private static final double[] OLYMPIC_PARK = {37.5169382511733, 127.123340764599};
    private static final double[] GOYANG_STADIUM = {37.67640372499092, 126.74308829942302};
    private static final double[] TANCHEON = {37.4101849257468, 127.121331148072};
    private static final double[] NAMDONG = {37.4346425602455, 126.733811034142};

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final MatchPostRepository postRepository;
    private final MatchRequestRepository requestRepository;
    private final ReviewRepository reviewRepository;
    private final TeamAdminRepository teamAdminRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRecordRepository teamRecordRepository;
    private final TeamJoinRequestRepository joinRequestRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordEncoder passwordEncoder;

    /** 입금받을 계좌. depositAmount 를 넣는 글에만 붙는다. */
    private record SeedAccount(String bankName, String accountNumber, String accountHolder) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("시드 생략 — 이미 데이터가 있다");
            return;
        }

        // 전화번호는 010-0000-000X 로만 쓴다. 국번 0000 은 실제로 배정되지 않아 어디에도
        // 닿지 않는다. 그럴듯한 번호(010-1234-5678 같은 것)를 넣으면 언젠가 그 번호의
        // 주인에게 문자가 간다 — 실제로 한 번 그렇게 나갔다. 운영도 SEED_DATA 로 이 행들을
        // 그대로 넣으므로 "개발용이니까 괜찮다"가 성립하지 않는다.
        // 이메일도 같은 이유로 예약 도메인(@example.com)만 쓴다 (RFC 2606).
        Team saebyeok = createTeam("kim@example.com", "김주장", "010-0000-0001", "서울",
                "FC 새벽", "서울 강서구", "강서구민운동장",
                SkillLevel.INTERMEDIATE, AgeGroup.THIRTIES, 18,
                "매주 토요일 오전 7시에 모입니다. 매너 있는 경기 지향합니다.");
        SeedAccount kimAcc = new SeedAccount("카카오뱅크", "3333-01-1234567", "김주장");

        Team mapo = createTeam("lee@example.com", "이감독", "010-0000-0002", "서울",
                "마포 유나이티드", "서울 마포구", "마포구립축구장",
                SkillLevel.ADVANCED, AgeGroup.TWENTIES, 22,
                "실력 있는 팀 환영합니다. 주말 오전 위주로 뜁니다.");
        SeedAccount leeAcc = new SeedAccount("신한은행", "110-234-567890", "이감독");

        Team songpa = createTeam("park@example.com", "박캡틴", "010-0000-0003", null,
                "송파 FC", "서울 송파구", "올림픽공원 축구장",
                SkillLevel.BEGINNER, AgeGroup.MIXED, 15,
                "이제 막 시작한 팀입니다. 즐겁게 뛰실 분들 구해요.");
        SeedAccount parkAcc = new SeedAccount("국민은행", "123456-04-567890", "박캡틴");

        Team goyang = createTeam("choi@example.com", "최총무", "010-0000-0004", "경기",
                "고양 킥커스", "경기 고양시", "고양종합운동장 보조구장",
                SkillLevel.AMATEUR, AgeGroup.FORTIES, 20,
                "40대 위주 팀입니다. 부상 없는 경기가 최우선.");
        SeedAccount choiAcc = new SeedAccount("우리은행", "1002-345-678901", "최총무");

        Team seongnam = createTeam("jung@example.com", "정주장", "010-0000-0005", "경기",
                "성남 레인저스", "경기 성남시", "탄천종합운동장",
                SkillLevel.INTERMEDIATE, AgeGroup.THIRTIES, 24,
                "매주 일요일 아침에 모입니다. 11인제로 꾸준히 뜁니다.");
        SeedAccount jungAcc = new SeedAccount("하나은행", "352-0123-4567-89", "정주장");

        Team incheon = createTeam("yoon@example.com", "윤코치", "010-0000-0006", null,
                "인천 스트라이커즈", "인천 남동구", "남동체육관 축구장",
                SkillLevel.ADVANCED, AgeGroup.FIFTIES_PLUS, 16,
                "연령대는 높지만 실력은 자신 있습니다.");
        SeedAccount yoonAcc = new SeedAccount("토스뱅크", "1000-1234-5678", "윤코치");

        seedSocialUser();

        // account 가 null 인 글은 depositAmount 도 null 인 "협의" 케이스.
        // dayOffset 이 음수면 이미 지난 경기다.
        List<MatchPost> posts = List.of(
                post(saebyeok, kimAcc, "토요일 아침 11인제 상대 구합니다",
                        "11인제로 2시간 뛸 팀 찾습니다. 매너 중요합니다.",
                        2, LocalTime.of(7, 0), "강서구민운동장 A구장", "서울 강서구",
                        SkillLevel.INTERMEDIATE, 100000, 50000,
                        GANGSEO[0], GANGSEO[1]),
                post(saebyeok, kimAcc, "평일 저녁 11인제 한 판",
                        "수요일 저녁에 풀피치로 뛸 팀 구합니다. 조명 있습니다.",
                        5, LocalTime.of(20, 0), "강서구민운동장 주경기장", "서울 강서구",
                        null, 240000, 120000),
                post(saebyeok, null, "강서 일요일 11인제 협의",
                        "비용은 만나서 반반으로 협의하시죠.",
                        11, LocalTime.of(9, 0), "강서구민운동장 B구장", "서울 강서구",
                        SkillLevel.AMATEUR, 150000, null),
                post(saebyeok, kimAcc, "강서 토요일 오후 11인제",
                        "오후 시간대도 괜찮으신 팀 구합니다.",
                        16, LocalTime.of(16, 0), "화곡 축구장", "서울 강서구",
                        SkillLevel.BEGINNER, 60000, 30000),
                post(saebyeok, kimAcc, "지난 주 강서 11인제 (상대 못 구한 글)",
                        "날짜가 지났는데 아직 모집중인 글입니다. 목록 정렬 확인용.",
                        -4, LocalTime.of(7, 0), "강서구민운동장 A구장", "서울 강서구",
                        SkillLevel.INTERMEDIATE, 90000, 45000),

                post(mapo, leeAcc, "일요일 오전 11인제 매칭",
                        "실력 있는 팀 환영합니다. 심판비 포함 금액입니다.",
                        3, LocalTime.of(9, 0), "마포구립축구장", "서울 마포구",
                        SkillLevel.ADVANCED, 180000, 90000),
                post(mapo, null, "주말 11인제 정기전 상대 구해요",
                        "매주 하실 팀이면 더 좋습니다. 비용은 협의합니다.",
                        9, LocalTime.of(10, 0), "마포 축구장 B", "서울 마포구",
                        null, 80000, null),
                post(mapo, leeAcc, "마포 평일 야간 11인제",
                        "퇴근하고 바로 오실 수 있는 팀이면 좋겠습니다.",
                        6, LocalTime.of(21, 0), "상암 보조구장", "서울 마포구",
                        SkillLevel.ADVANCED, 260000, 130000),
                post(mapo, leeAcc, "마포 11인제 가볍게",
                        "부담 없이 한 경기 하실 팀.",
                        14, LocalTime.of(11, 0), "마포 축구장 A", "서울 마포구",
                        SkillLevel.INTERMEDIATE, 70000, 35000),
                post(mapo, leeAcc, "마포 주말 11인제 추가 모집",
                        "한 팀 더 구합니다.",
                        19, LocalTime.of(8, 0), "마포구립축구장", "서울 마포구",
                        null, 170000, 85000),

                post(songpa, parkAcc, "초보팀끼리 즐겁게 한 경기",
                        "입문 수준입니다. 살살 해주실 팀 찾아요. 커피 쏘겠습니다.",
                        4, LocalTime.of(8, 0), "올림픽공원 축구장 2번", "서울 송파구",
                        SkillLevel.BEGINNER, 60000, 30000,
                        OLYMPIC_PARK[0], OLYMPIC_PARK[1]),
                post(songpa, parkAcc, "송파 일요일 오후 11인제",
                        "오후 시간대 선호하는 팀 있으면 연락 주세요.",
                        10, LocalTime.of(15, 0), "송파구민체육관 축구장", "서울 송파구",
                        null, 70000, 35000),
                post(songpa, parkAcc, "송파 11인제 초보 환영",
                        "저희도 못합니다. 편하게 오세요.",
                        7, LocalTime.of(19, 0), "잠실 축구장", "서울 송파구",
                        SkillLevel.BEGINNER, 50000, 25000),
                post(songpa, null, "송파 11인제 도전",
                        "인원 모아서 처음 11인제 해봅니다. 비용 협의.",
                        17, LocalTime.of(10, 0), "올림픽공원 축구장 1번", "서울 송파구",
                        null, 200000, null),
                post(songpa, parkAcc, "지난 주말 송파 11인제 (종료)",
                        "지난 경기입니다.",
                        -8, LocalTime.of(9, 0), "올림픽공원 축구장 2번", "서울 송파구",
                        SkillLevel.BEGINNER, 55000, 28000),

                post(goyang, choiAcc, "고양 토요일 오전 11인제",
                        "40대 위주 팀입니다. 비슷한 연령대면 더 좋습니다.",
                        2, LocalTime.of(10, 0), "고양종합운동장 보조구장", "경기 고양시",
                        SkillLevel.AMATEUR, 220000, 110000,
                        GOYANG_STADIUM[0], GOYANG_STADIUM[1]),
                post(goyang, choiAcc, "평일 낮 11인제 하실 팀",
                        "자영업자 팀이라 평일 낮이 편합니다. 같은 사정인 팀 환영.",
                        7, LocalTime.of(14, 0), "일산 축구장 1구장", "경기 고양시",
                        SkillLevel.AMATEUR, 90000, 45000),
                post(goyang, choiAcc, "고양 일요일 11인제",
                        "일요일 아침에 뜁니다.",
                        13, LocalTime.of(8, 0), "고양종합운동장 보조구장", "경기 고양시",
                        SkillLevel.INTERMEDIATE, 160000, 80000),
                post(goyang, choiAcc, "일산 11인제 친선",
                        "가볍게 한 경기 하실 팀 구합니다.",
                        20, LocalTime.of(17, 0), "일산 축구장 2구장", "경기 고양시",
                        null, 65000, 32000),
                post(goyang, null, "고양 11인제 비용 협의",
                        "구장은 저희가 잡았습니다. 비용은 이야기해보시죠.",
                        15, LocalTime.of(11, 0), "고양종합운동장 주경기장", "경기 고양시",
                        SkillLevel.ADVANCED, 230000, null),

                post(seongnam, jungAcc, "성남 일요일 아침 11인제",
                        "탄천에서 뜁니다. 주차 편합니다.",
                        3, LocalTime.of(7, 30), "탄천종합운동장 축구장", "경기 성남시",
                        SkillLevel.INTERMEDIATE, 160000, 80000,
                        TANCHEON[0], TANCHEON[1]),
                post(seongnam, null, "다음 주 토요일 11인제 상대 구합니다",
                        "정기전 상대가 펑크나서 급하게 구합니다. 비용은 반반 협의.",
                        9, LocalTime.of(6, 30), "탄천종합운동장 주경기장", "경기 성남시",
                        null, 260000, null),
                post(seongnam, jungAcc, "성남 평일 저녁 11인제",
                        "퇴근 후 가볍게 뛰실 팀.",
                        6, LocalTime.of(20, 30), "분당 축구장", "경기 성남시",
                        SkillLevel.INTERMEDIATE, 80000, 40000),
                post(seongnam, jungAcc, "성남 주말 11인제",
                        "11인제로 두 시간 뜁니다.",
                        12, LocalTime.of(13, 0), "야탑 축구장", "경기 성남시",
                        SkillLevel.AMATEUR, 75000, 38000),
                post(seongnam, jungAcc, "성남 매칭 완료된 경기",
                        "이미 상대가 정해진 글입니다. FE 뱃지 확인용.",
                        8, LocalTime.of(9, 0), "탄천종합운동장 축구장", "경기 성남시",
                        SkillLevel.INTERMEDIATE, 170000, 85000),

                post(incheon, yoonAcc, "인천 11인제 고수팀 구합니다",
                        "제대로 붙어볼 팀 찾습니다. 실력 자신 있는 팀만.",
                        6, LocalTime.of(19, 0), "남동체육관 축구장", "인천 남동구",
                        SkillLevel.ADVANCED, 120000, 60000,
                        NAMDONG[0], NAMDONG[1]),
                post(incheon, yoonAcc, "인천 주말 11인제 친선경기",
                        "가볍게 몸 풀 겸 한 경기 하실 팀 구해요.",
                        13, LocalTime.of(11, 0), "송도 축구장 3번", "인천 남동구",
                        SkillLevel.BEGINNER, 50000, 25000),
                post(incheon, yoonAcc, "인천 11인제 정기전 상대",
                        "매달 한 번씩 하실 팀이면 좋겠습니다.",
                        18, LocalTime.of(8, 0), "인천축구전용경기장 보조", "인천 남동구",
                        SkillLevel.ADVANCED, 250000, 125000),
                post(incheon, yoonAcc, "인천 11인제 매칭 완료",
                        "상대 정해졌고 입금까지 확인된 글입니다.",
                        10, LocalTime.of(10, 0), "남동 아시아드 보조구장", "인천 남동구",
                        SkillLevel.INTERMEDIATE, 180000, 90000),
                post(incheon, yoonAcc, "지난 주 인천 11인제 (종료)",
                        "지난 경기입니다.",
                        -2, LocalTime.of(19, 0), "남동체육관 축구장", "인천 남동구",
                        SkillLevel.ADVANCED, 110000, 55000),

                // 아래 3개는 리뷰용이다. 리뷰는 "끝난 경기의 수락된 매칭"에만 달 수 있는데
                // (계약서 §7) 위 30개에는 그 조합이 없다. 지난 경기이므로 목록에는 안 잡히고,
                // 매칭관리/팀 상세에서만 보인다.
                post(saebyeok, kimAcc, "지난 달 강서 정기전 (리뷰 완료)",
                        "끝난 경기입니다. 양 팀이 서로 리뷰를 남겼습니다.",
                        -12, LocalTime.of(7, 0), "강서구민운동장 A구장", "서울 강서구",
                        SkillLevel.BEGINNER, 100000, 50000),
                post(mapo, leeAcc, "지난 주 마포 11인제 (리뷰 대기)",
                        "끝난 경기입니다. 상대 팀이 아직 리뷰를 안 썼습니다.",
                        -6, LocalTime.of(9, 0), "마포구립축구장", "서울 마포구",
                        SkillLevel.BEGINNER, 180000, 90000),
                post(goyang, choiAcc, "지난 주 일산 11인제 (리뷰 대기)",
                        "끝난 경기입니다. 상대 팀이 아직 리뷰를 안 썼습니다.",
                        -5, LocalTime.of(14, 0), "일산 축구장 1구장", "경기 고양시",
                        SkillLevel.BEGINNER, 90000, 45000),
                post(seongnam, jungAcc, "지난 주 성남 11인제 (양 팀 미작성)",
                        "끝난 경기입니다. 아직 아무도 리뷰를 안 썼습니다.",
                        -3, LocalTime.of(20, 0), "분당 축구장", "경기 성남시",
                        SkillLevel.BEGINNER, 80000, 40000),

                /*
                 * 심야 경기 3개 (계약서 §5, v1.18.0).
                 *
                 * 시간대 필터가 생기면서 "시각이 골고루 섞여 있는가"도 시드의 몫이 됐는데,
                 * 위 34개는 06:30~21:00 안에만 있어 NIGHT(22~05시)이 통째로 비어 있었다.
                 * 그러면 times=NIGHT 이 0건을 내놓고, 그 0건이 <b>규칙이 맞아서 0건인지
                 * 구현이 빠져서 0건인지</b> 화면에서 구분되지 않는다 — 필터가 아예 동작하지
                 * 않아도 똑같이 0건이다.
                 *
                 * 게다가 NIGHT 은 자정을 넘는 유일한 구간이라 (TimeSlot 참고) 다섯 중 가장
                 * 틀리기 쉬운데, 하필 그것만 확인할 데이터가 없었다.
                 *
                 * 그래서 셋은 각각 다른 것을 보여준다. 지우면 그 구간이 다시 안 보인다.
                 *   - 22:00 정각: EVENING 이 아니라 NIGHT (경계는 시작 포함·끝 제외)
                 *   - 23:30    : 자정 직전
                 *   - 01:00    : 자정 직후 — 앞의 둘과 같은 슬롯으로 묶여야 한다
                 */
                post(mapo, leeAcc, "심야 11인제 상대 구합니다",
                        "10시 시작입니다. 조명 켜고 두 시간 뜁니다. 야간 좋아하는 팀 환영.",
                        4, LocalTime.of(22, 0), "상암 보조구장", "서울 마포구",
                        SkillLevel.INTERMEDIATE, 220000, 110000),
                post(seongnam, jungAcc, "성남 심야 11인제 (11시 반 시작)",
                        "늦은 시간이라 구장이 비어 대여료가 쌉니다. 끝나고 국밥 먹어요.",
                        8, LocalTime.of(23, 30), "분당 축구장", "경기 성남시",
                        SkillLevel.AMATEUR, 90000, 45000),
                post(saebyeok, kimAcc, "새벽 1시 11인제 하실 팀",
                        "교대 근무라 이 시간이 편한 팀들끼리 뜁니다. 조명 있습니다.",
                        9, LocalTime.of(1, 0), "강서구민운동장 주경기장", "서울 강서구",
                        null, 120000, 60000)
        );
        postRepository.saveAll(posts);

        // 아래는 전부 위 목록의 순번을 그대로 가리킨다. 글을 넣을 때는 반드시 <b>맨 뒤에</b>
        // 덧붙일 것 — 중간에 끼우면 마감·매칭·리뷰가 통째로 엉뚱한 글에 붙고, 에러 없이
        // 화면만 이상해진다.
        posts.get(14).close();
        posts.get(29).close();
        // 지난 경기는 목록에서 통째로 빠지므로, 위 둘만 마감하면 FE 가 마감 뱃지를 볼 수 없다.
        // 미래 글 하나를 마감 상태로 둬서 status=CLOSED 목록이 비지 않게 한다.
        posts.get(3).close();

        // posts.get(4)는 일부러 OPEN 인 채로 지난 경기다. 목록에서는 빠지고, id 로 상세는 열리고,
        // 신청하면 409 가 나는 "지난 경기 규칙"(계약서 §5) 검증용이다.

        // 카드에 "신청 N팀"이 보이도록 대기 중 신청을 깔아둔다
        applyPending(posts.get(0), mapo, "저희도 서울이라 가깝습니다!");
        applyPending(posts.get(0), songpa, "송파에서 갑니다. 잘 부탁드려요.");
        applyPending(posts.get(5), goyang, "고양 킥커스입니다. 한 수 배우겠습니다.");
        applyPending(posts.get(15), saebyeok, "강서에서 넘어가겠습니다.");
        applyPending(posts.get(15), seongnam, "성남 레인저스 신청합니다.");
        applyPending(posts.get(15), incheon, "인천도 갑니다.");
        applyPending(posts.get(25), songpa, "실력은 부족하지만 열심히 하겠습니다.");

        // 매칭·입금 시나리오 두 가지 — FE 매칭관리/입금 화면 확인용
        matchWith(posts.get(24), mapo, "성남까지 가겠습니다!", false);   // 수락됐고 입금 전
        matchWith(posts.get(28), songpa, "인천 가겠습니다.", true);      // 입금까지 확인됨

        // 리뷰 시나리오. 끝난 경기 셋을 전부 송파 FC 와 붙여서, 한 팀에 리뷰가 여러 건 쌓인
        // 평균(5·4·4 → 4.3)과 "아직 안 쓴 리뷰"가 같은 계정에서 동시에 보이게 한다.
        MatchRequest doneWithSaebyeok = matchWith(posts.get(30), songpa, "강서까지 원정 갑니다.", true);
        MatchRequest doneWithMapo = matchWith(posts.get(31), songpa, "마포 가겠습니다.", true);
        MatchRequest doneWithGoyang = matchWith(posts.get(32), songpa, "일산 가겠습니다.", true);
        // 리뷰가 한 건도 안 달린 끝난 매칭 하나 — 양 팀이 각각 처음부터 써 보는 경로가
        // 없으면 FE 가 "서로 한 번씩" 규칙을 빈 상태에서 확인할 수 없다.
        matchWith(posts.get(33), songpa, "성남 가겠습니다.", true);

        // 양쪽 다 쓴 매칭 하나, 작성 팀만 쓴 매칭 둘 — 송파 FC 로 로그인하면 남은 리뷰가 두 건 보인다.
        review(doneWithSaebyeok, saebyeok, songpa, 5, "시간 약속 정확하고 매너 좋았습니다.");
        review(doneWithSaebyeok, songpa, saebyeok, 4, "좋은 경기였습니다. 다음에 또 뵈어요.");
        review(doneWithMapo, mapo, songpa, 4, "즐겁게 뛰었습니다. 추천합니다.");
        review(doneWithGoyang, goyang, songpa, 4, "매너 좋은 팀입니다.");

        // 매칭에서 만든 전적 (계약서 §4-1, v1.10.0). 양 팀이 각자 자기 관점으로 남긴 한 쌍을
        // 넣어 FE 가 requestId 가 채워진 기록을 바로 볼 수 있게 한다.
        matchRecord(doneWithSaebyeok, songpa, saebyeok, 1, 3, "원정에서 아쉽게 패배");
        matchRecord(doneWithSaebyeok, saebyeok, songpa, 3, 1, "홈에서 승리");
        // 한 팀만 남긴 매칭 — 상대 계정으로 들어가면 "전적 기록하기"가 떠 있다
        matchRecord(doneWithMapo, mapo, songpa, 2, 2, null);
        // doneWithGoyang 과 성남 매칭은 <b>양 팀 다 비워 둔다</b>. 기록이 하나도 없는 지난
        // 매칭이 있어야 FE 가 버튼을 처음부터 밟아볼 수 있다 (리뷰 시드와 같은 이유).

        long teamCount = teamRepository.count();
        long postCount = postRepository.count();
        long requestCount = requestRepository.count();
        // 여기까지 왔는데 비어 있으면 빈 DB 로 서비스하느니 기동을 실패시킨다
        if (teamCount == 0 || postCount == 0) {
            throw new IllegalStateException(
                    "시드 데이터가 저장되지 않았다 — 팀 %d, 모집글 %d".formatted(teamCount, postCount));
        }
        long openCount = posts.stream().filter(MatchPost::isOpen).count();
        long withCoordinates = posts.stream().filter(MatchPost::hasCoordinates).count();
        seedTeamPage(saebyeok, goyang, goyang.getOwner());
        seedMembership(saebyeok, incheon.getOwner(), seongnam.getOwner());

        log.info("시드 데이터 생성 완료 — 팀 {}개, 모집글 {}개(OPEN {}개, 좌표 {}개), 신청 {}건, 리뷰 {}건, 명단 {}명, 기록 {}건(매칭연동 {}건), 관리자 {}명, 가입신청 {}건",
                teamCount, postCount, openCount, withCoordinates, requestCount,
                reviewRepository.count(), teamMemberRepository.count(),
                teamRecordRepository.count(), matchLinkedRecordCount(),
                teamAdminRepository.count(), joinRequestRepository.count());
    }

    /**
     * 팀 페이지 시드 (계약서 §4-1·§4-2, v1.8.0·v1.9.0).
     *
     * FE 가 화면을 바로 붙여볼 수 있게 두 팀에만 채운다. 나머지 팀은 명단·기록이 비어 있는데,
     * 그것도 실제로 나올 상태라 <b>일부러 비워 둔다</b> — 빈 화면 처리를 시드로 밟아볼
     * 대상이 없으면 그 경로는 배포 후에야 드러난다.
     */
    private void seedTeamPage(Team saebyeok, Team goyang, User choi) {
        // 프로필 확장 필드. 두 팀만 채우고 나머지는 비워 둔다 — 색이 없는 팀 카드도
        // 실제로 나올 화면이라 FE 가 기본색 처리를 시드로 밟아볼 수 있어야 한다.
        profile(saebyeok, 2015, "#1B7F4B", "4-4-2");
        profile(goyang, 2008, "#C1272D", "3-5-2");

        // 등번호 없는 팀원을 섞어 둔다. 정렬 규칙(등번호 오름차순, 없으면 뒤에 이름순)을
        // 화면에서 바로 확인할 수 있어야 한다.
        member(saebyeok, "김주장", Position.MF, 10);
        member(saebyeok, "박수비", Position.DF, 4);
        member(saebyeok, "이골키", Position.GK, 1);
        member(saebyeok, "최공격", Position.FW, 9);
        member(saebyeok, "정미드", Position.MF, 8);
        member(saebyeok, "한신입", null, null);
        member(saebyeok, "강신입", Position.DF, null);

        member(goyang, "최총무", Position.MF, 7);
        member(goyang, "윤수비", Position.DF, 3);
        member(goyang, "서골키", Position.GK, 21);
        member(goyang, "남공격", Position.FW, 11);
        member(goyang, "도미드", null, 6);
        member(goyang, "백후보", null, null);

        // 승·무·패를 섞어 요약이 0 이 아닌 화면을 만든다
        record(saebyeok, 3, "마포 유나이티드", 3, 1, "후반 역전승");
        record(saebyeok, 10, "송파 FC", 2, 2, "비 와서 미끄러웠음");
        record(saebyeok, 17, "인천 스트라이커즈", 0, 2, null);
        record(saebyeok, 24, "고양 킥커스", 4, 0, "완승");
        record(saebyeok, 31, "성남 레인저스", 1, 1, null);

        record(goyang, 5, "FC 새벽", 0, 4, "체력 부족");
        record(goyang, 12, "성남 레인저스", 2, 1, null);
        record(goyang, 19, "마포 유나이티드", 1, 3, null);
        record(goyang, 26, "송파 FC", 3, 3, "난타전");

        // 최총무를 FC 새벽의 관리자로 — 자기 팀(고양)을 가진 사람이 남의 팀 관리자도 되는
        // 경우다. FE 가 마이 탭에서 소유 1 + 관리 1 을 한 번에 확인할 수 있다.
        teamAdminRepository.save(TeamAdmin.builder().team(saebyeok).user(choi).build());
    }

    /**
     * 팀 소속·가입 시드 (계약서 §4-3, v1.11.0).
     *
     * 승인된 멤버 하나와 대기 중인 신청 하나를 넣는다. 둘 다 있어야 FE 가 "이미 멤버인 화면"과
     * "수락·거절 버튼이 뜬 화면"을 같은 팀에서 한 번에 볼 수 있다.
     */
    private void seedMembership(Team saebyeok, User yoon, User jung) {
        // 윤코치는 승인된 멤버 — 명단에 계정 연결 항목으로 올라간다 (수락 흐름과 같은 모양).
        TeamJoinRequest accepted = joinRequestRepository.save(TeamJoinRequest.builder()
                .team(saebyeok)
                .user(yoon)
                .message("주말에 시간 됩니다. 받아 주세요.")
                .build());
        accepted.accept();
        teamMemberRepository.save(TeamMember.builder()
                .team(saebyeok)
                .name(yoon.getNickname())
                .user(yoon)
                .build());

        // 정주장은 대기 중 — FC 새벽으로 로그인하면 수락·거절할 신청이 하나 보인다.
        joinRequestRepository.save(TeamJoinRequest.builder()
                .team(saebyeok)
                .user(jung)
                .message("같이 뛰고 싶습니다.")
                .build());
    }

    private void profile(Team team, int foundedYear, String teamColor, String formation) {
        team.updateFoundedYear(foundedYear);
        team.updateTeamColor(teamColor);
        team.updateFormation(formation);
        teamRepository.save(team);
    }

    /** 매칭에 연결된 기록 수 — 시드 로그에서 수동 기록과 갈라 보려는 것뿐이다. */
    private long matchLinkedRecordCount() {
        return teamRecordRepository.findAll().stream()
                .filter(record -> record.getRequestId() != null)
                .count();
    }

    /**
     * 매칭에서 만든 기록. 상대 팀 이름과 경기 날짜는 서비스와 같은 규칙으로 매칭에서 뽑는다 —
     * 시드가 다른 규칙을 쓰면 화면에서만 어긋난다.
     */
    private void matchRecord(MatchRequest request, Team team, Team opponent,
                             int ourScore, int opponentScore, String memo) {
        teamRecordRepository.save(TeamRecord.builder()
                .team(team)
                .playedOn(request.getPost().getMatchAt().toLocalDate())
                .opponentName(opponent.getName())
                .ourScore(ourScore)
                .opponentScore(opponentScore)
                .memo(memo)
                .request(request)
                .build());
    }

    private void member(Team team, String name, Position position, Integer backNumber) {
        teamMemberRepository.save(TeamMember.builder()
                .team(team)
                .name(name)
                .position(position)
                .backNumber(backNumber)
                .build());
    }

    private void record(Team team, int daysAgo, String opponentName,
                        int ourScore, int opponentScore, String memo) {
        teamRecordRepository.save(TeamRecord.builder()
                .team(team)
                .playedOn(LocalDate.now().minusDays(daysAgo))
                .opponentName(opponentName)
                .ourScore(ourScore)
                .opponentScore(opponentScore)
                .memo(memo)
                .build());
    }

    /**
     * 팀과 그 소유자를 함께 만든다.
     *
     * activityRegion 은 <b>null 을 넣는 계정을 일부러 남긴다</b> (계약서 §2, v1.6.0).
     * null 이 "전국"이라서, FE 가 지역 필터 없는 홈 화면을 붙여볼 대상이 필요하다.
     * 전부 채워 두면 그 경로를 시드로는 못 밟는다.
     */
    private Team createTeam(String email, String nickname, String phone, String activityRegion,
                            String teamName, String region, String homeGround,
                            SkillLevel skillLevel, AgeGroup ageGroup, int memberCount,
                            String introduction) {
        User owner = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(RAW_PASSWORD))
                .nickname(nickname)
                .phone(phone)
                .activityRegion(activityRegion)
                .build());
        return teamRepository.save(Team.builder()
                .owner(owner)
                .name(teamName)
                .region(region)
                .homeGround(homeGround)
                .skillLevel(skillLevel)
                .ageGroup(ageGroup)
                .memberCount(memberCount)
                .introduction(introduction)
                .build());
    }

    /**
     * 소셜로만 가입한 계정 하나 (계약서 §3-1·§3-2).
     *
     * 이메일도 비밀번호도 없고 전화번호만 있다 — 카카오로 들어와 번호를 등록한 모양이다.
     * 시드에 이런 계정이 없으면 FE 가 <b>소셜 분기를 화면으로 밟을 수 없다</b>. v1.17.0 의
     * existingAccount 는 EMAIL·KAKAO·계정 없음 세 갈래인데, 그중 하나를 만들려면 OAuth 를
     * 실제로 태워야 해서 로컬에서 만들 방법이 마땅치 않다.
     *
     * 팀을 주지 않는 것도 의도다. 팀 없는 사용자는 목록·검색 어디에도 안 나오므로 기존
     * 화면들에 영향을 주지 않는다.
     */
    private void seedSocialUser() {
        User social = userRepository.save(User.builder()
                .nickname("카카오가입자")
                .phone("010-0000-0007")
                .activityRegion("서울")
                .build());
        socialAccountRepository.save(SocialAccount.builder()
                .user(social)
                .provider(AuthProvider.KAKAO)
                .providerUserId("seed-kakao-1")
                .build());

        // 전화번호가 <b>없는</b> 계정 — v1.17.0 의 소셜 게이트(phone 이 null 인 사용자를
        // 화면에서 막는 것)를 FE 가 밟으려면 이 상태가 필요하다. 위 계정은 번호가 있어
        // 조건에 안 걸린다.
        //
        // <b>이메일도 비밀번호도 없다.</b> 계약이 "소셜 가입 계정은 email 항상 null"을
        // 불변식으로 못박고 있고(§2, v1.3.4), 그래서 FE 는 "email 이 있으면 비밀번호도
        // 있다"에 기대어 화면을 가른다 — v1.24.1 의 계정 관리 게이트가 그렇다.
        //
        // 예전에는 이 계정에 이메일·비밀번호를 넣어 뒀다. FE 가 로컬에서 로그인할 방법이
        // 없어서였는데(비밀번호 로그인 불가, OAuth 를 태울 수도 없다), 그 편의 때문에
        // <b>계약에 없는 조합</b>이 시드에 생겼다: 이메일은 있는데 비밀번호가 없는 계정을
        // 게이트가 만나면, 비밀번호를 물어 놓고 어떤 값도 통과 못 하는 화면이 된다.
        //
        // 이제는 BE 가 로컬 access token 을 만들어 줄 수 있어 그 편의가 필요 없다.
        // 시드는 계약과 같은 모양이어야 한다 — 시드가 못 만드는 상태는 FE 도 못 밟지만,
        // 시드에만 있는 상태는 <b>없는 버그를 쫓게 만든다.</b>
        User pending = userRepository.save(User.builder()
                .nickname("번호없는카카오")
                .build());
        socialAccountRepository.save(SocialAccount.builder()
                .user(pending)
                .provider(AuthProvider.KAKAO)
                .providerUserId("seed-kakao-2")
                .build());
    }

    /** 좌표 없는 글 — 장소를 직접 입력한 경우다. FE 는 상세에서 지도 영역을 숨긴다. */
    private MatchPost post(Team team, SeedAccount account, String title, String content,
                           int dayOffset, LocalTime time, String location, String region,
                           SkillLevel preferredSkillLevel,
                           Integer rentalFee, Integer depositAmount) {
        return post(team, account, title, content, dayOffset, time, location, region,
                preferredSkillLevel, rentalFee, depositAmount, null, null);
    }

    private MatchPost post(Team team, SeedAccount account, String title, String content,
                           int dayOffset, LocalTime time, String location, String region,
                           SkillLevel preferredSkillLevel,
                           Integer rentalFee, Integer depositAmount,
                           Double latitude, Double longitude) {
        OffsetDateTime matchAt = OffsetDateTime.now()
                .truncatedTo(ChronoUnit.DAYS)
                .plusDays(dayOffset)
                .with(time);
        return MatchPost.builder()
                .team(team)
                .title(title)
                .content(content)
                .matchAt(matchAt)
                .location(location)
                .region(region)
                .preferredSkillLevel(preferredSkillLevel)
                .rentalFee(rentalFee)
                .depositAmount(depositAmount)
                .bankName(account == null ? null : account.bankName())
                .accountNumber(account == null ? null : account.accountNumber())
                .accountHolder(account == null ? null : account.accountHolder())
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    private MatchRequest applyPending(MatchPost post, Team applicant, String message) {
        return requestRepository.save(MatchRequest.builder()
                .post(post)
                .applicantTeam(applicant)
                .message(message)
                .build());
    }

    /** 실제 수락 흐름과 같은 결과를 만든다 — 신청 ACCEPTED + 글 MATCHED. */
    private MatchRequest matchWith(MatchPost post, Team applicant, String message,
                                   boolean depositPaid) {
        MatchRequest accepted = applyPending(post, applicant, message);
        accepted.accept();
        if (depositPaid) {
            accepted.confirmDeposit();
        }
        post.markMatched();
        return accepted;
    }

    /** 대상 팀은 서비스가 매칭 관계로 정하지만, 시드는 엔티티를 직접 만들어 넣는다. */
    private void review(MatchRequest request, Team reviewer, Team target, int rating,
                        String comment) {
        reviewRepository.save(Review.builder()
                .request(request)
                .reviewerTeam(reviewer)
                .targetTeam(target)
                .rating(rating)
                .comment(comment)
                .build());
    }
}
