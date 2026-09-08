package com.kickoff.be.user.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.passwordreset.repository.PasswordResetCodeRepository;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.verification.repository.PhoneVerificationRepository;
import com.kickoff.be.user.dto.AccountDeleteRequest;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.AccountDeletionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 (계약서 §3-4, v1.23.0).
 *
 * 개인정보처리방침 §3·§6 이 약속한 삭제권의 실행 경로다. 되돌릴 수 없다.
 *
 * <b>삭제 순서가 이 기능의 전부다.</b> 외래키가 걸린 순서를 한 번이라도 어기면 탈퇴가
 * 500 으로 막히는데, 그때 사용자는 "탈퇴가 안 된다"만 보고 이유를 알 수 없다. 계약이
 * 세부를 BE 재량으로 남기면서 원칙 둘만 못박은 것도 그래서다 —
 * <b>500 으로 막히는 경로가 없을 것, 상대 팀 화면이 깨지지 않을 것.</b>
 *
 * 쿼리는 {@code AccountDeletionRepository} 한 곳에 모아 뒀다. 여기와 나란히 읽으면
 * 전체 순서가 한눈에 들어온다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final AccountDeletionRepository deletionRepository;
    private final TeamRepository teamRepository;
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void delete(User user, AccountDeleteRequest request) {
        requirePassword(user, request);

        Team team = teamRepository.findByOwnerId(user.getId()).orElse(null);
        if (team != null) {
            requireNoUpcomingMatch(team);
            deleteTeam(team);
        }
        deleteIdentityTraces(user);
        deleteUserScoped(user.getId());

        // 전화번호 유니크가 풀려 같은 번호로 재가입할 수 있다 (계약서 §3-4).
        log.info("회원 탈퇴 완료 — userId={}, 팀 삭제={}", user.getId(), team != null);
    }

    /**
     * 이메일 계정은 비밀번호를 확인한다 (계약서 §3-4).
     *
     * <b>불일치는 400 이지 401 이 아니다.</b> 401 을 주면 FE 인터셉터가 세션 만료로 오인해
     * refresh 를 타고, 사용자는 비밀번호를 틀린 줄도 모른 채 화면이 튄다.
     *
     * 소셜 계정은 비밀번호가 없으므로 body 없이 부른다 — FE 의 확인 다이얼로그가 유일한
     * 관문이다. 여기서 비밀번호를 요구하면 소셜 사용자는 <b>영영 탈퇴할 수 없다.</b>
     */
    private void requirePassword(User user, AccountDeleteRequest request) {
        if (!user.hasPassword()) {
            return;
        }
        String password = request == null ? null : request.password();
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
    }

    /**
     * 경기 예정인 확정 매칭이 있으면 막는다 (계약서 §3-4).
     *
     * 지난 매칭과 PENDING 신청은 차단 사유가 아니다. 막는 것은 <b>상대가 이미 일정을
     * 잡아 둔 경기</b> 하나뿐이다 — 그게 소리 없이 사라지면 상대 팀은 경기 당일에야 안다.
     */
    private void requireNoUpcomingMatch(Team team) {
        if (deletionRepository.countUpcomingAcceptedMatches(team.getId(), OffsetDateTime.now())
                > 0) {
            throw new BusinessException(ErrorCode.ACTIVE_MATCH_EXISTS);
        }
    }

    /**
     * 팀과 팀에 딸린 모든 것. <b>자식부터 지운다.</b>
     *
     * 순서를 바꾸면 외래키 제약에 걸려 탈퇴가 통째로 실패한다. 각 줄이 어떤 참조를 푸는지는
     * 리포지토리 쪽에 적어 뒀다.
     */
    private void deleteTeam(Team team) {
        Long teamId = team.getId();
        List<Long> requestIds = deletionRepository.findRequestIdsInvolving(teamId);

        if (!requestIds.isEmpty()) {
            // 상대 팀의 수동 전적은 살리고 연결만 끊는다 (계약서 §3-4).
            // 이걸 안 하면 아래 신청 삭제가 외래키에 걸린다 — 그리고 지워 버리면
            // 상대 팀 전적에서 실제로 한 경기가 사라진다.
            deletionRepository.detachRecordsFromRequests(requestIds);
            deletionRepository.deleteChatLeaves(requestIds, teamId);
            deletionRepository.deleteChatMessages(requestIds);
        } else {
            // 신청이 없어도 팀 자체의 나가기 기록은 있을 수 있다.
            deletionRepository.deleteChatLeaves(List.of(-1L), teamId);
        }

        // 리뷰는 신청을 참조한다(not null). 신청보다 먼저 지워야 한다.
        deletionRepository.deleteReviewsOfTeam(teamId);
        deletionRepository.deleteRecordsOfTeam(teamId);
        deletionRepository.deleteMatchRequestsOfTeam(teamId);
        deletionRepository.deletePostsOfTeam(teamId);

        deletionRepository.deleteMembersOfTeam(teamId);
        deletionRepository.deleteAdminsOfTeam(teamId);
        deletionRepository.deleteJoinRequestsOfTeam(teamId);
        deletionRepository.deleteTeam(teamId);
    }

    /**
     * 인증 이력에 남은 전화번호·이메일을 지운다 (방침 §3-1 "탈퇴 시 지체 없이 파기").
     *
     * 이 두 표는 <b>users 를 참조하지 않는다.</b> 가입 전에도 쓰이기 때문이다 — 전화 인증은
     * 가입 자격을 만드는 절차이고, 비밀번호 재설정은 로그인하지 못하는 사람이 쓴다. 그래서
     * 외래키가 없고, users 행을 지워도 따라 사라지지 않는다. 번호·주소 문자열로 직접 지운다.
     *
     * 코드와 토큰은 BCrypt 해시라 그 자체로는 문제가 없다. 지우는 대상은 <b>평문으로 남는
     * 번호와 주소</b>다.
     *
     * <b>부작용 하나를 적어 둔다.</b> 이력이 사라지면 그 번호의 발송 레이트리밋 카운터도
     * 함께 초기화된다 — 탈퇴가 한도를 푸는 우회로가 되는 셈이다. 탈퇴는 비밀번호 확인과
     * 예정 매칭 검사를 지나야 하는 무거운 동작이라 실익이 없다고 보고 그대로 둔다.
     * 예전 주석이 이 표를 "부정 이용 방지 목적으로 남긴다"고 한 것도 그 값을 본 것인데,
     * 남의 개인정보를 계속 들고 있는 대가로 얻기에는 작다.
     */
    private void deleteIdentityTraces(User user) {
        if (user.hasPhone()) {
            phoneVerificationRepository.deleteByPhone(user.getPhone());
        }
        if (user.hasEmail()) {
            passwordResetCodeRepository.deleteByEmail(user.getEmail());
        }
    }

    /** 팀을 안 가진 계정도 지나는 경로. 남의 팀 소속·문의·소셜 연동이 여기서 정리된다. */
    private void deleteUserScoped(Long userId) {
        deletionRepository.deleteMembershipsOf(userId);
        deletionRepository.deleteAdminRolesOf(userId);
        deletionRepository.deleteJoinRequestsOf(userId);
        deletionRepository.deleteSupportMessagesOf(userId);
        deletionRepository.deleteSupportRoomOf(userId);
        deletionRepository.deleteSocialAccountsOf(userId);
        // refresh·push token 은 users 행에 있어 계정과 함께 사라진다.
        deletionRepository.deleteUser(userId);
    }
}
