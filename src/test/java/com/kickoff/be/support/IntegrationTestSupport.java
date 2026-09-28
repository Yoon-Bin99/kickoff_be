package com.kickoff.be.support;

import com.jayway.jsonpath.JsonPath;
import com.kickoff.be.auth.jwt.JwtTokenProvider;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.repository.MatchRequestRepository;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.oauth.repository.SocialAccountRepository;
import com.kickoff.be.post.repository.MatchPostRepository;
import com.kickoff.be.chat.repository.ChatLeaveRepository;
import com.kickoff.be.chat.repository.ChatMessageRepository;
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
import com.kickoff.be.support.repository.SupportMessageRepository;
import com.kickoff.be.support.repository.SupportRoomRepository;
import com.kickoff.be.user.repository.UserRepository;
import com.kickoff.be.verification.repository.PhoneVerificationRepository;
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
@Import({StubOAuthConfig.class, StubPushConfig.class, StubPlaceConfig.class,
        StubSmsConfig.class, StubAiSupportConfig.class, StubEmailConfig.class})
public abstract class IntegrationTestSupport {

    /**
     * 테스트 계정의 비밀번호. v1.16.0 부터 가입 규칙이 영문·숫자·특수문자를 요구하므로
     * 느낌표가 붙어 있다 — 이걸로 가입한 뒤 같은 값으로 로그인하는 흐름이 대부분이다.
     *
     * dev 시드(DataInitializer)의 pass1234 는 그대로 둔다. 규칙은 가입에만 걸리고 기존
     * 계정에 소급하지 않으므로, 시드가 규칙을 어겨도 로그인은 된다 — 그 성질 자체를
     * SignupHardeningTest 가 검증한다.
     */
    protected static final String PASSWORD = "pass1234!";
    protected static final String BANK = "카카오뱅크";
    protected static final String ACCOUNT_NUMBER = "3333-01-1234567";
    protected static final String ACCOUNT_HOLDER = "김주장";
    protected static final int DEPOSIT_AMOUNT = 50000;

