package com.kickoff.be.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * V18 데이터 마이그레이션 — 시드 계정의 실번호를 도달 불가 번호로 바꾼다.
 *
 * <b>마이그레이션 파일을 그대로 읽어 실행한다.</b> SQL 을 테스트에 베껴 쓰면 둘이 갈라지는
 * 순간 이 테스트가 아무것도 안 지키게 된다 — 진짜로 배포되는 문장이 무엇인지가 검증
 * 대상이라 사본을 두지 않는다. 덤으로 그 문장이 지금 DB 방언에서 도는지도 함께 확인된다
 * (H2 는 기본, PostgreSQL 은 {@code ./gradlew postgresTest}).
 *
 * 테스트가 뜰 때 Flyway 는 이미 V18 까지 적용한 뒤다. 그때는 테이블이 비어 있어 0행이므로,
 * 여기서는 <b>바꿀 것이 있는 상태를 만들어 놓고 같은 문장을 다시 돌린다.</b> 멱등이라
 * 그래도 된다.
 */
class SeedPhoneMigrationTest extends IntegrationTestSupport {

    private static final String MIGRATION = "db/migration/V18__fix_seed_phones.sql";

    @Test
    @DisplayName("시드 계정의 옛 번호가 도달 불가 번호로 바뀐다")
    void rewritesSeedPhones() {
        createUser("kim@example.com", "김주장", "010-1234-5678");
        createUser("lee@example.com", "이감독", "010-2345-6789");
        User untouched = createUser("someone@example.com", "남", "010-7777-1234");

        runMigration();

        assertThat(phoneOf("kim@example.com")).isEqualTo("010-0000-0001");
        assertThat(phoneOf("lee@example.com")).isEqualTo("010-0000-0002");
        assertThat(userRepository.findById(untouched.getId()).orElseThrow().getPhone())
                .as("시드가 아닌 계정은 건드리지 않는다")
                .isEqualTo("010-7777-1234");
    }

    /** 소셜 시드 계정은 이메일이 없어 닉네임으로 특정한다 (소셜은 email 을 저장하지 않는다). */
    @Test
    @DisplayName("이메일 없는 소셜 시드 계정도 닉네임으로 잡힌다")
    void rewritesSocialSeedPhone() {
        User social = createUserWithoutEmail("카카오가입자", "010-7890-1234");

        runMigration();

        assertThat(userRepository.findById(social.getId()).orElseThrow().getPhone())
                .isEqualTo("010-0000-0007");
    }

    /**
     * <b>가드가 없으면 배포가 죽는다.</b> users.phone 은 V13 에서 유니크가 걸려 있어,
     * 목표 번호를 이미 다른 계정이 쓰고 있으면 UPDATE 가 제약 위반으로 실패하고 Flyway 가
     * 멈춘다. 데이터 마이그레이션은 어떤 상태의 DB 에서 돌지 알 수 없으므로 실패하지 않는
     * 쪽이 맞다 — 그 줄만 건너뛴다.
     */
    @Test
    @DisplayName("목표 번호를 이미 다른 계정이 쓰고 있으면 그 줄만 건너뛴다")
    void skipsWhenTargetPhoneIsTaken() {
        createUser("kim@example.com", "김주장", "010-1234-5678");
        createUser("occupant@example.com", "선점자", "010-0000-0001");

        runMigration();

        assertThat(phoneOf("kim@example.com"))
                .as("바꾸지 못하고 옛 번호로 남는다 — 배포가 죽는 것보다 낫다")
                .isEqualTo("010-1234-5678");
        assertThat(phoneOf("occupant@example.com")).isEqualTo("010-0000-0001");
    }

    /** 두 번 돌려도 결과가 같아야 한다. 이미 적용된 DB 에서 다시 도는 경우가 있다. */
    @Test
    @DisplayName("여러 번 돌려도 같다 — 멱등")
    void isIdempotent() {
        createUser("kim@example.com", "김주장", "010-1234-5678");

        runMigration();
        runMigration();

        assertThat(phoneOf("kim@example.com")).isEqualTo("010-0000-0001");
    }

    private String phoneOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getPhone();
    }

    /**
     * 파일의 문장을 순서대로 실행한다. 주석 줄을 걷어내고 {@code ;} 로 나눈다 —
     * 이 마이그레이션에는 문자열 리터럴 안의 세미콜론이 없어서 그 정도면 충분하다.
     */
    private void runMigration() {
        List<String> statements = Arrays.stream(readMigration().split(";"))
                .map(this::stripComments)
                .filter(sql -> !sql.isBlank())
                .toList();
        transactionTemplate.executeWithoutResult(status ->
                statements.forEach(sql -> entityManager.createNativeQuery(sql).executeUpdate()));
        entityManager.clear();
    }

    private String stripComments(String sql) {
        return sql.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b)
                .trim();
    }

    private String readMigration() {
        try (InputStream in = new ClassPathResource(MIGRATION).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(MIGRATION + " 을 클래스패스에서 못 찾았다", e);
        }
    }
}
