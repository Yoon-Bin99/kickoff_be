package com.kickoff.be.operator.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 복원 뒤 다시 탈퇴시킬 계정들 (계약서 §7-2 B-6, v1.37.3).
 *
 * 한 번에 여러 개를 받는 이유: 복원 뒤의 실제 작업이 "이 시점 이후 탈퇴한 목록"을 한
 * 번에 처리하는 모양이다. 한 명씩이면 수십 번 부르며 빠뜨린다.
 *
 * @param userIds 1~200개. 중복은 한 번으로 본다
 */
public record AdminDeletionRequest(

        @NotEmpty(message = "지울 계정을 지정해 주세요.")
        @Size(max = 200, message = "한 번에 200개까지만 처리할 수 있습니다.")
        List<@Positive(message = "사용자 id 는 양수여야 합니다.") Long> userIds
) {
}
