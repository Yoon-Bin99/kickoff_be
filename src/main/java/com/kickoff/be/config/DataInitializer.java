package com.kickoff.be.config;

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
 * 개발 프로파일 시드 데이터. FE 가 목록/필터 화면을 바로 붙여볼 수 있게
 * 지역·구장유형·실력수준을 골고루 섞는다. 비밀번호는 전부 pass1234.
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
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        Team saebyeok = createTeam("kim@example.com", "김주장", "010-1234-5678",
                "FC 새벽", "서울 강서구", "강서구민운동장",
                SkillLevel.INTERMEDIATE, AgeGroup.THIRTIES, 18,
                "매주 토요일 오전 7시에 모입니다. 매너 있는 경기 지향합니다.");

        Team mapo = createTeam("lee@example.com", "이감독", "010-2345-6789",
                "마포 유나이티드", "서울 마포구", "마포구립축구장",
                SkillLevel.ADVANCED, AgeGroup.TWENTIES, 22,
                "실력 있는 팀 환영합니다. 주말 오전 위주로 뜁니다.");

        Team songpa = createTeam("park@example.com", "박캡틴", "010-3456-7890",
                "송파 FC", "서울 송파구", "올림픽공원 축구장",
                SkillLevel.BEGINNER, AgeGroup.MIXED, 15,
                "이제 막 시작한 팀입니다. 즐겁게 뛰실 분들 구해요.");

        Team goyang = createTeam("choi@example.com", "최총무", "010-4567-8901",
                "고양 킥커스", "경기 고양시", "고양종합운동장 보조구장",
                SkillLevel.AMATEUR, AgeGroup.FORTIES, 20,
                "40대 위주 팀입니다. 부상 없는 경기가 최우선.");

        Team seongnam = createTeam("jung@example.com", "정주장", "010-5678-9012",
                "성남 레인저스", "경기 성남시", "탄천종합운동장",
                SkillLevel.INTERMEDIATE, AgeGroup.THIRTIES, 24,
                "매주 일요일 아침에 모입니다. 풋살, 11인제 다 합니다.");

        Team incheon = createTeam("yoon@example.com", "윤코치", "010-6789-0123",
                "인천 스트라이커즈", "인천 남동구", "남동체육관 풋살장",
                SkillLevel.ADVANCED, AgeGroup.FIFTIES_PLUS, 16,
                "연령대는 높지만 실력은 자신 있습니다.");

        OffsetDateTime base = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);

        List<MatchPost> posts = List.of(
                post(saebyeok, "토요일 아침 풋살 상대 구합니다",
                        "6인제로 2시간 뛸 팀 찾습니다. 매너 중요합니다.",
                        base.plusDays(2).with(LocalTime.of(7, 0)),
                        "강서구민운동장 A구장", "서울 강서구",
                        FieldType.FUTSAL, SkillLevel.INTERMEDIATE, 50000),

                post(saebyeok, "평일 저녁 11인제 한 판",
                        "수요일 저녁에 풀피치로 뛸 팀 구합니다. 조명 있습니다.",
                        base.plusDays(5).with(LocalTime.of(20, 0)),
                        "강서구민운동장 주경기장", "서울 강서구",
                        FieldType.SOCCER_11, null, 120000),

                post(mapo, "일요일 오전 9인제 매칭",
                        "실력 있는 팀 환영합니다. 심판비 포함 금액입니다.",
                        base.plusDays(3).with(LocalTime.of(9, 0)),
                        "마포구립축구장", "서울 마포구",
                        FieldType.SOCCER_9, SkillLevel.ADVANCED, 90000),

                post(mapo, "주말 풋살 정기전 상대 구해요",
                        "매주 하실 팀이면 더 좋습니다. 우선 한 번 붙어보시죠.",
                        base.plusDays(9).with(LocalTime.of(10, 0)),
                        "마포 실내풋살장 B", "서울 마포구",
                        FieldType.FUTSAL, null, 40000),

                post(songpa, "초보팀끼리 즐겁게 한 경기",
                        "입문 수준입니다. 살살 해주실 팀 찾아요. 커피 쏘겠습니다.",
                        base.plusDays(4).with(LocalTime.of(8, 0)),
                        "올림픽공원 축구장 2번", "서울 송파구",
                        FieldType.SOCCER_6, SkillLevel.BEGINNER, 30000),

                post(songpa, "송파 일요일 오후 6인제",
                        "오후 시간대 선호하는 팀 있으면 연락 주세요.",
                        base.plusDays(10).with(LocalTime.of(15, 0)),
                        "송파구민체육관 풋살장", "서울 송파구",
                        FieldType.SOCCER_6, null, 35000),

                post(goyang, "고양 토요일 오전 11인제",
                        "40대 위주 팀입니다. 비슷한 연령대면 더 좋습니다.",
                        base.plusDays(2).with(LocalTime.of(10, 0)),
                        "고양종합운동장 보조구장", "경기 고양시",
                        FieldType.SOCCER_11, SkillLevel.AMATEUR, 110000),

                post(goyang, "평일 낮 풋살 하실 팀",
                        "자영업자 팀이라 평일 낮이 편합니다. 같은 사정인 팀 환영.",
                        base.plusDays(7).with(LocalTime.of(14, 0)),
                        "일산 풋살파크 1구장", "경기 고양시",
                        FieldType.FUTSAL, SkillLevel.AMATEUR, 45000),

                post(seongnam, "성남 일요일 아침 9인제",
                        "탄천에서 뜁니다. 주차 편합니다.",
                        base.plusDays(3).with(LocalTime.of(7, 30)),
                        "탄천종합운동장 축구장", "경기 성남시",
                        FieldType.SOCCER_9, SkillLevel.INTERMEDIATE, 80000),

                post(seongnam, "다음 주 토요일 11인제 상대 구합니다",
                        "정기전 상대가 펑크나서 급하게 구합니다.",
                        base.plusDays(9).with(LocalTime.of(6, 30)),
                        "탄천종합운동장 주경기장", "경기 성남시",
                        FieldType.SOCCER_11, null, 130000),

                post(incheon, "인천 풋살 고수팀 구합니다",
                        "제대로 붙어볼 팀 찾습니다. 실력 자신 있는 팀만.",
                        base.plusDays(6).with(LocalTime.of(19, 0)),
                        "남동체육관 풋살장", "인천 남동구",
                        FieldType.FUTSAL, SkillLevel.ADVANCED, 60000),

                post(incheon, "인천 주말 6인제 친선경기",
                        "가볍게 몸 풀 겸 한 경기 하실 팀 구해요.",
                        base.plusDays(13).with(LocalTime.of(11, 0)),
                        "송도 축구장 3번", "인천 남동구",
                        FieldType.SOCCER_6, SkillLevel.BEGINNER, 25000)
        );

        postRepository.saveAll(posts);
        log.info("시드 데이터 생성 완료 — 사용자/팀 {}개, 모집글 {}개", teamRepository.count(), posts.size());
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

    private MatchPost post(Team team, String title, String content, OffsetDateTime matchAt,
                           String location, String region, FieldType fieldType,
                           SkillLevel preferredSkillLevel, Integer costPerTeam) {
        return MatchPost.builder()
                .team(team)
                .title(title)
                .content(content)
                .matchAt(matchAt)
                .location(location)
                .region(region)
                .fieldType(fieldType)
                .preferredSkillLevel(preferredSkillLevel)
                .costPerTeam(costPerTeam)
                .build();
    }
}
