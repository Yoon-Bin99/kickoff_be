package com.kickoff.be.post.entity;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.post.service.MatchAtStorage;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 홈 시간대 필터 (계약서 §5, v1.18.0). 경기 시각의 <b>KST 기준</b> 시각으로 판정한다.
 *
 * 경계는 시작 포함·끝 제외다 — 08:00 정각은 MORNING 이다.
 *
 * <b>NIGHT 만 자정을 넘는다.</b> 22시에 시작해 다음 날 05시에 끝나므로 start > end 이고,
 * 포함 판정도 다른 값들과 반대다 (다른 값은 start ≤ h < end, NIGHT 는 h ≥ start 이거나
 * h < end). 이 하나 때문에 "시작과 끝 사이"라는 단순한 식이 통하지 않는다.
 */
public enum TimeSlot {

    DAWN(5, 8),
    MORNING(8, 12),
    AFTERNOON(12, 18),
    EVENING(18, 22),
    /** 22:00~05:00. 자정을 넘는 유일한 구간이다. */
    NIGHT(22, 5);

    private static final int HOURS_PER_DAY = 24;

    private final int startHour;
    private final int endHour;

    TimeSlot(int startHour, int endHour) {
        this.startHour = startHour;
        this.endHour = endHour;
    }

    public static TimeSlot from(String value) {
        return Arrays.stream(values())
                .filter(slot -> slot.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "알 수 없는 시간대입니다: " + value));
    }

    /**
     * 이 시간대에 해당하는 <b>DB 에 저장된 시(hour) 집합</b>.
     *
     * KST → 저장 시각 변환을 <b>SQL 이 아니라 여기서</b> 한다. 타임존 변환 함수는 H2 와
     * PostgreSQL 에서 동작이 갈리는 대표적인 자리라 아예 쓰지 않는다 — 남는 SQL 기능은
     * 표준 extract(hour ...) 하나뿐이다.
     *
     * 저장 시각이 KST 와 같지 않을 수 있다는 게 핵심이다 ({@link MatchAtStorage} 참고).
     *
     * 슬롯 경계가 모두 정시라서 시 단위 집합으로 정확히 표현된다 — 07:59 는 7 시라 DAWN,
     * 08:00 은 8 시라 MORNING 이다.
     */
    public Set<Integer> storedHours() {
        Set<Integer> hours = new LinkedHashSet<>();
        for (int kstHour = startHour; kstHour != endHour; kstHour = (kstHour + 1) % HOURS_PER_DAY) {
            hours.add(MatchAtStorage.toStoredHour(kstHour));
        }
        return hours;
    }
}
