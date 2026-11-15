package com.kickoff.be.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * 운영에서 시드를 켜면 기동이 막히는가 (공개 저장소 전제).
 *
 * 스프링을 띄우지 않는다 — 검사가 생성자 한 줄이고, 운영 프로파일로 컨텍스트를 올리려면
 * {@code DB_URL} 같은 것이 전부 필요해서 <b>엉뚱한 이유로 실패하는 테스트</b>가 된다.
 * 그러면 "막혔다"가 가드 때문인지 설정 때문인지 구별되지 않는다.
 */
class SeedDataGuardTest {

    /**
     * 이 한 줄이 이 클래스의 존재 이유다. 시드 계정의 비밀번호는 공개 저장소에 적혀 있고,
     * 운영에 들어가면 누구나 그 계정으로 로그인할 수 있다.
     */
    @Test
    @DisplayName("운영에서 시드를 켜면 기동을 거부한다")
    void productionWithSeedRefusesToStart() {
        assertThatThrownBy(() -> new SeedDataGuard(environment("prod", "true")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEED_DATA")
                // 무엇을 해야 하는지가 메시지에 있어야 한다 — 배포가 선 사람이 읽는 글이다
                .hasMessageContaining("다시 배포");
    }

    @Test
    @DisplayName("운영이라도 시드가 꺼져 있으면 통과한다")
    void productionWithoutSeedStarts() {
        assertThatCode(() -> new SeedDataGuard(environment("prod", "false")))
                .doesNotThrowAnyException();
    }

    /** 값을 아예 주지 않은 경우다. 기본값은 꺼짐이라 막을 이유가 없다. */
    @Test
    @DisplayName("설정이 없으면 통과한다")
    void productionWithoutPropertyStarts() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatCode(() -> new SeedDataGuard(environment)).doesNotThrowAnyException();
    }

    /**
     * 막는 대상은 운영뿐이다. 개발은 시드가 기본 on 이고(화면에 볼 것이 있어야 한다),
     * 테스트도 시드 플래그를 켜는 경우가 있다 — 여기서 함께 막으면 그쪽이 전부 선다.
     */
    @ParameterizedTest(name = "{0} 프로파일에서는 시드를 켜도 통과한다")
    @ValueSource(strings = {"dev", "test"})
    void otherProfilesAreUntouched(String profile) {
        assertThatCode(() -> new SeedDataGuard(environment(profile, "true")))
                .doesNotThrowAnyException();
    }

    /**
     * 프로파일을 아예 주지 않으면 운영이 아니다. <b>이쪽을 막으면 안 된다</b> — 프로파일
     * 없이 뜨는 경로는 dev 로 떨어지고, 거기서 시드는 정상 동작이다.
     */
    @Test
    @DisplayName("프로파일이 없으면 막지 않는다")
    void noProfileIsNotProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(SeedDataGuard.SEED_PROPERTY, "true");

        assertThatCode(() -> new SeedDataGuard(environment)).doesNotThrowAnyException();
    }

    private MockEnvironment environment(String profile, String seedData) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        environment.setProperty(SeedDataGuard.SEED_PROPERTY, seedData);
        return environment;
    }
}
