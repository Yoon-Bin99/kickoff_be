package com.kickoff.be.oauth.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.oauth.client.OAuthProperties;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 로그인 완료 후 돌아갈 URL 을 허용 목록과 대조한다 (계약서 §3-1, open redirect 방지).
 *
 * 이 검사가 없으면 공격자가 `redirect=https://evil.example` 로 authorize 링크를 만들어
 * 피해자가 로그인을 마치는 순간 <b>발급된 JWT 가 통째로 공격자 서버로</b> 넘어간다.
 * 토큰을 쿼리로 실어 보내는 구조(계약서상 dev 한정)라 더더욱 여기서 막아야 한다.
 */
@Component
public class RedirectAllowList {

    private final List<Pattern> allowed;

    public RedirectAllowList(OAuthProperties properties) {
        this.allowed = properties.allowedRedirects().stream()
                .map(RedirectAllowList::toPattern)
                .toList();
    }

    /**
     * 계약서가 이 경우의 에러 코드를 규정하지 않았다. 잘못된 쿼리 파라미터이므로
     * VALIDATION_FAILED 로 내보낸다. 여기서는 redirect 로 302 할 수 없다 —
     * 신뢰할 수 없다고 판단한 주소로 되돌려 보내면 검사한 의미가 없다.
     */
    public String require(String redirect) {
        if (redirect == null || redirect.isBlank() || !isAllowed(redirect)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    List.of(new ErrorResponse.FieldError("redirect",
                            "허용되지 않은 복귀 주소입니다.")));
        }
        return redirect;
    }

    public boolean isAllowed(String redirect) {
        return allowed.stream().anyMatch(pattern -> pattern.matcher(redirect).matches());
    }

    /** `exp://*`, `http://localhost:*` 처럼 `*` 만 와일드카드로 쓰는 단순 패턴이다. */
    private static Pattern toPattern(String raw) {
        StringBuilder regex = new StringBuilder();
        for (String literal : raw.split("\\*", -1)) {
            if (regex.length() > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(literal));
        }
        return Pattern.compile(regex.toString());
    }
}
