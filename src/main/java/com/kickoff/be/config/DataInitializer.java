package com.kickoff.be.config;

import com.kickoff.be.matchrequest.MatchRequest;
import com.kickoff.be.matchrequest.MatchRequestRepository;
import com.kickoff.be.post.FieldType;
import com.kickoff.be.post.MatchPost;
import com.kickoff.be.post.MatchPostRepository;
import com.kickoff.be.team.AgeGroup;
import com.kickoff.be.team.SkillLevel;
import com.kickoff.be.team.Team;
import com.kickoff.be.team.TeamRepository;
import com.kickoff.be.user.User;
import com.kickoff.be.user.UserRepository;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발 프로파일 시드 데이터. FE 가 목록/필터/무한스크롤/매칭·입금 화면을 바로 붙여볼 수 있게
 * 팀 6개, 모집글 30개, 신청 몇 건을 넣는다. 비밀번호는 전부 pass1234.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final String RAW_PASSWORD = "pass1234";

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final MatchPostRepository postRepository;
    private final MatchRequestRepository requestRepository;
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

        Team saebyeok = createTeam("kim@example.com", "김주장", "010-1234-5678",
                "FC 새벽", "서울 강서구", "강서구민운동장",
                SkillLevel.INTERMEDIATE, AgeGroup.THIRTIES, 18,
                "매주 토요일 오전 7시에 모입니다. 매너 있는 경기 지향합니다.");
        SeedAccount kimAcc = new SeedAccount("카카오뱅크", "3333-01-1234567", "김주장");

        Team mapo = createTeam("lee@example.com", "이감독", "010-2345-6789",
                "마포 유나이티드", "서울 마포구", "마포구립축구장",
                SkillLevel.ADVANCED, AgeGroup.TWENTIES, 22,
                "실력 있는 팀 환영합니다. 주말 오전 위주로 뜁니다.");
        SeedAccount leeAcc = new SeedAccount("신한은행", "110-234-567890", "이감독");

        Team songpa = createTeam("park@example.com", "박캡틴", "010-3456-7890",
                "송파 FC", "서울 송파구", "올림픽공원 축구장",
                SkillLevel.BEGINNER, AgeGroup.MIXED, 15,
                "이제 막 시작한 팀입니다. 즐겁게 뛰실 분들 구해요.");
        SeedAccount parkAcc = new SeedAccount("국민은행", "123456-04-567890", "박캡틴");

        Team goyang = createTeam("choi@example.com", "최총무", "010-4567-8901",
                "고양 킥커스", "경기 고양시", "고양종합운동장 보조구장",
                SkillLevel.AMATEUR, AgeGroup.FORTIES, 20,
                "40대 위주 팀입니다. 부상 없는 경기가 최우선.");
        SeedAccount choiAcc = new SeedAccount("우리은행", "1002-345-678901", "최총무");

        Team seongnam = createTeam("jung@example.com", "정주장", "010-5678-9012",
                "성남 레인저스", "경기 성남시", "탄천종합운동장",
                SkillLevel.INTERMEDIATE, AgeGroup.THIRTIES, 24,
                "매주 일요일 아침에 모입니다. 풋살, 11인제 다 합니다.");
        SeedAccount jungAcc = new SeedAccount("하나은행", "352-0123-4567-89", "정주장");

        Team incheon = createTeam("yoon@example.com", "윤코치", "010-6789-0123",
                "인천 스트라이커즈", "인천 남동구", "남동체육관 풋살장",
                SkillLevel.ADVANCED, AgeGroup.FIFTIES_PLUS, 16,
                "연령대는 높지만 실력은 자신 있습니다.");
        SeedAccount yoonAcc = new SeedAccount("토스뱅크", "1000-1234-5678", "윤코치");

        // account 가 null 인 글은 depositAmount 도 null 인 "협의" 케이스.
        // dayOffset 이 음수면 이미 지난 경기다.
        List<MatchPost> posts = List.of(
                post(saebyeok, kimAcc, "토요일 아침 풋살 상대 구합니다",
                        "6인제로 2시간 뛸 팀 찾습니다. 매너 중요합니다.",
                        2, LocalTime.of(7, 0), "강서구민운동장 A구장", "서울 강서구",
                        FieldType.FUTSAL, SkillLevel.INTERMEDIATE, 100000, 50000),
                post(saebyeok, kimAcc, "평일 저녁 11인제 한 판",
                        "수요일 저녁에 풀피치로 뛸 팀 구합니다. 조명 있습니다.",
                        5, LocalTime.of(20, 0), "강서구민운동장 주경기장", "서울 강서구",
                        FieldType.SOCCER_11, null, 240000, 120000),
                post(saebyeok, null, "강서 일요일 9인제 협의",
                        "비용은 만나서 반반으로 협의하시죠.",
                        11, LocalTime.of(9, 0), "강서구민운동장 B구장", "서울 강서구",
                        FieldType.SOCCER_9, SkillLevel.AMATEUR, 150000, null),
                post(saebyeok, kimAcc, "강서 토요일 오후 6인제",
                        "오후 시간대도 괜찮으신 팀 구합니다.",
                        16, LocalTime.of(16, 0), "화곡 풋살파크", "서울 강서구",
                        FieldType.SOCCER_6, SkillLevel.BEGINNER, 60000, 30000),
                post(saebyeok, kimAcc, "지난 주 강서 풋살 (상대 못 구한 글)",
                        "날짜가 지났는데 아직 모집중인 글입니다. 목록 정렬 확인용.",
                        -4, LocalTime.of(7, 0), "강서구민운동장 A구장", "서울 강서구",
                        FieldType.FUTSAL, SkillLevel.INTERMEDIATE, 90000, 45000),

                post(mapo, leeAcc, "일요일 오전 9인제 매칭",
                        "실력 있는 팀 환영합니다. 심판비 포함 금액입니다.",
                        3, LocalTime.of(9, 0), "마포구립축구장", "서울 마포구",
                        FieldType.SOCCER_9, SkillLevel.ADVANCED, 180000, 90000),
                post(mapo, null, "주말 풋살 정기전 상대 구해요",
                        "매주 하실 팀이면 더 좋습니다. 비용은 협의합니다.",
                        9, LocalTime.of(10, 0), "마포 실내풋살장 B", "서울 마포구",
                        FieldType.FUTSAL, null, 80000, null),
                post(mapo, leeAcc, "마포 평일 야간 11인제",
                        "퇴근하고 바로 오실 수 있는 팀이면 좋겠습니다.",
                        6, LocalTime.of(21, 0), "상암 보조구장", "서울 마포구",
                        FieldType.SOCCER_11, SkillLevel.ADVANCED, 260000, 130000),
                post(mapo, leeAcc, "마포 6인제 가볍게",
                        "부담 없이 한 경기 하실 팀.",
                        14, LocalTime.of(11, 0), "마포 실내풋살장 A", "서울 마포구",
                        FieldType.SOCCER_6, SkillLevel.INTERMEDIATE, 70000, 35000),
                post(mapo, leeAcc, "마포 주말 9인제 추가 모집",
                        "한 팀 더 구합니다.",
                        19, LocalTime.of(8, 0), "마포구립축구장", "서울 마포구",
                        FieldType.SOCCER_9, null, 170000, 85000),

                post(songpa, parkAcc, "초보팀끼리 즐겁게 한 경기",
                        "입문 수준입니다. 살살 해주실 팀 찾아요. 커피 쏘겠습니다.",
                        4, LocalTime.of(8, 0), "올림픽공원 축구장 2번", "서울 송파구",
                        FieldType.SOCCER_6, SkillLevel.BEGINNER, 60000, 30000),
                post(songpa, parkAcc, "송파 일요일 오후 6인제",
                        "오후 시간대 선호하는 팀 있으면 연락 주세요.",
                        10, LocalTime.of(15, 0), "송파구민체육관 풋살장", "서울 송파구",
                        FieldType.SOCCER_6, null, 70000, 35000),
                post(songpa, parkAcc, "송파 풋살 초보 환영",
                        "저희도 못합니다. 편하게 오세요.",
                        7, LocalTime.of(19, 0), "잠실 풋살장", "서울 송파구",
                        FieldType.FUTSAL, SkillLevel.BEGINNER, 50000, 25000),
                post(songpa, null, "송파 11인제 도전",
                        "인원 모아서 처음 11인제 해봅니다. 비용 협의.",
                        17, LocalTime.of(10, 0), "올림픽공원 축구장 1번", "서울 송파구",
                        FieldType.SOCCER_11, null, 200000, null),
                post(songpa, parkAcc, "지난 주말 송파 6인제 (종료)",
                        "지난 경기입니다.",
                        -8, LocalTime.of(9, 0), "올림픽공원 축구장 2번", "서울 송파구",
                        FieldType.SOCCER_6, SkillLevel.BEGINNER, 55000, 28000),

                post(goyang, choiAcc, "고양 토요일 오전 11인제",
                        "40대 위주 팀입니다. 비슷한 연령대면 더 좋습니다.",
                        2, LocalTime.of(10, 0), "고양종합운동장 보조구장", "경기 고양시",
                        FieldType.SOCCER_11, SkillLevel.AMATEUR, 220000, 110000),
                post(goyang, choiAcc, "평일 낮 풋살 하실 팀",
                        "자영업자 팀이라 평일 낮이 편합니다. 같은 사정인 팀 환영.",
                        7, LocalTime.of(14, 0), "일산 풋살파크 1구장", "경기 고양시",
                        FieldType.FUTSAL, SkillLevel.AMATEUR, 90000, 45000),
                post(goyang, choiAcc, "고양 일요일 9인제",
                        "일요일 아침에 뜁니다.",
                        13, LocalTime.of(8, 0), "고양종합운동장 보조구장", "경기 고양시",
                        FieldType.SOCCER_9, SkillLevel.INTERMEDIATE, 160000, 80000),
                post(goyang, choiAcc, "일산 6인제 친선",
                        "가볍게 한 경기 하실 팀 구합니다.",
                        20, LocalTime.of(17, 0), "일산 풋살파크 2구장", "경기 고양시",
                        FieldType.SOCCER_6, null, 65000, 32000),
                post(goyang, null, "고양 11인제 비용 협의",
                        "구장은 저희가 잡았습니다. 비용은 이야기해보시죠.",
                        15, LocalTime.of(11, 0), "고양종합운동장 주경기장", "경기 고양시",
                        FieldType.SOCCER_11, SkillLevel.ADVANCED, 230000, null),

                post(seongnam, jungAcc, "성남 일요일 아침 9인제",
                        "탄천에서 뜁니다. 주차 편합니다.",
                        3, LocalTime.of(7, 30), "탄천종합운동장 축구장", "경기 성남시",
                        FieldType.SOCCER_9, SkillLevel.INTERMEDIATE, 160000, 80000),
                post(seongnam, null, "다음 주 토요일 11인제 상대 구합니다",
                        "정기전 상대가 펑크나서 급하게 구합니다. 비용은 반반 협의.",
                        9, LocalTime.of(6, 30), "탄천종합운동장 주경기장", "경기 성남시",
                        FieldType.SOCCER_11, null, 260000, null),
                post(seongnam, jungAcc, "성남 평일 저녁 풋살",
                        "퇴근 후 가볍게 뛰실 팀.",
                        6, LocalTime.of(20, 30), "분당 풋살장", "경기 성남시",
                        FieldType.FUTSAL, SkillLevel.INTERMEDIATE, 80000, 40000),
                post(seongnam, jungAcc, "성남 주말 6인제",
                        "6인제로 두 시간 뜁니다.",
                        12, LocalTime.of(13, 0), "야탑 풋살파크", "경기 성남시",
                        FieldType.SOCCER_6, SkillLevel.AMATEUR, 75000, 38000),
                post(seongnam, jungAcc, "성남 매칭 완료된 경기",
                        "이미 상대가 정해진 글입니다. FE 뱃지 확인용.",
                        8, LocalTime.of(9, 0), "탄천종합운동장 축구장", "경기 성남시",
                        FieldType.SOCCER_9, SkillLevel.INTERMEDIATE, 170000, 85000),

                post(incheon, yoonAcc, "인천 풋살 고수팀 구합니다",
                        "제대로 붙어볼 팀 찾습니다. 실력 자신 있는 팀만.",
                        6, LocalTime.of(19, 0), "남동체육관 풋살장", "인천 남동구",
                        FieldType.FUTSAL, SkillLevel.ADVANCED, 120000, 60000),
                post(incheon, yoonAcc, "인천 주말 6인제 친선경기",
                        "가볍게 몸 풀 겸 한 경기 하실 팀 구해요.",
                        13, LocalTime.of(11, 0), "송도 축구장 3번", "인천 남동구",
                        FieldType.SOCCER_6, SkillLevel.BEGINNER, 50000, 25000),
                post(incheon, yoonAcc, "인천 11인제 정기전 상대",
                        "매달 한 번씩 하실 팀이면 좋겠습니다.",
                        18, LocalTime.of(8, 0), "인천축구전용경기장 보조", "인천 남동구",
                        FieldType.SOCCER_11, SkillLevel.ADVANCED, 250000, 125000),
                post(incheon, yoonAcc, "인천 9인제 매칭 완료",
                        "상대 정해졌고 입금까지 확인된 글입니다.",
                        10, LocalTime.of(10, 0), "남동 아시아드 보조구장", "인천 남동구",
                        FieldType.SOCCER_9, SkillLevel.INTERMEDIATE, 180000, 90000),
                post(incheon, yoonAcc, "지난 주 인천 풋살 (종료)",
                        "지난 경기입니다.",
                        -2, LocalTime.of(19, 0), "남동체육관 풋살장", "인천 남동구",
                        FieldType.FUTSAL, SkillLevel.ADVANCED, 110000, 55000)
        );
        postRepository.saveAll(posts);

        // 지난 경기 중 둘은 마감 처리 — 목록 기본 필터(OPEN)에서 빠진다.
        // posts.get(4)는 일부러 OPEN 으로 남긴다. 아무도 매칭하지 않은 채 날짜가 지난 글이라
        // matchAt 오름차순 정렬에서 맨 위로 올라온다. FE 가 실제로 만나는 상황이라 시드에 포함한다.
        posts.get(14).close();
        posts.get(29).close();

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

        long teamCount = teamRepository.count();
        long postCount = postRepository.count();
        long requestCount = requestRepository.count();
        // 여기까지 왔는데 비어 있으면 빈 DB 로 서비스하느니 기동을 실패시킨다
        if (teamCount == 0 || postCount == 0) {
            throw new IllegalStateException(
                    "시드 데이터가 저장되지 않았다 — 팀 %d, 모집글 %d".formatted(teamCount, postCount));
        }
        long openCount = posts.stream().filter(MatchPost::isOpen).count();
        log.info("시드 데이터 생성 완료 — 팀 {}개, 모집글 {}개(OPEN {}개), 신청 {}건",
                teamCount, postCount, openCount, requestCount);
    }

    private Team createTeam(String email, String nickname, String phone, String teamName,
                            String region, String homeGround, SkillLevel skillLevel,
                            AgeGroup ageGroup, int memberCount, String introduction) {
        User owner = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(RAW_PASSWORD))
                .nickname(nickname)
                .phone(phone)
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

    private MatchPost post(Team team, SeedAccount account, String title, String content,
                           int dayOffset, LocalTime time, String location, String region,
                           FieldType fieldType, SkillLevel preferredSkillLevel,
                           Integer rentalFee, Integer depositAmount) {
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
                .fieldType(fieldType)
                .preferredSkillLevel(preferredSkillLevel)
                .rentalFee(rentalFee)
                .depositAmount(depositAmount)
                .bankName(account == null ? null : account.bankName())
                .accountNumber(account == null ? null : account.accountNumber())
                .accountHolder(account == null ? null : account.accountHolder())
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
    private void matchWith(MatchPost post, Team applicant, String message, boolean depositPaid) {
        MatchRequest accepted = applyPending(post, applicant, message);
        accepted.accept();
        if (depositPaid) {
            accepted.confirmDeposit();
        }
        post.markMatched();
    }
}
