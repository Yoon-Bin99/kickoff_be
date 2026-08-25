package com.kickoff.be.common;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * PATCH 본문에서 <b>"필드를 안 보냄"과 "명시적으로 null 을 보냄"을 구분</b>하기 위한 타입.
 *
 * 평범한 Double 로 받으면 둘 다 null 이라 구분이 안 된다. 그래서 좌표를 지우려고 null 을
 * 보내도 "안 보낸 것"으로 읽혀 기존 값이 남았다 — 글에는 "직접 입력한 공터"라고 쓰여 있는데
 * 지도는 이전 구장을 가리키는 사고가 실제로 났다.
 *
 * 구분은 Jackson 의 두 진입점이 갈라지는 성질을 쓴다.
 * <ul>
 *   <li>필드 자체가 없으면 → Jackson 이 아무것도 호출하지 않아 이 래퍼가 {@code null} 로 남는다</li>
 *   <li>{@code "latitude": null} 이면 → {@code getNullValue} 가 불려 value 가 null 인 래퍼가 온다</li>
 *   <li>값이 있으면 → {@code deserialize} 가 불린다</li>
 * </ul>
 *
 * Bean Validation 어노테이션은 이 타입에 붙지 않으므로 범위 검증은 서비스에서 한다.
 */
@JsonDeserialize(using = PatchableDouble.Deserializer.class)
public record PatchableDouble(Double value) {

    public boolean hasValue() {
        return value != null;
    }

    /** 요청에 필드가 있었는지. null 참조면 없었던 것이다. */
    public static boolean isPresent(PatchableDouble field) {
        return field != null;
    }

    public static Double valueOf(PatchableDouble field) {
        return field == null ? null : field.value();
    }

    static class Deserializer extends ValueDeserializer<PatchableDouble> {

        @Override
        public PatchableDouble deserialize(JsonParser parser, DeserializationContext context) {
            return new PatchableDouble(context.readValue(parser, Double.class));
        }

        /** 명시적 null 이 여기로 온다 — 필드가 아예 없을 때와 갈리는 지점이다. */
        @Override
        public PatchableDouble getNullValue(DeserializationContext context) {
            return new PatchableDouble(null);
        }

        /**
         * 필드가 <b>아예 없을 때</b> 불린다. 기본 구현이 getNullValue 로 위임하기 때문에
         * 이걸 재정의하지 않으면 "안 보냄"과 "null 을 보냄"이 다시 같아진다 —
         * 실제로 그 상태에서는 좌표를 건드리지 않는 PATCH 가 좌표를 지워버렸다.
         */
        @Override
        public Object getAbsentValue(DeserializationContext context) {
            return null;
        }
    }
}
