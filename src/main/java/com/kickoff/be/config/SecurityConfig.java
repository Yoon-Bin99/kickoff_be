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

                        // 약관·개인정보처리방침 (계약서 §3, v1.21.0). 가입하기 <b>전에</b>
                        // 읽는 문서라 토큰이 있을 수 없다. 앱 심사에서도 이 주소를 요구한다.
                        //
                        // share-card 와 같은 이유로 <b>메서드를 지정하지 않는다.</b> 링크
                        // 미리보기나 크롤러가 HEAD 로 먼저 찔러 보는데, GET 만 열어 두면
                        // 그게 401 로 떨어진다 — 스프링 시큐리티는 HEAD 를 GET 으로 쳐 주지
                        // 않는다. share-card 에서 실제로 겪은 일이라 여기서 되풀이하지 않는다.
                        .requestMatchers("/terms", "/privacy").permitAll()

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
                                "/api/auth/phone/verifications/confirm",
                                // 비밀번호 재설정 (계약서 §3-3, v1.23.0). 로그인을 <b>못 하는</b>
                                // 사람이 쓰는 API 라 토큰이 있을 수 없다. 여기가 막히면
                                // 비밀번호를 잊은 사용자가 영영 못 들어온다.
                                "/api/auth/password-reset",
                                "/api/auth/password-reset/confirm",
                                // 웹 OAuth 일회용 코드 교환 (계약서 §3-1, v1.26.0).
                                // 토큰을 <b>받으러</b> 오는 호출이라 토큰이 있을 수 없다.
                                // 코드 자체가 자격 증명이고, 60초·1회용이다.
                                "/api/auth/oauth/exchange").permitAll()
                        // 소셜 로그인 (계약서 §3-1) — 콜백은 제공자가 부르므로 토큰이 없다
                        .requestMatchers(HttpMethod.GET, "/api/auth/oauth/*/authorize",
                                "/api/auth/oauth/*/callback").permitAll()

                        // 가입 폼의 사전 중복 확인 (계약서 §3, v1.16.0). 가입 전에
                        // 부르므로 인증이 있을 수 없다.
                        .requestMatchers(HttpMethod.GET, "/api/auth/availability").permitAll()

                        // 가입 정책 알림 (계약서 §3-2, v1.16.1). 가입 화면을 그리기 전에
                        // 부르므로 인증이 있을 수 없다. 여기가 막히면 FE 는 401 을 받고
                        // "이 경로를 모르는 옛 서버"로 간주해 인증 UI 를 감춘다 — 스위치를
                        // 켠 서버에서도 화면이 인증을 요구하지 않게 되어, 사용자는 가입
                        // 버튼을 눌러야만 400 을 본다.
                        .requestMatchers(HttpMethod.GET, "/api/auth/signup-policy").permitAll()

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

                        // 스쿼드 조회 (계약서 §4-4, v1.28.0). <b>공개가 아니다.</b>
                        // 계약서가 "비소속·비로그인은 403 FORBIDDEN"으로 정했는데, 여기에
                        // authenticated() 를 걸면 토큰 없는 요청이 필터에서 401 로 끊겨
                        // 403 이 될 수 없다. 그래서 통과시키고 SquadService 가 첫 줄에서
                        // 소속을 본다 (TeamAuthz.requireMember — user 가 null 이면 403).
                        //
                        // 그 검사가 빠지면 스쿼드가 통째로 공개된다. 명단(§4-1)과 달리
                        // 스쿼드는 비공개라는 점을 TeamAuthz·SquadController 에도 적어 뒀다.
                        //
                        // <b>쓰기는 여기 넣지 않는다</b> — POST·PUT·DELETE 는 아래
                        // anyRequest().authenticated() 로 떨어져 토큰 없으면 401 이다.
                        .requestMatchers(HttpMethod.GET, "/api/teams/*/squads",
                                "/api/teams/*/squads/*").permitAll()

                        // 구장 디렉터리 (계약서 §8-1, v1.27.0). 가입 전에도 어디서 찰 수
                        // 있는지 볼 수 있어야 한다 — 공개 데이터를 외부 예약 페이지로
                        // 연결하는 것뿐이라 개인정보가 실리지 않는다.
                        .requestMatchers(HttpMethod.GET, "/api/stadiums").permitAll()

                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
