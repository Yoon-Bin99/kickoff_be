package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.Formation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * POST·PUT /api/teams/{teamId}/squads (계약서 §4-4, v1.28.0).
 *
 * <b>생성과 수정이 같은 DTO 다.</b> PUT 이 전체 교체라서다 — 보드 하나를 통째로 저장하는
 * UI 라 부분 수정이 의미가 없고, 포메이션이 바뀌면 자리 개수도 바뀌어 섞을 수도 없다.
 *
 * {@code slots} 개수 검증은 여기서 못 한다. 정확히 몇 개여야 하는지가 {@code formation} 에
 * 달려 있어 필드 하나만 보는 빈 검증으로는 판정할 수 없다 — 서비스가 본다.
 *
 * {@code bench} 가 null 이면 빈 목록으로 본다. 교체 없이 선발만 짜는 게 정상이라
 * FE 가 키를 생략해도 400 을 내지 않는다.
 */
public record SquadSaveRequest(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(min = 1, max = 30, message = "제목은 1~30자여야 합니다.")
        String title,

        @NotNull(message = "포메이션은 필수입니다.")
        Formation formation,

        @NotNull(message = "자리 목록은 필수입니다.")
        @Valid
        List<SquadItemRequest> slots,

        @Size(max = 10, message = "교체는 10명을 넘을 수 없습니다.")
        @Valid
        List<SquadItemRequest> bench
) {

    public List<SquadItemRequest> benchOrEmpty() {
        return bench == null ? List.of() : bench;
    }
}
