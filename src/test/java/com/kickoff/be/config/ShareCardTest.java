package com.kickoff.be.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 카카오톡 카드형 공유용 브랜딩 이미지 — GET /share-card.png.
 *
 * 계약서의 API 표면이 아니라 정적 자원이다. 그래도 테스트를 두는 이유는 이게 <b>인증 없이
 * 열려 있어야만</b> 쓸모가 있기 때문이다. 카카오 서버가 미리보기를 만들 때는 사용자의
 * 토큰이 없으므로, 401 이 되는 순간 공유 카드에 이미지가 통째로 빠진다 — 우리 앱에서는
 * 아무 에러도 나지 않고 카카오톡 대화창에서만 회색 칸으로 보인다.
 *
 * SecurityConfig 의 anyRequest().authenticated() 가 기본값이라, 이 permitAll 한 줄을
 * 누가 지워도 테스트 없이는 아무도 모른다.
 */
class ShareCardTest extends IntegrationTestSupport {

    @Test
    @DisplayName("토큰 없이 200 으로 받아진다 — 카카오 서버가 토큰 없이 가져간다")
    void servedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/share-card.png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE));
    }

    @Test
    @DisplayName("캐시 헤더가 붙는다 — 카카오가 매번 원본을 긁어가지 않게")
    void hasCacheHeader() throws Exception {
        mockMvc.perform(get("/share-card.png"))
                .andExpect(header().string("Cache-Control", "max-age=86400, public"));
    }

    @Test
    @DisplayName("실제로 열리는 800x400 PNG 다 — 빈 파일이나 깨진 파일이 아니다")
    void isARealImageOfTheExpectedSize() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/share-card.png"))
                .andReturn().getResponse();
        byte[] bytes = response.getContentAsByteArray();

        // 0 바이트 파일도 200 으로 나간다. 카카오는 그런 이미지를 그냥 안 그리므로,
        // 여기서 실제로 디코딩해 봐야 "서빙은 되는데 그림이 없는" 상태를 막을 수 있다.
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(image).as("PNG 로 디코딩돼야 한다").isNotNull();
        assertThat(image.getWidth()).isEqualTo(800);
        assertThat(image.getHeight()).isEqualTo(400);
    }
}
