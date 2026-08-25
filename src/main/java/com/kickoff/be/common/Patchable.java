package com.kickoff.be.common;

import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * PATCH 본문에서 <b>"필드를 안 보냄"과 "명시적으로 null 을 보냄"을 구분</b>하는 래퍼
 * (계약서 §5 "PATCH 지우기 규칙", v1.5.1).
 *
 * 평범한 타입으로 받으면 둘 다 null 이라 구분이 안 된다. 그래서 값을 지우려고 null 을 보내도
 * "안 보낸 것"으로 읽혀 기존 값이 남았다 — 좌표에서 실제로 사고가 났다. 글에는 "직접 입력한
 * 공터"라고 쓰여 있는데 지도는 이전 구장을 가리켰다. 같은 결함이 입금액·계좌·실력수준 등에도
 * 남아 있어서 v1.5.1 에서 규칙을 일반화했다.
 *
 * 구분은 Jackson 의 세 진입점이 갈라지는 성질을 쓴다.
 * <ul>
 *   <li>필드가 아예 없으면 → {@code getAbsentValue} 가 null 을 주어 이 래퍼 자체가 null 로 남는다</li>
 *   <li>{@code "rentalFee": null} 이면 → {@code getNullValue} 가 불려 value 가 null 인 래퍼가 온다</li>
 *   <li>값이 있으면 → {@code deserialize} 가 불린다</li>
 * </ul>
 *
 * Bean Validation 은 {@link PatchableValueExtractor} 덕분에 담긴 값에 그대로 걸린다.
 * 즉 {@code @Size(max = 20) Patchable<String> bankName} 처럼 평소대로 쓰면 된다.
 */
@JsonDeserialize(using = Patchable.Deserializer.class)
public record Patchable<T>(T value) {

    /** 요청에 필드가 있었는지. 래퍼 참조가 null 이면 안 보낸 것이다. */
    public static boolean isPresent(Patchable<?> field) {
        return field != null;
    }

    /** 지우라는 요청인지 — 필드가 있었고 그 값이 null 이면 지우기다. */
    public static boolean isClear(Patchable<?> field) {
        return field != null && field.value() == null;
    }

    /** 안 보냈거나 지우기면 null, 값이 있으면 그 값. */
    public static <T> T valueOf(Patchable<T> field) {
        return field == null ? null : field.value();
    }

    /** 안 보냈으면 기존 값을 그대로, 보냈으면 보낸 값(지우기면 null)을 준다. */
    public static <T> T merge(Patchable<T> field, T current) {
        return field == null ? current : field.value();
    }

    /**
     * 지울 수 없는 필드에 명시적 {@code null} 이 오면 400 으로 끊는다 (계약서 §5, v1.5.1).
     *
     * 모집글과 팀이 같은 규칙을 쓰므로 여기 둔다. 막지 않으면 "안 보냄"과 같아져 조용히
     * 무시되는데, 200 이 돌아오니 FE 는 반영된 줄 알고 넘어간다.
     */
    public static void rejectClear(Patchable<?> field, String name) {
        if (isClear(field)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                    new ErrorResponse.FieldError(name, "이 필드는 null 로 지울 수 없습니다.")));
        }
    }

    static class Deserializer extends ValueDeserializer<Patchable<?>> {

        /** 담긴 타입. createContextual 이 필드 선언에서 뽑아 준다. */
        private final JavaType valueType;

        Deserializer() {
            this(null);
        }

        private Deserializer(JavaType valueType) {
            this.valueType = valueType;
        }

        /**
         * {@code Patchable<Integer>} 의 Integer 를 여기서 알아낸다. 이걸 안 하면 담긴 값이
         * 전부 LinkedHashMap/Integer 같은 날것으로 들어와 레코드 생성에서 터진다.
         */
        @Override
        public ValueDeserializer<?> createContextual(DeserializationContext context,
                                                     BeanProperty property) {
            JavaType wrapper = property != null ? property.getType() : context.getContextualType();
            JavaType contained = wrapper != null && wrapper.containedTypeCount() > 0
                    ? wrapper.containedType(0)
                    : null;
            return new Deserializer(contained);
        }

        @Override
        public Patchable<?> deserialize(JsonParser parser, DeserializationContext context) {
            return new Patchable<>(valueType == null
                    ? context.readValue(parser, Object.class)
                    : context.readValue(parser, valueType));
        }

        /** 명시적 null 이 여기로 온다 — 필드가 아예 없을 때와 갈리는 지점이다. */
        @Override
        public Object getNullValue(DeserializationContext context) {
            return new Patchable<>(null);
        }

        /**
         * 필드가 <b>아예 없을 때</b> 불린다. 기본 구현이 getNullValue 로 위임하기 때문에
         * 이걸 재정의하지 않으면 "안 보냄"과 "null 을 보냄"이 다시 같아진다 — 실제로 그
         * 상태에서는 좌표를 건드리지 않는 PATCH 가 좌표를 지워버렸다.
         */
        @Override
        public Object getAbsentValue(DeserializationContext context) {
            return null;
        }
    }
}
