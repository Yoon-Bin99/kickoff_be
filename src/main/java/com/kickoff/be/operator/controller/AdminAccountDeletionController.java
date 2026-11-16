package com.kickoff.be.operator.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.operator.dto.AdminDeletionRequest;
import com.kickoff.be.operator.dto.AdminDeletionResponse;
import com.kickoff.be.operator.service.AdminDeletionProperties;
import com.kickoff.be.operator.service.OperatorGuard;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.service.AccountDeletionService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 백업 복원 뒤의 재탈퇴 (계약서 §7-2 「운영자 API」 B-6, v1.37.3).
 *
 * <b>왜 필요한가.</b> DB 를 백업에서 되살리면 그 시점 이후에 탈퇴했던 계정이 함께
 * 되살아난다. 손으로 SQL 을 돌려 지우면 채팅 서버로 가는 {@code PURGE} 가 빠지고,
 * 그러면 탈퇴한 사람의 대화가 거기 남는다 — 방침이 약속한 "지체 없이 파기"가 깨지는
 * 자리다. 그래서 §3-4 의 탈퇴 경로를 그대로 다시 태운다.
 *
 * <b>평시에는 꺼 둔다.</b> 상시 살아 있으면 운영자 계정이 뚫렸을 때 대량 삭제 수단이
 * 된다. 복원 작업 중에만 켜고 끝나면 끈다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminAccountDeletionController {

    private final AdminDeletionProperties properties;
    private final OperatorGuard operatorGuard;
    private final AccountDeletionService accountDeletionService;

    @PostMapping("/api/admin/users/deletions")
    public ResponseEntity<AdminDeletionResponse> delete(
            @LoginUser User operator, @Valid @RequestBody AdminDeletionRequest request) {
        requireEnabled();
        List<Long> userIds = request.userIds().stream().distinct().toList();
        // 운영자가 자기 계정을 지우면 그 순간 복원 작업을 이어 갈 사람이 없어진다.
        if (userIds.contains(operator.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                    new ErrorResponse.FieldError("userIds",
                            "자기 계정은 여기서 지울 수 없습니다.")));
        }
        operatorGuard.require(operator, "계정 재탈퇴", null, null);

        List<Long> deleted = new ArrayList<>();
        List<Long> notFound = new ArrayList<>();
        for (Long userId : userIds) {
            // 대상이 전부 접속기록에 남아야 한다 (계약서 §7-2). 한 줄에 몰아 적지 않는
            // 것은 접속기록이 "누구의 정보를" 한 명씩 가리키는 기록이기 때문이다.
            operatorGuard.record(operator, "계정 재탈퇴", userId, null);
            if (accountDeletionService.deleteByOperator(userId)) {
                deleted.add(userId);
            } else {
                notFound.add(userId);
            }
        }
        log.info("운영자 재탈퇴 — 요청 {}건, 삭제 {}건, 없음 {}건",
                userIds.size(), deleted.size(), notFound.size());
        return ResponseEntity.ok(new AdminDeletionResponse(deleted, notFound));
    }

    /**
     * 꺼져 있으면 <b>404 다</b> (계약서 §7-2). 403 이 아닌 이유: 있는 줄도 모르게 두는
     * 편이 낫다 — 운영자 계정을 쥔 공격자에게 "여기 대량 삭제 API 가 있는데 꺼져 있다"는
     * 사실까지 알려 줄 이유가 없다.
     */
    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }
}
