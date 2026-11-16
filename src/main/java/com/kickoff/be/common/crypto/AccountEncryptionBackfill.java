package com.kickoff.be.common.crypto;

import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아직 평문으로 남아 있는 모집글 계좌를 암호화 형식으로 옮긴다 (계약서 §5).
 *
 * <b>마이그레이션(SQL)으로 할 수 없는 일이다.</b> 암호화에는 키가 필요하고 키는 애플리케이션만
 * 갖고 있다. 그래서 스키마 변경(V26)은 칸만 넓히고, 값을 옮기는 것은 기동 때 한 번 돈다.
 *
 * <b>여러 번 돌아도 안전하다.</b> 옮길 대상을 "{@code v1:} 로 시작하지 않는 값"으로 고르므로,
 * 이미 옮긴 행은 다음 기동에서 걸리지 않는다. 그래서 재배포마다 조용히 지나간다.
 *
 * <b>JPA 변환기를 타지 않는다.</b> 네이티브 쿼리로 읽고 네이티브 쿼리로 쓴다 — 변환기를
 * 거치면 읽을 때 평문이 그대로 돌아오고(접두어가 없으니) 엔티티에 바뀐 것이 없어서
 * 더티 체킹이 UPDATE 를 내지 않는다. 값이 그대로인데 저장 형식만 바꾸는 일이라
 * <b>엔티티로는 표현되지 않는다.</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEncryptionBackfill implements ApplicationRunner {

    /**
     * 세 칸 중 하나라도 평문인 행. {@code v1:} 로 시작하지 않으면 평문이다.
     *
     * {@code is not null} 을 함께 보는 이유: null 은 "계좌가 없다"라서 옮길 것이 없고,
     * {@code not like} 는 null 에 대해 참이 아니라 unknown 이므로 그것만으로는 걸러지지 않는다.
     */
    private static final String SELECT_PLAINTEXT = """
            select id, bank_name, account_number, account_holder
              from match_posts
             where (bank_name      is not null and bank_name      not like 'v1:%')
                or (account_number is not null and account_number not like 'v1:%')
                or (account_holder is not null and account_holder not like 'v1:%')
            """;

    private static final String UPDATE_ROW = """
            update match_posts
               set bank_name = ?1, account_number = ?2, account_holder = ?3
             where id = ?4
            """;

    private final EntityManager entityManager;
    private final FieldCipher cipher;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(SELECT_PLAINTEXT).getResultList();
        if (rows.isEmpty()) {
            return;
        }
        for (Object[] row : rows) {
            entityManager.createNativeQuery(UPDATE_ROW)
                    .setParameter(1, encryptIfNeeded((String) row[1]))
                    .setParameter(2, encryptIfNeeded((String) row[2]))
                    .setParameter(3, encryptIfNeeded((String) row[3]))
                    .setParameter(4, ((Number) row[0]).longValue())
                    .executeUpdate();
        }
        // 건수만 남긴다. 어느 글인지도 적지 않는다 — 계좌가 있는 글의 목록이 된다.
        log.info("모집글 계좌 {}건을 암호화 형식으로 옮겼다", rows.size());
    }

    /**
     * 한 행 안에서 칸마다 상태가 다를 수 있다 — 글을 수정하다 멈춘 경우다. 이미 암호화된
     * 칸을 다시 싸면 <b>이중 암호화</b>가 되어 읽을 때 {@code v1:...} 라는 평문이 나온다.
     */
    private String encryptIfNeeded(String stored) {
        return FieldCipher.isEncrypted(stored) ? stored : cipher.encrypt(stored);
    }
}