    /**
     * 운영과 같은 메이저 버전 <b>그리고 같은 libc</b>. Railway 의 PostgreSQL 16 에 맞춘다.
     *
     * <b>alpine 이 아니라 debian 이어야 한다.</b> 예전에는 {@code postgres:16-alpine} 을 썼는데,
     * alpine 은 musl libc 라 로케일 콜레이션이 사실상 코드포인트 순서다. 운영(glibc)은
     * 같은 이름의 {@code en_US.utf8} 로도 <b>한글을 다르게 정렬한다</b> — glibc 의 다단계
     * 가중치 비교에서 한글은 en_US 규칙에 없어 상위 레벨이 무시되고, 첫 글자가 순서를
     * 결정하지 않는다.
     *
     * 이걸로 v1.27.0 배포에서 실제로 당했다. 구장 목록 정렬 테스트가 H2 와 alpine
     * PostgreSQL 양쪽에서 통과했는데 운영에서만 순서가 엉켰고, 두 인천 구장이 경기 구장들을
     * 사이에 두고 갈라져 나왔다. <b>테스트가 잡을 수 없는 차이였다</b> — 하니스가 운영과
     * 다른 libc 를 쓰고 있었기 때문이다. 이미지를 되돌리지 말 것.
     */
    private static final String POSTGRES_IMAGE = "postgres:16";

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
    protected ChatMessageRepository chatMessageRepository;
    @Autowired
    protected ChatLeaveRepository chatLeaveRepository;
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
    protected StubSmsClient smsClient;
    @Autowired
    protected StubAiSupportClient aiClient;
    @Autowired
    protected StubEmailClient emailClient;
    @Autowired
    protected com.kickoff.be.passwordreset.repository.PasswordResetCodeRepository
            passwordResetCodeRepository;
    @jakarta.persistence.PersistenceContext
    protected jakarta.persistence.EntityManager entityManager;
    /**
     * 테스트에 트랜잭션이 없어서(수락 흐름처럼 여러 요청에 걸친 상태 전이를 보려고 일부러
     * 뺐다) 벌크 update 를 직접 돌리려면 이게 필요하다.
     */
    @Autowired
    protected org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    @Autowired
    protected SupportMessageRepository supportMessageRepository;
    @Autowired
    protected SupportRoomRepository supportRoomRepository;
    @Autowired
    protected PhoneVerificationRepository phoneVerificationRepository;
    @Autowired
    protected com.kickoff.be.stadium.repository.StadiumRepository stadiumRepository;
    @Autowired
    protected com.kickoff.be.verification.service.SmsDispatchQuota smsDispatchQuota;
    @Autowired
    protected com.kickoff.be.auth.service.LoginAttemptLimiter loginAttemptLimiter;
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
        // 채팅은 신청과 팀을 참조한다 (v1.12.0, 나가기 상태는 v1.13.0)
        chatLeaveRepository.deleteAll();
        // 고객센터는 사용자만 참조한다 (v1.22.0). 메시지가 방보다 먼저다.
        supportMessageRepository.deleteAll();
        supportRoomRepository.deleteAll();
        // 비밀번호 재설정 요청 (v1.23.0). 아무것도 참조하지 않지만 <b>반드시 비워야 한다</b> —
        // 레이트리밋이 이 표의 행 수로 판정하므로, 남아 있으면 다음 테스트의 첫 요청이
        // 429 로 막힌다.
        passwordResetCodeRepository.deleteAll();
        chatMessageRepository.deleteAll();
        requestRepository.deleteAll();
        postRepository.deleteAll();
        // 팀 페이지 자식들 (v1.8.0·v1.9.0). 팀보다 먼저 지워야 한다 — 특히 team_admins 는
        // 사용자도 참조해서, 빠뜨리면 팀·사용자 삭제가 참조 무결성 위반으로 터진다.
        teamMemberRepository.deleteAll();
        teamAdminRepository.deleteAll();
        // 가입 신청은 팀과 사용자를 함께 참조한다 (v1.11.0)
        teamJoinRequestRepository.deleteAll();
        teamRepository.deleteAll();
        // 전화번호 인증은 아무것도 참조하지 않지만, 레이트리밋이 남으면 다음 테스트의
        // 발송이 429 로 막힌다 (v1.15.0)
        phoneVerificationRepository.deleteAll();
        // 구장은 아무것도 참조하지 않지만, 마이그레이션 시드(V20)가 들어 있어 비워야 한다 —
        // 안 비우면 목록 테스트가 자기가 넣지 않은 5건을 같이 세게 된다 (v1.27.0).
        stadiumRepository.deleteAll();
        // 소셜 연동은 사용자를 참조하므로 사용자보다 먼저 지운다
        socialAccountRepository.deleteAll();
        userRepository.deleteAll();
        kakaoStub.reset();
        naverStub.reset();
        pushClient.reset();
        placeSearchClient.reset();
        smsClient.reset();
        // 인메모리 발송 쿼터도 비운다. 싱글턴이라 테스트끼리 카운터를 공유하고,
        // MockMvc 요청은 전부 같은 주소로 보여 한 IP 로 묶인다 — 안 비우면 문자를 세 번
        // 보낸 뒤의 모든 테스트가 429 를 받는다.
        smsDispatchQuota.reset();
        loginAttemptLimiter.resetAll();
    }

    /** 소셜 가입 직후처럼 전화번호가 없는 사용자. */
    /**
     * 이메일 없이 만든다 — 소셜로만 가입한 계정의 모양이다 (계약서 §3-1).
     * 비밀번호도 없다: 소셜 계정은 비밀번호 로그인을 하지 않는다.
     */
    protected User createUserWithoutEmail(String nickname, String phone) {
        return userRepository.save(User.builder()
                .nickname(nickname)
                .phone(phone)
                .build());
    }

    protected User createUserWithoutPhone(String email, String nickname) {
        return createUser(email, nickname, null);
    }

    protected User createUser(String email, String nickname, String phone) {
        return createUserWithPassword(email, nickname, phone, PASSWORD);
    }

    /**
     * 비밀번호를 지정해 만든다. 가입 API 를 거치지 않으므로 v1.16.0 의 비밀번호 규칙을
     * 타지 않는다 — "규칙을 어기는 기존 계정"을 만들어 로그인이 되는지 보는 데 쓴다.
     */
    protected User createUserWithPassword(String email, String nickname, String phone,
                                          String rawPassword) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
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

    /** 시각과 지역을 지정해 만든다 — 날짜·시간대 필터(§5, v1.18.0) 검증용. */
    protected MatchPost createPostIn(Team team, String title, OffsetDateTime matchAt,
                                     String region) {
        return postRepository.save(MatchPost.builder()
                .team(team)
                .title(title)
                .content(title + " 내용입니다.")
                .matchAt(matchAt)
                .location(region + " 구장")
                .region(region)
                .preferredSkillLevel(SkillLevel.INTERMEDIATE)
                .rentalFee(100000)
                .build());
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
