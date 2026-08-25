package com.kickoff.be.common;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.ValueExtractor;

/**
 * {@link Patchable} 안의 값에 Bean Validation 어노테이션이 그대로 걸리게 한다.
 *
 * 이게 없으면 {@code @Size(max = 20) Patchable<String> bankName} 의 @Size 가 래퍼에 걸려
 * 아무 일도 하지 않는다. 그러면 필드를 Patchable 로 바꾸는 순간 <b>검증이 조용히 사라진다</b> —
 * 컴파일도 되고 테스트도 통과하는데 길이 제한만 없어지는, 눈에 안 보이는 종류의 퇴행이다.
 * 그래서 지우기를 도입하면서 검증은 선언 그대로 두는 쪽을 택했다.
 *
 * 값이 null 인 래퍼(= 지우라는 요청)는 담긴 값 null 로 전달된다. @Size·@PositiveOrZero 같은
 * 제약은 null 을 통과시키므로 지우기가 막히지 않는다. 그게 의도다 — 지울 수 있는지 없는지는
 * 어노테이션이 아니라 계약(§5)이 정하고, 서비스가 판단한다.
 *
 * {@code @UnwrapByDefault} 가 핵심이다. 이게 없으면 {@code @Size(max = 20) Patchable<String>} 의
 * 제약이 <b>컨테이너</b>에 걸린 것으로 읽혀 "Patchable 을 검증할 validator 가 없다"로 터진다.
 * 담긴 값에 걸리게 하려면 원래 {@code Patchable<@Size(max = 20) String>} 처럼 타입 인자에 붙여야
 * 하는데, 그러면 기존 선언을 전부 바꿔야 하고 POST 쪽 DTO 와 모양이 달라져 읽기 나쁘다.
 *
 * 등록은 {@code META-INF/services/jakarta.validation.valueextraction.ValueExtractor} 로 한다.
 * Hibernate Validator 가 부팅할 때 ServiceLoader 로 집어 가므로 스프링 설정이 필요 없다.
 */
@UnwrapByDefault
public class PatchableValueExtractor implements ValueExtractor<Patchable<@ExtractedValue ?>> {

    @Override
    public void extractValues(Patchable<?> originalValue, ValueReceiver receiver) {
        receiver.value(null, originalValue.value());
    }
}
