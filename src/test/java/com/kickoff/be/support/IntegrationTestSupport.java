package com.kickoff.be.support;

import com.jayway.jsonpath.JsonPath;
import com.kickoff.be.auth.jwt.JwtTokenProvider;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.repository.MatchRequestRepository;
import com.kickoff.be.post.entity.FieldType;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.oauth.repository.SocialAccountRepository;
import com.kickoff.be.post.repository.MatchPostRepository;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.team.entity.AgeGroup;
import com.kickoff.be.team.entity.SkillLevel;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamAdminRepository;
import com.kickoff.be.team.repository.TeamJoinRequestRepository;
import com.kickoff.be.team.repository.TeamMemberRepository;
import com.kickoff.be.team.repository.TeamRecordRepository;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 통합 테스트 공통 기반.
 *
 * 테스트에 @Transactional 을 걸지 않는다. 수락 흐름처럼 여러 요청에 걸친 상태 전이를
 * 검증해야 하는데, 테스트 트랜잭션으로 감싸면 실제 운영과 커밋 시점이 달라진다.
 * 대신 매 테스트 전에 테이블을 비운다.
 *
 * <b>DB 는 둘 중 하나로 붙는다.</b> 기본은 H2(빠르고 Docker 가 필요 없다)이고,
 * {@code -Dkickoff.test.db=postgres} 를 주면 실제 PostgreSQL 컨테이너에 붙는다
 * ({@code ./gradlew postgresTest}). 같은 테스트를 두 DB 에서 돌리는 구조라 커버리지가
 * 벌어질 여지가 없다 — H2 가 통과시키는 SQL 을 PostgreSQL 이 거부하는 일이 실제로 있었다
 * (function lower(bytea) does not exist).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubOAuthConfig.class, StubPushConfig.class, StubPlaceConfig.class})
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "pass1234";
    protected static final String BANK = "카카오뱅크";
    protected static final String ACCOUNT_NUMBER = "3333-01-1234567";
    protected static final String ACCOUNT_HOLDER = "김주장";
    protected static final int DEPOSIT_AMOUNT = 50000;

    /** 운영과 같은 메이저 버전. Railway 의 PostgreSQL 16 에 맞춘다. */
    private static final String POSTGRES_IMAGE = "postgres:16-alpine";

    private static PostgreSQLContainer postgres;

    /**
     * PostgreSQL 로 돌리라고 지시받았을 때만 컨테이너를 띄운다. 지시가 없으면 아무것도 하지
     * 않으므로 application-test.yaml 의 H2 설정이 그대로 쓰인다.
     *
     * 컨테이너는 static 이라 JVM 당 한 번만 뜨고 모든 테스트 클래스가 공유한다. 클래스마다
     * 새로 띄우면 스위트 시간이 몇 배가 된다. 정리는 Testcontainers 의 Ryuk 이 맡는다.
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (!"postgres".equals(System.getProperty("kickoff.test.db"))) {
            return;
        }
        if (postgres == null) {
            postgres = new PostgreSQLContainer(POSTGRES_IMAGE);
            postgres.start();
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected TeamRepository teamRepository;
    @Autowired
    protected TeamAdminRepository teamAdminRepository;
    @Autowired
    protected TeamJoinRequestRepository teamJoinRequestRepository;
    @Autowired
    protected TeamMemberRepository teamMemberRepository;
    @Autowired
    protected TeamRecordRepository teamRecordRepository;
    @Autowired
    protected MatchPostRepository postRepository;
    @Autowired
    protected MatchRequestRepository requestRepository;
    @Autowired
    protected ReviewRepository reviewRepository;
    @Autowired
    protected SocialAccountRepository socialAccountRepository;
    @Autowired
    protected StubOAuthClient kakaoStub;
    @Autowired
    protected StubOAuthClient naverStub;
    @Autowired
    protected StubPushClient pushClient;
    @Autowired
    protected StubPlaceSearchClient placeSearchClient;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtTokenProvider tokenProvider;

    @BeforeEach
    void resetDatabase() {
        // 외래키 순서대로 — 리뷰가 신청을, 신청이 글을, 글이 팀을, 팀이 사용자를 참조한다.
        // 경기 기록은 v1.10.0 부터 신청도 참조하므로 신청보다 먼저 지운다.
        reviewRepository.deleteAll();
        teamRecordRepository.deleteAll();
        requestRepository.deleteAll();
        postRepository.deleteAll();
        // 팀 페이지 자식들 (v1.8.0·v1.9.0). 팀보다 먼저 지워야 한다 — 특히 team_admins 는
        // 사용자도 참조해서, 빠뜨리면 팀·사용자 삭제가 참조 무결성 위반으로 터진다.
        teamMemberRepository.deleteAll();
        teamAdminRepository.deleteAll();
        // 가입 신청은 팀과 사용자를 함께 참조한다 (v1.11.0)
        teamJoinRequestRepository.deleteAll();
        teamRepository.deleteAll();
        // 소셜 연동은 사용자를 참조하므로 사용자보다 먼저 지운다
        socialAccountRepository.deleteAll();
        userRepository.deleteAll();
        kakaoStub.reset();
        naverStub.reset();
        pushClient.reset();
        placeSearchClient.reset();
    }

    /** 소셜 가입 직후처럼 전화번호가 없는 사용자. */
    protected User createUserWithoutPhone(String email, String nickname) {
        return createUser(email, nickname, null);
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

    /**
     * 지난 경기의 수락된 매칭. 리뷰 조건을 만들려면 이 방법뿐이다 —
     * 지난 경기 글에는 API 로 신청을 넣을 수 없어서(계약서 §5) 엔티티로 직접 만든다.
     */
    protected MatchRequest acceptedRequest(MatchPost post, Team applicantTeam) {
        MatchRequest request = pendingRequest(post, applicantTeam);
        request.accept();
        post.markMatched();
        postRepository.save(post);
        return requestRepository.save(request);
    }

    protected MatchRequest pendingRequest(MatchPost post, Team applicantTeam) {
        return requestRepository.save(MatchRequest.builder()
                .post(post)
                .applicantTeam(applicantTeam)
                .message(applicantTeam.getName() + " 신청합니다")
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
