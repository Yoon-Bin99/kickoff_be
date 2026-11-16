package com.kickoff.be.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 엔티티 필드 하나를 암호화해 저장하고 읽을 때 되돌린다 (모집글 입금 계좌, 계약서 §5).
 *
 * <b>스프링 빈이다.</b> 하이버네이트가 변환기를 직접 만들면 {@link FieldCipher} 를 넣을
 * 길이 없다 — 스프링 부트가 하이버네이트에 빈 컨테이너를 꽂아 두기 때문에
 * {@code @Component} 로 두면 그쪽에서 받아 간다.
 *
 * <b>읽을 때 옛 평문을 그대로 돌려준다.</b> 암호화 이전에 저장된 행이 남아 있을 수 있어서,
 * 접두어가 없으면 평문으로 보고 그대로 준다({@link FieldCipher#decrypt}). 이게 없으면
 * 옮기기 전의 글을 읽는 순간 전부 복호화 실패가 된다. <b>쓸 때는 언제나 암호화한다</b> —
 * 그래서 글을 한 번 수정하면 그 행은 자동으로 새 형식이 된다.
 *
 * {@code autoApply} 를 쓰지 않는다. 모든 문자열에 걸리면 닉네임·제목까지 암호화돼
 * 검색·정렬이 통째로 깨진다 — 붙일 자리는 필드마다 {@code @Convert} 로 명시한다.
 */
@Converter
@Component
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldCipher cipher;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    /** 복호화 실패는 null 이다 — 이유는 {@link FieldCipher#decrypt} 에 적었다. */
    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
