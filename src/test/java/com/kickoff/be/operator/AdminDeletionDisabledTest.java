package com.kickoff.be.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.operator.service.OperatorGuard;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 재탈퇴 API 는 <b>기본이 꺼짐</b>이다 (계약서 §7-2 B-6, v1.37.3).
 *
 * 설정을 덮지 않는 것이 이 테스트의 전부다 — 기본값이 켜짐으로 바뀌는 날 여기서 걸린다.
 * 상시 살아 있으면 운영자 계정이 뚫렸을 때 그것이 곧 대량 삭제 수단이 된다.
 */
class AdminDeletionDisabledTest extends IntegrationTestSupport {

    /**
     * <b>403 이 아니라 404 다.</b> 운영자 계정을 쥔 공격자에게 "여기 대량 삭제 API 가
     * 있는데 꺼져 있다"는 사실까지 알려 줄 이유가 없다.
     */
    @Test
    @DisplayName("꺼져 있으면 404 이고 아무도 지워지지 않는다")
    void disabledByDefault() throws Exception {
        User operator = createUser("operator@kickoff.test", "운영자", "010-9999-9999");
        User target = createUser("target@example.com", "대상", "010-1111-1111");

        mockMvc.perform(post("/api/admin/users/deletions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .header(OperatorGuard.HEADER, operatorToken(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[%d]}".formatted(target.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(userRepository.findById(target.getId())).isPresent();
    }
}
