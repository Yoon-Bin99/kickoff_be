package com.kickoff.be.matchrequest.repository;

/** 목록에서 글마다 신청 수를 세려고 한 번에 긁어오는 집계 결과. */
public record PostRequestCount(Long postId, Long count) {
}
