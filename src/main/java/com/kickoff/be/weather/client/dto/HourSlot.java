package com.kickoff.be.weather.client.dto;

/**
 * 단기예보의 한 시각 칸 (계약서 §8-3).
 *
 * 네 값이 서로 다른 항목(SKY·PTY·TMP·POP)으로 따로 오므로 받아 오는 쪽에서 한 칸으로
 * 모은다. 일부만 온 칸이 있을 수 있어 전부 nullable 이다.
 */
public record HourSlot(Integer sky, Integer pty, Integer temperature,
                       Integer precipitationProbability) {

    public HourSlot withSky(Integer value) {
        return new HourSlot(value, pty, temperature, precipitationProbability);
    }

    public HourSlot withPty(Integer value) {
        return new HourSlot(sky, value, temperature, precipitationProbability);
    }

    public HourSlot withTemperature(Integer value) {
        return new HourSlot(sky, pty, value, precipitationProbability);
    }

    public HourSlot withPop(Integer value) {
        return new HourSlot(sky, pty, temperature, value);
    }

    public static HourSlot empty() {
        return new HourSlot(null, null, null, null);
    }
}
