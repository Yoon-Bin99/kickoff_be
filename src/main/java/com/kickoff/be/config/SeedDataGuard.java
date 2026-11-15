package com.kickoff.be.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 운영에서 시드 데이터를 켜면 <b>기동을 거부한다.</b>
 *
 * <b>왜 코드로 막는가.</b> 시드 계정의 이메일과 공통 비밀번호가 이 저장소에 적혀 있고
 * (README 「시드 계정」), 저장소는 공개다. 그러니 운영에 시드가 들어가는 순간
 * <b>비밀번호를 누구나 아는 계정이 운영 DB 에 생긴다.</b> 환경변수 하나를 잘못 준 것이
 * 곧 계정 탈취가 되는 자리라, 문서로 "켜지 말 것"이라고만 적어 두기에는 대가가 너무 크다.
 *
 * <b>{@code DataInitializer} 는 프로파일을 보지 않는다</b> — {@code kickoff.seed-data}
 * 하나만 본다. 그래서 운영에서 {@code SEED_DATA=true} 를 주면 실제로 돈다. 팀·모집글만
 * 들어가는 것이 아니라 사용자 계정이 함께 들어가고, 시드가 전화번호를 들고 있어 발송
 * 경로가 닿으면 모르는 사람에게 문자가 나간 전력도 있다(그래서 국번 0000 을 쓴다).
 *
 * <b>건너뛰지 않고 기동을 막는 이유.</b> 건너뛰면 서비스는 멀쩡히 뜨고 ERROR 한 줄만
 * 남는데, 배포 직후 로그를 끝까지 읽는 사람은 드물다 — 잘못된 설정이 그대로 굳는다.
 * 반대로 기동을 막으면 <b>배포가 그 자리에서 선다.</b> 롤링 배포라 그동안 옛 인스턴스가
 * 계속 응답하므로, 정상 경로에서는 잃는 것이 없고 잘못된 배포만 멈춘다.
 *
 * 생성자에서 검사하는 것은 순서 때문이다. {@code DataInitializer} 는
 * {@code ApplicationRunner} 라 컨텍스트가 다 뜬 뒤에 도는데, 빈 생성은 그보다 먼저다 —
 * 시드가 한 줄이라도 들어가기 전에 멈춘다.
 *
 * <b>{@code @Value("${spring.profiles.active}")} 로 읽지 않는다.</b> 그 프로퍼티는
 * yaml·환경변수로 프로파일을 줄 때만 채워져서, {@code @ActiveProfiles} 로 띄우는 테스트는
 * 빈 값을 받는다. {@link Environment#matchesProfiles} 는 실제 활성 프로파일을 본다
 * ({@code SecurityConfig} 가 H2 콘솔에서 같은 이유로 같은 선택을 했다).
 */
@Component
public class SeedDataGuard {

    static final String SEED_PROPERTY = "kickoff.seed-data";

    /** 막는 대상은 운영뿐이다. dev·test 는 시드가 있어야 화면도 테스트도 돈다. */
    static final String PRODUCTION_PROFILE = "prod";

    public SeedDataGuard(Environment environment) {
        if (!environment.matchesProfiles(PRODUCTION_PROFILE)) {
            return;
        }
        if (Boolean.TRUE.equals(environment.getProperty(SEED_PROPERTY, Boolean.class, false))) {
            throw new IllegalStateException("""
                    운영(prod)에서는 시드 데이터를 켤 수 없습니다 — SEED_DATA=true 를 지우고 다시 배포하세요.

                    시드 계정의 이메일과 비밀번호는 공개 저장소의 README 에 적혀 있습니다. \
                    운영에 넣으면 누구나 그 계정으로 로그인할 수 있습니다.
                    화면에 보여 줄 예제 데이터가 필요하면 시드가 아니라 실제 계정으로 만드세요.""");
        }
    }
}
