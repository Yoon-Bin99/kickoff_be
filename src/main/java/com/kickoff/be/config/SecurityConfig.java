package com.kickoff.be.config;

import com.kickoff.be.auth.jwt.JwtAuthenticationFilter;
import com.kickoff.be.auth.jwt.JwtTokenProvider;
import com.kickoff.be.auth.jwt.SecurityErrorResponder;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityErrorResponder securityErrorResponder;
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * H2 콘솔 경로를 열어 줄지 (개발 전용).
     *
     * <b>프로파일로 가른다.</b> {@code spring.h2.console.enabled} 를 보는 쪽이 더 정확해
     * 보이지만, 그러면 운영에서 그 값을 실수로 켜는 순간 보안 규칙이 따라 열린다 —
     * 스위치 하나가 두 가지를 동시에 푸는 구조가 된다. 프로파일을 기준으로 두면 운영에서는
     * 무엇을 켜도 이 경로가 열리지 않는다.
     *
     * <b>{@code @Value("${spring.profiles.active}")} 로 읽으면 안 된다.</b> 그 프로퍼티는
     * yaml·환경변수로 프로파일을 줄 때만 채워진다. 테스트는 {@code @ActiveProfiles} 로
     * 띄우는데 그 경로는 프로퍼티를 채우지 않아서, 기본값 {@code dev} 로 떨어져
     * <b>테스트가 운영이 아니라 개발 가지를 타게 된다.</b> 실제로 그렇게 짰다가 이 규칙의
     * 테스트가 403 대신 404(= 콘솔 서블릿이 없을 뿐, 보안은 통과)를 받았다.
     * {@link Environment#matchesProfiles} 는 실제 활성 프로파일을 보므로 양쪽에서 맞다.
     */
    private final boolean devProfile;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider,
                          SecurityErrorResponder securityErrorResponder,
                          CorsConfigurationSource corsConfigurationSource,
                          Environment environment) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.securityErrorResponder = securityErrorResponder;
        this.corsConfigurationSource = corsConfigurationSource;
        this.devProfile = environment.matchesProfiles("dev");
    }

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
                .authorizeHttpRequests(auth -> {
                        // H2 콘솔 규칙을 <b>맨 앞에</b> 둔다. 아래 OPTIONS /** permitAll 보다
                        // 뒤에 두면 콘솔 경로의 프리플라이트가 먼저 통과해 버린다.
                        h2Console(auth);

                        auth
                        // CORS 프리플라이트
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

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

                        // 스쿼드(§4-4, v1.28.0)는 여기에 아무 줄도 없다. 읽기도 쓰기도
                        // 아래 anyRequest().authenticated() 로 떨어진다 — 팀 명단·전적
                        // (§4-1)과 달리 <b>조회도 비공개</b>라서다.
                        //
                        // v1.28.0 은 "비로그인도 403"이라는 계약 문면을 맞추려고 GET 을
                        // permitAll 로 열고 서비스가 소속을 보게 했다. 그러면 그 검사 한 줄이
                        // 유일한 방어선이 되는데, v1.28.1 이 비로그인을 <b>401 로 통일</b>해
                        // 그 구조를 걷어냈다 — 토큰이 없으면 여기서 끊기고, 로그인했지만
                        // 비소속이면 서비스가 403 을 낸다.

                        // 구장 디렉터리 (계약서 §8-1, v1.27.0). 가입 전에도 어디서 찰 수
                        // 있는지 볼 수 있어야 한다 — 공개 데이터를 외부 예약 페이지로
                        // 연결하는 것뿐이라 개인정보가 실리지 않는다.
                        .requestMatchers(HttpMethod.GET, "/api/stadiums").permitAll()

                        // 공휴일 (계약서 §8-2). 달력은 로그인 전에도 보이고, 공개된
                        // 공공 데이터라 개인정보가 실리지 않는다.
                        .requestMatchers(HttpMethod.GET, "/api/holidays").permitAll()

                        .anyRequest().authenticated();
                })
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * H2 콘솔 — <b>개발 전용</b>. 운영에서는 명시적으로 막는다.
     *
     * 운영에서 콘솔이 떠 있던 것은 아니다. {@code spring.h2.console.enabled} 가 dev 블록에만
     * {@code true} 라 운영에서는 서블릿이 등록되지 않는다. 그런데 <b>H2 와 h2console 모듈은
     * 운영 이미지에 그대로 들어간다</b> — 의존성이 {@code implementation}·{@code runtimeOnly}
     * 라 {@code bootJar} 의 {@code BOOT-INF/lib} 에 포함된다(직접 확인했다). 즉 막고 있는
     * 것은 프로퍼티 한 줄뿐이고 클래스는 거기 있다.
     *
     * 그 상태에서 {@code permitAll} 을 조건 없이 두면 두 가지가 나쁘다. 프로퍼티가 실수로
     * 켜지는 날 경로가 <b>이미 열려 있다</b>는 것, 그리고 운영 설정을 읽는 사람이 "콘솔이
     * 공개돼 있다"고 읽는다는 것이다. 뒤쪽이 더 자주 비용을 만든다.
     *
     * <b>{@code denyAll} 로 명시하는 이유</b>: 규칙을 아예 빼면 {@code anyRequest()
     * .authenticated()} 로 떨어져 "토큰만 있으면 통과"가 된다. 콘솔은 로그인한 사용자에게도
     * 열려서는 안 되는 경로다.
     */
    private void h2Console(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
                    auth) {
        if (devProfile) {
            auth.requestMatchers("/h2-console/**").permitAll();
        } else {
            auth.requestMatchers("/h2-console/**").denyAll();
        }
    }
}
