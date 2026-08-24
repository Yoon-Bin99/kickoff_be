package com.kickoff.be.oauth.client;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.oauth.entity.AuthProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 쓸 수 있는 제공자만 모은다.
 *
 * <b>키가 없는 제공자는 등록하지 않는다.</b> 사용자가 아직 카카오/네이버 키를 발급받는 중이라
 * 키 없이도 서버가 떠야 하고, 그 상태에서 로그인을 시도하면 500 이 아니라 계약서대로
 * 400 UNSUPPORTED_PROVIDER 가 나가야 한다. 테스트는 configured 인 스텁을 넣어
 * 실제 키 없이도 흐름을 검증한다.
 */
@Slf4j
@Component
public class OAuthClientRegistry {

    private final Map<AuthProvider, OAuthClient> byProvider = new EnumMap<>(AuthProvider.class);

    public OAuthClientRegistry(List<OAuthClient> clients) {
        for (OAuthClient client : clients) {
            if (!client.isConfigured()) {
                continue;
            }
            // 같은 제공자가 둘이면 먼저 온 쪽을 쓴다. 실제로는 테스트 스텁만 configured 라
            // 실 클라이언트와 겹치지 않는다.
            byProvider.putIfAbsent(client.provider(), client);
        }
        log.info("활성 소셜 제공자: {}", byProvider.isEmpty() ? "없음 (키 미설정)" : byProvider.keySet());
    }

    public OAuthClient get(AuthProvider provider) {
        OAuthClient client = byProvider.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
        }
        return client;
    }
}
