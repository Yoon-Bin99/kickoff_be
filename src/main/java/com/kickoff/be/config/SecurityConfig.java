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

                        // 인증 불필요 (계약서 §3)
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        // 소셜 로그인 (계약서 §3-1) — 콜백은 제공자가 부르므로 토큰이 없다
                        .requestMatchers(HttpMethod.GET, "/api/auth/oauth/*/authorize",
                                "/api/auth/oauth/*/callback").permitAll()

                        // "/me" 는 반드시 "/{id}" 패턴보다 먼저 걸어야 한다
                        .requestMatchers(HttpMethod.GET, "/api/teams/me", "/api/posts/me").authenticated()

                        // 인증 불필요 (계약서 §4, §5)
                        .requestMatchers(HttpMethod.GET, "/api/teams/*").permitAll()
                        // 팀 평판은 누구나 본다 (계약서 §7)
                        .requestMatchers(HttpMethod.GET, "/api/teams/*/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*").permitAll()

                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
