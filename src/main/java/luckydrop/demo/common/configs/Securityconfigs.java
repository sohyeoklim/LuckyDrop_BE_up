package luckydrop.demo.common.configs;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.common.auth.JwtAuthFilter;
import luckydrop.demo.common.auth.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
//@EnableMethodSecurity
@EnableWebSecurity
@RequiredArgsConstructor
public class Securityconfigs {

    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @Bean
    public SecurityFilterChain myFilter(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .cors(cors->cors.configurationSource(corsConfigurationSource())) // 같은 도메인이 아니면 통신이 되지 않음. 그래서 이것을 통해 예외적으로 프론트를 허용하게 만듦.
                .csrf(AbstractHttpConfigurer::disable) // csrf 비활성화 (코드나 서비스로 방어할 수 있는 부분이 많아 비활성화 함)
                .httpBasic(AbstractHttpConfigurer::disable) // Http basic 비활성화 (토큰 기반 인증처리를 하기 때문에 자동적으로 켜지는 basic 비활성화)
                // 특정 url 패턴에 대해서는 Authentication 객체 요구하지 않음. (인증처리 제외)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/ticket/adjust").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/draws/*/draw").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/draws/create").hasAnyRole("HOST", "ADMIN")
                        .requestMatchers("/api/ticket/**").authenticated()

                        .requestMatchers("/api/user/create", "/api/user/login", "/api/user/logout",
                        "/api/user/token/reissue",
                        "/oauth2/**", "/login/oauth2/**", "/login", "/error",
                        "/api/signup/email", "/api/signup/emailAuth",
                        "/uploads/**",
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                        "/api/draws", "/api/draws/hot-banner", "/api/draws/stats", "/api/draws/events",
                        "/api/user/forgot-password", "/api/user/verify-reset-code", "/api/user/reset-password", "/api/user/change-password",
                        "/api/user/check-nickname", "/api/user/check-email")
                        .permitAll()

                        .anyRequest().authenticated())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // OAuth 요청의 state 검증에는 세션을 쓰되, 로그인 상태는 JWT로만 유지한다.
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .oauth2Login(oauth -> oauth.successHandler(oauth2LoginSuccessHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173"));
        configuration.setAllowedMethods(Arrays.asList("*")); // 모든 HTTP메서드 허용
        configuration.setAllowedHeaders(Arrays.asList("*")); // 모든 헤더값 허용
        configuration.setAllowCredentials(true); // 자격증명 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // 모든 url 패턴에 대해 cors 허용 설정
        return source;
    }

}
