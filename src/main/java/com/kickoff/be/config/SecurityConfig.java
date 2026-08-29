package com.kickoff.be.config;

import com.kickoff.be.auth.jwt.JwtAuthenticationFilter;
import com.kickoff.be.auth.jwt.JwtTokenProvider;
import com.kickoff.be.auth.jwt.SecurityErrorResponder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityErrorResponder securityErrorResponder;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(securityErrorResponder)
                        .accessDeniedHandler(securityErrorResponder))
                .authorizeHttpRequests(auth -> auth
                        // CORS 프리플라이트
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 개발용 H2 콘솔
                        .requestMatchers("/h2-console/**").permitAll()

                        // 카카오톡 카드형 공유용 브랜딩 이미지. 계약서의 API 표면이 아니라
                        // 정적 자원이다 — 카카오 서버가 미리보기를 만들려고 토큰 없이
                        // 가져가므로 반드시 공개여야 한다.
                        //
                        // <b>메서드를 지정하지 않는다.</b> GET 만 열어 두면 HEAD 가
                        // anyRequest().authenticated() 로 떨어져 401 이 된다 — 스프링 시큐리티는
                        // 메서드를 정확히 대조하지 HEAD 를 GET 으로 쳐 주지 않는다. 이미지
                        // 수집기가 HEAD 로 먼저 찔러 보는 경우가 있어서, 그때 카드에 이미지만
                        // 통째로 빠진다. 우리 쪽 로그에는 401 한 줄이 남을 뿐이라 원인을 찾기
                        // 어렵다. 공개 파일 한 장이라 메서드로 좁혀서 지킬 것도 없다.
                        .requestMatchers("/share-card.png").permitAll()

                        // 인증 불필요 (계약서 §3)
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login",
                                // refresh token 자체가 자격 증명이라 access 없이 부른다 (v1.7.0).
                                // 만료된 access 로 이 경로를 부르는 게 정상 흐름이기도 하다.
                                "/api/auth/refresh",
                                // 전화번호 문자 인증 (계약서 §3-2, v1.15.0). 가입 전에
                                // 부르는 API 라 인증이 있을 수 없다.
                                //
                                // 여기가 막히면 기능만 죽는 게 아니다. FE 는 이 경로를
                                // 헤더 없이 불러 401 이면 "§3-2 를 모르는 옛 서버"로
                                // 판정한다 — permitAll 이 빠지면 신버전이 스스로를 옛
                                // 서버라고 말하는 셈이 되어, 인증 UI 가 통째로 사라진다.
                                "/api/auth/phone/verifications",
                                "/api/auth/phone/verifications/confirm").permitAll()
                        // 소셜 로그인 (계약서 §3-1) — 콜백은 제공자가 부르므로 토큰이 없다
                        .requestMatchers(HttpMethod.GET, "/api/auth/oauth/*/authorize",
                                "/api/auth/oauth/*/callback").permitAll()

                        // "/me" 는 반드시 "/{id}" 패턴보다 먼저 걸어야 한다
                        .requestMatchers(HttpMethod.GET, "/api/teams/me", "/api/posts/me").authenticated()

                        // 인증 불필요 (계약서 §4, §5)
                        // 팀 찾기는 공개다 (계약서 §4, v1.11.0). "/api/teams" 는 정확히
                        // 한 경로라 아래 "/api/teams/me" 인증 규칙을 가리지 않는다.
                        .requestMatchers(HttpMethod.GET, "/api/teams").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/teams/*").permitAll()
                        // 팀 평판은 누구나 본다 (계약서 §7)
                        .requestMatchers(HttpMethod.GET, "/api/teams/*/reviews").permitAll()
                        // 팀 페이지 조회는 공개다 — 상대 팀을 보고 신청할지 정하는 정보라
                        // 로그인 전에도 보여야 한다 (계약서 §4-1). 쓰기는 아래 authenticated
                        .requestMatchers(HttpMethod.GET, "/api/teams/*/members",
                                "/api/teams/*/records").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*").permitAll()

                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
