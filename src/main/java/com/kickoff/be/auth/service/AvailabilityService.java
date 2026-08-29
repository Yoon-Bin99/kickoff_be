package com.kickoff.be.auth.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.user.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 폼의 사전 중복 확인 (계약서 §3, v1.16.0).
 *
 * <b>UX 보조일 뿐 진실이 아니다.</b> 확인과 제출 사이에 다른 사람이 같은 값을 가져갈 수
 * 있으므로, 최종 판정은 언제나 signup·PATCH 의 409 다. 그래서 여기서 잠그거나 예약하지
 * 않는다 — 예약을 넣으면 아무도 쓰지 않는 값이 영영 묶인다.
 *
 * 응답에 <b>요청한 키만</b> 담는 게 계약이다. 프로젝트가 Jackson 을
 * {@code default-property-inclusion: always} 로 두고 있어서, 필드를 가진 DTO 로 만들면
 * 요청하지 않은 키가 null 로 함께 나간다. 그래서 Map 을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<String, Boolean> check(String email, String nickname, String phone) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (email != null) {
            result.put("email", !userRepository.existsByEmailIgnoreCase(email));
        }
        if (nickname != null) {
            // 닉네임도 대소문자를 무시한다 (계약서 §3, v1.16.0). 전화번호는 형식이 고정
            // 이라 그럴 일이 없다.
            result.put("nickname", !userRepository.existsByNicknameIgnoreCase(nickname));
        }
        if (phone != null) {
            result.put("phone", !userRepository.existsByPhone(phone));
        }
        if (result.isEmpty()) {
            // 셋 다 없으면 물어본 게 없다. 빈 객체를 200 으로 돌려주면 FE 는 "전부 사용
            // 가능"으로 읽기 쉬워서, 조용히 틀리는 대신 400 으로 끊는다.
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "email, nickname, phone 중 하나 이상을 주어야 합니다.");
        }
        return result;
    }
}
