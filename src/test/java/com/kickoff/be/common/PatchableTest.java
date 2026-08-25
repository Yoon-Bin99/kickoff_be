package com.kickoff.be.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link Patchable} 의 두 가지 성질을 고정한다.
 *
 * 1. "필드 없음"과 "명시적 null"이 갈리는가 — 이게 v1.5.1 지우기 규칙 전체가 딛고 선 성질이다
 * 2. Bean Validation 이 <b>담긴 값</b>에 걸리는가 — 안 걸리면 필드를 Patchable 로 바꾸는
 *    순간 길이·범위 검증이 조용히 사라진다. 컴파일도 되고 다른 테스트도 통과해서
 *    눈에 띄지 않는 종류의 퇴행이라 여기서 못을 박는다
 */
class PatchableTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    // 일부러 기본 팩토리를 쓴다. PatchableValueExtractor 를 손으로 등록하지 않아도
    // META-INF/services 로 자동 등록되는지까지 이 테스트가 함께 확인한다 —
    // 앱에서도 별도 설정 없이 같은 경로로 붙기 때문이다.
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    record Sample(
            @Size(max = 5, message = "5자 이하") Patchable<String> name,
            @PositiveOrZero(message = "0 이상") Patchable<Integer> amount,
            Patchable<Double> ratio) {
    }

    @Test
    @DisplayName("필드가 없으면 래퍼 자체가 null 이다 — 기존 값 유지")
    void absentFieldStaysNull() {
        Sample sample = mapper.readValue("{}", Sample.class);

        assertThat(sample.name()).isNull();
        assertThat(sample.amount()).isNull();
        assertThat(Patchable.isPresent(sample.name())).isFalse();
        assertThat(Patchable.isClear(sample.name())).isFalse();
    }

    @Test
    @DisplayName("명시적 null 은 값이 null 인 래퍼다 — 지우기")
    void explicitNullBecomesClearRequest() {
        Sample sample = mapper.readValue("{\"name\": null, \"amount\": null}", Sample.class);

        assertThat(sample.name()).isNotNull();
        assertThat(sample.name().value()).isNull();
        assertThat(Patchable.isPresent(sample.name())).isTrue();
        assertThat(Patchable.isClear(sample.name())).isTrue();
        assertThat(Patchable.isClear(sample.amount())).isTrue();
    }

    @Test
    @DisplayName("값이 오면 선언한 타입으로 변환된다")
    void valueIsConvertedToDeclaredType() {
        Sample sample = mapper.readValue(
                "{\"name\": \"kim\", \"amount\": 5000, \"ratio\": 1.5}", Sample.class);

        assertThat(sample.name().value()).isEqualTo("kim");
        // 제네릭 인자를 못 읽으면 여기서 타입이 어긋난다
        assertThat(sample.amount().value()).isEqualTo(5000);
        assertThat(sample.ratio().value()).isEqualTo(1.5);
    }

    @Test
    @DisplayName("merge 는 안 보냄이면 기존 값, 보냈으면 보낸 값을 준다")
    void mergeKeepsCurrentWhenAbsent() {
        Sample absent = mapper.readValue("{}", Sample.class);
        Sample cleared = mapper.readValue("{\"name\": null}", Sample.class);
        Sample changed = mapper.readValue("{\"name\": \"lee\"}", Sample.class);

        assertThat(Patchable.merge(absent.name(), "old")).isEqualTo("old");
        assertThat(Patchable.merge(cleared.name(), "old")).isNull();
        assertThat(Patchable.merge(changed.name(), "old")).isEqualTo("lee");
    }

    @Test
    @DisplayName("어노테이션이 래퍼가 아니라 담긴 값에 걸린다")
    void validationReachesTheContainedValue() {
        Sample tooLong = new Sample(new Patchable<>("여섯자넘는이름"), null, null);

        assertThat(validator.validate(tooLong))
                .singleElement()
                .satisfies(v -> assertThat(v.getMessage()).isEqualTo("5자 이하"));
    }

    @Test
    @DisplayName("음수도 잡힌다 — 숫자 제약이 살아 있다")
    void numericConstraintStillApplies() {
        Sample negative = new Sample(null, new Patchable<>(-1), null);

        assertThat(validator.validate(negative))
                .singleElement()
                .satisfies(v -> assertThat(v.getMessage()).isEqualTo("0 이상"));
    }

    @Test
    @DisplayName("지우기(값이 null)는 제약을 통과한다 — 지우기를 막으면 안 된다")
    void clearingPassesValidation() {
        Sample cleared = new Sample(new Patchable<>(null), new Patchable<>(null), null);

        assertThat(validator.validate(cleared)).isEmpty();
    }
}
