package com.kickoff.be.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 금칙어 검사 (계약서 §7-2 「금칙어」, v1.35.0).
 *
 * <b>좁게 잡는다.</b> 걸러야 할 글은 신고로도 걸러지지만, 멀쩡한 글이 막히는 일은
 * 되돌릴 방법이 없다 — 사용자는 무엇이 문제인지 모른 채 글을 못 쓴다. 목록의 기준은
 * 리소스 파일 머리말에 적어 두었다.
 *
 * <b>한글과 영문을 다르게 본다.</b> 한글은 부분 일치(공백을 지우고 본다 — "씨 발"도
 * 같은 말이다), 영문은 <b>단어 경계</b>다. 영문을 부분 일치로 두면 "ass" 하나가
 * class·pass·bass 를 전부 막는다.
 *
 * 이미 저장된 것에는 소급하지 않는다 — 저장할 때만 본다.
 */
@Component
public class ProfanityFilter {

    private static final String RESOURCE = "profanity.txt";
    private static final String MESSAGE = "사용할 수 없는 표현이 포함되어 있습니다.";

    /** 공백·점·하이픈처럼 사이에 끼워 넣어 피해 가는 글자들. */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-_*·]+");

    private static final Pattern ASCII_WORD = Pattern.compile("^[a-z]+$");

    private final List<String> koreanWords;
    private final List<Pattern> asciiWords;

    public ProfanityFilter() {
        List<String> korean = new ArrayList<>();
        List<Pattern> ascii = new ArrayList<>();
        for (String word : read()) {
            if (ASCII_WORD.matcher(word).matches()) {
                ascii.add(Pattern.compile("\\b" + Pattern.quote(word) + "\\b"));
            } else {
                korean.add(word);
            }
        }
        this.koreanWords = List.copyOf(korean);
        this.asciiWords = List.copyOf(ascii);
        // 기동에서 멈추는 쪽을 택한다. 목록이 비면 검사가 통째로 없는 것과 같은데,
        // 에러 없이 전부 통과해서 한참 모른다.
        if (koreanWords.isEmpty() && asciiWords.isEmpty()) {
            throw new IllegalStateException(RESOURCE + " 에서 금칙어를 하나도 못 읽었습니다.");
        }
    }

    /**
     * 걸리면 400 을 던진다 (계약서 §7-2).
     *
     * @param field 어느 칸이 문제인지 — FE 가 그 입력창을 가리켜야 한다
     */
    public void check(String field, String value) {
        if (contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    List.of(new ErrorResponse.FieldError(field, MESSAGE)));
        }
    }

    public boolean contains(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = SEPARATORS.matcher(value.toLowerCase(Locale.ROOT))
                .replaceAll("");
        for (String word : koreanWords) {
            if (normalized.contains(word)) {
                return true;
            }
        }
        // 영문은 원문에서 단어 경계로 본다 — 공백을 지운 문자열에서 보면 "a shit" 이
        // "ashit" 이 되어 경계가 사라진다.
        String lower = value.toLowerCase(Locale.ROOT);
        return asciiWords.stream().anyMatch(pattern -> pattern.matcher(lower).find());
    }

    private List<String> read() {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(RESOURCE + " 를 찾을 수 없습니다.");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(line -> line.toLowerCase(Locale.ROOT))
                        .distinct()
                        .toList();
            }
        } catch (IOException e) {
            throw new IllegalStateException(RESOURCE + " 를 읽지 못했습니다.", e);
        }
    }
}
