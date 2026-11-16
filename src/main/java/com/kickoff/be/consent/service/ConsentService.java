package com.kickoff.be.consent.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.consent.dto.ConsentsRequest;
import com.kickoff.be.consent.entity.ConsentType;
import com.kickoff.be.consent.entity.UserConsent;
import com.kickoff.be.consent.repository.UserConsentRepository;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동의를 기록하고 "지금 무엇을 받아야 하는가"를 판정한다 (계약서 §3 「동의」, v1.34.1).
 *
 * <b>판정이 서버에 있는 것이 이 개편의 핵심이다.</b> 전에는 FE 가 상수로 시행일을 들고
 * 비교했다 — 문서를 개정하는 날 앱이 업데이트되지 않으면 아무도 다시 동의하지 않았고,
 * 그 사실이 화면에 드러나지 않았다.
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final UserConsentRepository consentRepository;
    private final TeamRepository teamRepository;
    private final LegalProperties legalProperties;

    /**
     * 이 계정이 지금 받아야 하는 항목 (계약서 §3 — {@code pendingConsents}).
     *
     * 기준은 하나다: 기록이 없거나 현행 시행일보다 이르면 받아야 한다.
     * {@code contactSharing} 은 <b>팀을 소유한 계정에만</b> 묻는다 — 팀이 없으면 전화번호가
     * 상대에게 넘어갈 일이 없어서 받을 항목이 아니다.
     */
    @Transactional(readOnly = true)
    public List<ConsentType> pendingFor(User user) {
        Map<ConsentType, UserConsent> recorded = recordedFor(user.getId());
        boolean teamOwner = teamRepository.findByOwnerId(user.getId()).isPresent();

        List<ConsentType> pending = new ArrayList<>();
        for (ConsentType type : ConsentType.values()) {
            if (type.isForTeamOwnerOnly() && !teamOwner) {
                continue;
            }
            if (!isSatisfied(recorded.get(type))) {
                pending.add(type);
            }
        }
        return pending;
    }

    /** 동의 화면을 거쳐야 하는지 (계약서 §2 — {@code consentRequired}). */
    @Transactional(readOnly = true)
    public boolean isRequiredFor(User user) {
        return !pendingFor(user).isEmpty();
    }

    /**
     * 가입 화면 세 항목을 모두 마친 시각 (계약서 §2 — {@code termsAgreedAt}, 표시용).
     *
     * 셋 중 하나라도 없으면 null 이다. 있으면 <b>가장 늦은 시각</b> — 셋이 다 채워진 순간이
     * 그때다. v1.34.0 전까지 {@code users.terms_agreed_at} 이 들고 있던 값의 자리인데,
     * 이제 기록에서 계산한다(같은 사실을 두 곳에 두면 한쪽만 갱신되는 날이 온다).
     */
    @Transactional(readOnly = true)
    public OffsetDateTime signupConsentCompletedAt(User user) {
        Map<ConsentType, UserConsent> recorded = recordedFor(user.getId());
        OffsetDateTime latest = null;
        for (ConsentType type : ConsentType.SIGNUP_ITEMS) {
            UserConsent consent = recorded.get(type);
            if (consent == null) {
                return null;
            }
            if (latest == null || consent.getAgreedAt().isAfter(latest)) {
                latest = consent.getAgreedAt();
            }
        }
        return latest;
    }

    /**
     * 가입할 때 받은 세 항목을 기록한다 (계약서 §3 signup).
     *
     * 검증은 요청 DTO 가 끝낸 뒤다 — 여기 오는 요청은 셋이 모두 {@code true} 다.
     */
    @Transactional
    public void recordSignupConsents(User user) {
        OffsetDateTime now = OffsetDateTime.now();
        ConsentType.SIGNUP_ITEMS.forEach(type -> record(user, type, now));
    }

    /**
     * 팀을 만들 때 받은 연락처 제공 동의를 기록한다 (계약서 §3, v1.34.1).
     *
     * 검증은 팀 생성 요청 DTO 가 한다({@code contactSharingConsent: true} 필수).
     */
    @Transactional
    public void recordContactSharing(User user) {
        record(user, ConsentType.CONTACT_SHARING, OffsetDateTime.now());
    }

    /**
     * 동의 화면이 보낸 것을 기록한다 (계약서 §3 — {@code PUT /api/users/me/consents}).
     *
     * <b>받아야 하는 항목이 모두 true 로 들어 있어야 한다.</b> 빠졌거나 false 면 400 이고
     * {@code field} 는 항목 키다 — FE 가 어느 체크박스를 가리켜야 할지 알아야 한다.
     *
     * 받을 항목이 아닌 키는 보내지 않아도 되고, {@code true} 면 함께 기록한다. 그쪽을
     * 허용하는 이유: 팀을 만들기 전에 동의 화면에서 미리 체크한 사용자를 거부할 이유가
     * 없고, {@code false} 는 "거부"라서 거부한다.
     *
     * 멱등이다 — 같은 요청을 다시 보내면 시각만 새로 찍힌다. 동의 화면에서 네트워크가
     * 끊겨 재시도하는 경우가 정상 경로에 있다.
     */
    @Transactional
    public void submit(User user, ConsentsRequest request) {
        List<ConsentType> pending = pendingFor(user);
        List<ErrorResponse.FieldError> missing = new ArrayList<>();
        for (ConsentType type : pending) {
            if (!Boolean.TRUE.equals(request.valueOf(type))) {
                missing.add(new ErrorResponse.FieldError(type.key(),
                        "이 항목에 동의해야 서비스를 이용할 수 있습니다."));
            }
        }
        // 받을 항목이 아니어도 false 로 보낸 것은 거부다 — 조용히 무시하면 사용자가 거부한
        // 항목이 기록되지 않은 채 통과한다.
        for (ConsentType type : ConsentType.values()) {
            if (!pending.contains(type) && Boolean.FALSE.equals(request.valueOf(type))) {
                missing.add(new ErrorResponse.FieldError(type.key(),
                        "이 항목에 동의해야 서비스를 이용할 수 있습니다."));
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, missing);
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (ConsentType type : ConsentType.values()) {
            if (Boolean.TRUE.equals(request.valueOf(type))) {
                record(user, type, now);
            }
        }
    }

    private void record(User user, ConsentType type, OffsetDateTime now) {
        OffsetDateTime effectiveAt = legalProperties.effectiveAt();
        consentRepository.findByUserIdAndConsentType(user.getId(), type)
                .ifPresentOrElse(
                        existing -> existing.renew(now, effectiveAt),
                        () -> consentRepository.save(
                                UserConsent.of(user, type, now, effectiveAt)));
    }

    private Map<ConsentType, UserConsent> recordedFor(Long userId) {
        Map<ConsentType, UserConsent> byType = new EnumMap<>(ConsentType.class);
        consentRepository.findByUserId(userId)
                .forEach(consent -> byType.put(consent.getConsentType(), consent));
        return byType;
    }

    /**
     * 이 기록으로 지금 충분한지 (계약서 §3 「동의」 판정, v1.35.3).
     *
     * <b>아직 오지 않은 시행일로는 재동의를 요구하지 않는다.</b> 개정 약관은 시행 전에
     * 공지하므로 설정값을 미리 올려 두는 운영이 자연스러운데, 그때 요구하면 그 기간 동안
     * 아무도 통과할 수 없다 — 다시 동의해도 동의 시각이 그 날짜보다 이르니 풀리지 않는다.
     * 그날이 되면 그 전에 동의한 계정이 모두 대상이 되고, 다시 동의하면 풀린다.
     */
    private boolean isSatisfied(UserConsent consent) {
        if (consent == null) {
            return false;
        }
        OffsetDateTime effectiveAt = legalProperties.effectiveAt();
        if (effectiveAt.isAfter(OffsetDateTime.now())) {
            return true;
        }
        return consent.coversDocumentEffectiveAt(effectiveAt);
    }
}
