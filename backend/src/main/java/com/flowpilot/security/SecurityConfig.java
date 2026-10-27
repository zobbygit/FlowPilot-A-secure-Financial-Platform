package com.flowpilot.security;

import com.flowpilot.config.AppProperties;
import com.flowpilot.repo.AuthSessionRepository;
import com.flowpilot.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Authentication is done by SessionAuthFilter. This stops Spring Boot creating a default in-memory user with a random password. */
    @Bean
    UserDetailsService noDefaultUser() {
        return username -> { throw new UsernameNotFoundException("not used"); };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AppProperties props, AuthSessionRepository sessions,
                                    UserRepository users, TokenService tokens, CookieService cookies,
                                    SessionService sessionService) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieName("fp_csrf");   // unique name: other dev apps on localhost also use XSRF-TOKEN and can overwrite it
        csrfRepo.setCookieCustomizer(b -> b.secure(props.cookie().secure())
                .sameSite(props.cookie().sameSite()).path("/"));
        // Plain (non-XOR) handler: the SPA reads the token from GET /api/auth/csrf and echoes it in X-XSRF-TOKEN.
        var csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
            .cors(Customizer.withDefaults())
            .csrf(c -> c.csrfTokenRepository(csrfRepo).csrfTokenRequestHandler(csrfHandler))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .logout(l -> l.disable())
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                        writeError(res, 401, "UNAUTHENTICATED", "Please sign in to continue."))
                .accessDeniedHandler((req, res, ex) -> {
                    if (ex instanceof CsrfException) {
                        boolean cookie = req.getCookies() != null && java.util.Arrays.stream(req.getCookies()).anyMatch(c -> "fp_csrf".equals(c.getName()));
                        log.warn("CSRF rejected: {} {} | contentType={} | headerPresent={} | csrfCookiePresent={} | origin={}", req.getMethod(), req.getRequestURI(), req.getContentType(),
                                req.getHeader("X-XSRF-TOKEN") != null, cookie, req.getHeader("Origin"));
                        writeError(res, 403, "CSRF_INVALID", "Security token missing or invalid. Please retry.");
                    }
                    else writeError(res, 403, "FORBIDDEN", "You do not have access to this resource.");
                }))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/login", "/api/auth/admin/login",
                        "/api/auth/verify-email", "/api/auth/resend-verification", "/api/auth/forgot-password",
                        "/api/auth/reset-password", "/api/auth/logout").permitAll()
                .requestMatchers("/api/admin/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("ADMIN")
                .requestMatchers("/api/**", "/ws").authenticated()
                .anyRequest().denyAll())
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .addFilterBefore(new SessionAuthFilter(sessions, users, tokens, cookies, sessionService, props),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(props.cors().allowedOrigins());           // explicit origins only
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key"));
        c.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        c.setAllowCredentials(true);
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", c);
        return src;
    }

    private static void writeError(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    /** Forces the deferred CSRF token to be generated so the cookie is always set (Spring Security SPA pattern). */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(req, res);
        }
    }
}