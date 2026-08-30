package com.kickoff.be.post.service;

import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * matchAt 이 DB 에 <b>어떤 시각</b>으로 들어가는지를 한곳에서 다룬다 (계약서 §5, v1.18.0).
 *
 * Hibernate 의 {@code timezone.default_storage: NORMALIZE} 는 OffsetDateTime 을 <b>JVM
 * 기본 시간대</b>의 벽시계 시각으로 바꿔 {@code timestamp} 컬럼에 넣는다. UTC 가 아니다.
 * 그래서 호스트 시간대가 다르면 같은 경기가 다른 시각으로 저장된다 — 개발 머신(KST)과
 * 컨테이너(UTC)가 아홉 시간 어긋나는 식이다.
 *
 * <b>이 앱은 그 흔들림을 이미 없애 두었다.</b> {@code BeApplication} 의 정적 초기화가
 * JVM 기본 시간대를 Asia/Seoul 로 고정한다 — EntityManagerFactory 가 만들어지기 전에
 * 잡히므로 Hibernate 가 보는 시간대도 그것이다. 따라서 <b>저장된 시각은 언제나 KST
 * 벽시계</b>이고, 시간대 필터는 KST 시(hour)를 그대로 쓰면 된다.
 *
 * 이 클래스가 하는 일은 그 전제를 <b>말로 적고 확인하는 것</b>이다. 전제가 깨지면
 * (누가 정적 초기화를 지우면) 필터는 조용히 아홉 시간 어긋난 목록을 내놓는다 — 에러가
 * 없어서 화면을 시간대별로 세어 보기 전에는 아무도 모른다. 그래서 조용히 틀리는 대신
 * 시끄럽게 멈춘다.
 */
public final class MatchAtStorage {

    /** 계약이 정한 판정 기준 시간대 (계약서 §5). 한국은 앞으로 서머타임 전환이 없다. */
    public static final ZoneOffset KST = ZoneOffset.ofHours(9);

    /** {@code BeApplication} 이 고정하는 시간대. 두 곳이 어긋나면 안 된다. */
    private static final ZoneId STORAGE_ZONE = ZoneId.of("Asia/Seoul");

    private MatchAtStorage() {
    }

    /**
     * KST 시(hour)를 <b>DB 에 저장된 시</b>로 옮긴다.
     *
     * 저장 시간대가 KST 로 고정돼 있으므로 값은 그대로다. 이 메서드를 없애고 호출부에서
     * KST 시를 바로 쓰지 않는 이유는, "왜 그대로여도 되는가"가 사라지기 때문이다 —
     * 그 근거는 자명하지 않고, 틀렸을 때 조용하다.
     */
    public static int toStoredHour(int kstHour) {
        requireStorageZone();
        return kstHour;
    }

    private static void requireStorageZone() {
        ZoneId actual = ZoneId.systemDefault();
        if (!STORAGE_ZONE.equals(actual)) {
            throw new IllegalStateException(
                    ("JVM 기본 시간대가 %s 입니다. 시간대 필터는 matchAt 이 %s 벽시계로 "
                            + "저장된다는 전제 위에 있고, 그 전제는 BeApplication 의 정적 "
                            + "초기화가 만듭니다. 그 초기화가 사라졌거나 -Duser.timezone 으로 "
                            + "덮였는지 확인해 주세요.").formatted(actual, STORAGE_ZONE));
        }
    }
}
