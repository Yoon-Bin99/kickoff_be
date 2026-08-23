package com.kickoff.be.support;

import com.jayway.jsonpath.JsonPath;
import com.kickoff.be.auth.JwtTokenProvider;
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
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * 통합 테스트 공통 기반.
 *
 * 테스트에 @Transactional 을 걸지 않는다. 수락 흐름처럼 여러 요청에 걸친 상태 전이를
 * 검증해야 하는데, 테스트 트랜잭션으로 감싸면 실제 운영과 커밋 시점이 달라진다.
 * 대신 매 테스트 전에 테이블을 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "pass1234";
    protected static final String BANK = "카카오뱅크";
    protected static final String ACCOUNT_NUMBER = "3333-01-1234567";
    protected static final String ACCOUNT_HOLDER = "김주장";
    protected static final int DEPOSIT_AMOUNT = 50000;

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected TeamRepository teamRepository;
    @Autowired
    protected MatchPostRepository postRepository;
    @Autowired
    protected MatchRequestRepository requestRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtTokenProvider tokenProvider;

    @BeforeEach
    void resetDatabase() {
        // 외래키 순서대로 — 신청이 글을, 글이 팀을, 팀이 사용자를 참조한다
        requestRepository.deleteAll();
        postRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected User createUser(String email, String nickname, String phone) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .nickname(nickname)
                .phone(phone)
                .build());
    }

    protected Team createTeam(User owner, String teamName, String region) {
        return teamRepository.save(Team.builder()
                .owner(owner)
                .name(teamName)
                .region(region)
                .homeGround(region + " 운동장")
                .skillLevel(SkillLevel.INTERMEDIATE)
                .ageGroup(AgeGroup.THIRTIES)
                .memberCount(15)
                .introduction(teamName + " 소개")
                .build());
    }

    /** 계좌가 붙은 미래 경기 글. */
    protected MatchPost createPost(Team team, String title) {
        return createPost(team, title, OffsetDateTime.now().plusDays(7), true);
    }

    protected MatchPost createPost(Team team, String title, OffsetDateTime matchAt,
                                   boolean withAccount) {
        return postRepository.save(MatchPost.builder()
                .team(team)
                .title(title)
                .content(title + " 내용입니다.")
                .matchAt(matchAt)
                .location(team.getRegion() + " 구장")
                .region(team.getRegion())
                .fieldType(FieldType.FUTSAL)
                .preferredSkillLevel(SkillLevel.INTERMEDIATE)
                .rentalFee(100000)
                .depositAmount(withAccount ? DEPOSIT_AMOUNT : null)
                .bankName(withAccount ? BANK : null)
                .accountNumber(withAccount ? ACCOUNT_NUMBER : null)
                .accountHolder(withAccount ? ACCOUNT_HOLDER : null)
                .build());
    }

    protected String bearer(User user) {
        return "Bearer " + tokenProvider.createToken(user.getId());
    }

    protected String bodyOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected long idOf(ResultActions actions) throws Exception {
        return ((Number) JsonPath.read(bodyOf(actions), "$.id")).longValue();
    }
}
