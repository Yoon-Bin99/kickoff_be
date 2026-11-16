package com.kickoff.be.operator.dto;

import java.util.List;

/**
 * 재탈퇴 결과 (계약서 §7-2 B-6, v1.37.3).
 *
 * <b>이미 없는 id 를 오류로 보지 않는다.</b> 같은 목록을 다시 돌리는 일이 정상 경로에
 * 있다(중간에 끊겼을 때). 그때 "없다"는 것은 실패가 아니라 이미 끝났다는 뜻이다.
 *
 * @param deleted  이번에 지운 id
 * @param notFound 그 id 의 계정이 없던 것
 */
public record AdminDeletionResponse(List<Long> deleted, List<Long> notFound) {
}
