package com.kickoff.be.team;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.team.entity.Formation;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 포메이션 이름과 총원이 어긋나지 않는지 (계약서 §1·§4-4, v1.28.0).
 *
 * <b>왜 이런 테스트가 있는가.</b> 이름이 곧 계약이다 — FE 는 {@code F11_4_3_3} 을 받아
 * 자리 11개를 그리고, BE 는 {@code totalPlayers()} 로 11개인지 검증한다. 둘이 어긋나면
 * 저장은 되는데 화면에 자리가 모자라거나 남는다. <b>에러가 아니라 그림이 틀릴 뿐이라</b>
 * 눈으로 보기 전에는 드러나지 않는다.
 *
 * 포메이션을 새로 더할 때 숫자 하나 잘못 적는 것으로 그 상태가 되므로, 이름에서 읽어낸
 * 값과 선언한 값을 기계가 대조한다.
 */
class FormationTest {

    @Test
    @DisplayName("이름의 앞 숫자가 totalPlayers() 와 같다")
    void leadingNumberMatchesTotal() {
        for (Formation formation : Formation.values()) {
            // F11_4_3_3 → "11"
            String leading = formation.name().substring(1).split("_")[0];
            assertThat(formation.totalPlayers())
                    .as("%s 의 앞 숫자", formation.name())
                    .isEqualTo(Integer.parseInt(leading));
        }
    }

    @Test
    @DisplayName("줄 인원 합 + GK 1명 = totalPlayers()")
    void lineSumPlusKeeperMatchesTotal() {
        for (Formation formation : Formation.values()) {
            // F11_4_3_3 → [4, 3, 3] (앞 숫자는 총원이라 버린다)
            String[] parts = formation.name().substring(1).split("_");
            int lineSum = Arrays.stream(parts, 1, parts.length)
                    .mapToInt(Integer::parseInt)
                    .sum();
            assertThat(lineSum + 1)
                    .as("%s 의 줄 합 + GK", formation.name())
                    .isEqualTo(formation.totalPlayers());
        }
    }

    /**
     * 계약서 §1 이 열거한 10종이 그대로 있는지. 값을 지우거나 이름을 바꾸면 FE 가 깨진다 —
     * 저장된 스쿼드의 formation 도 읽을 수 없게 된다(DB check 제약과도 어긋난다).
     */
    @Test
    @DisplayName("계약서 §1 의 10종이 그대로 있다")
    void hasExactlyContractValues() {
        assertThat(Formation.values()).extracting(Formation::name).containsExactlyInAnyOrder(
                "F11_4_4_2", "F11_4_3_3", "F11_4_2_3_1", "F11_3_5_2", "F11_3_4_3", "F11_5_3_2",
                "F7_2_3_1", "F7_3_2_1", "F6_2_2_1", "F6_1_3_1");
    }
}
