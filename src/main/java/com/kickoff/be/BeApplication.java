package com.kickoff.be;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BeApplication {

    /*
     * 계약서가 오프셋 포함 ISO-8601(+09:00)을 요구하고 Hibernate 도 JVM 기본 시간대로
     * 타임스탬프를 정규화하므로, 호스트 설정과 무관하게 KST 로 고정한다.
     * EntityManagerFactory 가 만들어지기 전에 잡혀야 해서 정적 초기화에 둔다.
     */
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(BeApplication.class, args);
    }

}
