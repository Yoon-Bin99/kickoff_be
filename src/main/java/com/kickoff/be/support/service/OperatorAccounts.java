package com.kickoff.be.support.service;

import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "누가 운영자인가"를 한 곳에서 답한다 (계약서 §7-1·§7-2).
 *
 * 문의함(§7-1)과 신고함(§7-2)이 같은 판정을 쓰는데, 각자 설정값을 읽으면 한쪽만 바뀌는
 * 날이 온다 — 그때 운영자는 문의는 열고 신고는 못 여는(또는 그 반대) 상태가 된다.
 *
 * <b>미설정이 기본이다.</b> 운영자가 아무도 없으면 운영자 API 는 전부 403 이고, 알림은
 * 조용히 생략된다 — 설정을 빠뜨린 배포가 남의 문의·신고를 열어 주지 않게 하려는 것이다.
 */
@Component
@RequiredArgsConstructor
public class OperatorAccounts {

    private final SupportProperties properties;
    private final UserRepository userRepository;

    public boolean isOperator(User user) {
        return user != null && properties.isOperator(user.getEmail());
    }

    /**
     * 운영자 계정의 id. 미설정이거나 그 이메일의 계정이 없으면 비어 있다 — 알림만 안 갈
     * 뿐 문의·신고 저장은 정상이다. 운영자가 없다고 사용자가 글을 못 남기면 안 된다.
     */
    @Transactional(readOnly = true)
    public Optional<Long> operatorUserId() {
        String email = properties.operatorEmail();
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findAllByEmailIgnoreCase(email).stream()
                .findFirst()
                .map(User::getId);
    }
}
