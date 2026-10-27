package com.flowpilot.security;

import com.flowpilot.config.AppProperties;
import com.flowpilot.domain.AuthSession;
import com.flowpilot.domain.User;
import com.flowpilot.domain.UserStatus;
import com.flowpilot.repo.AuthSessionRepository;
import com.flowpilot.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from the HttpOnly session cookie by looking the hashed token up in
 * PostgreSQL. Revocation, expiry and account suspension therefore take effect on the very next
 * request. Deliberately NOT a Spring bean (avoids double registration in the servlet chain).
 */
public class SessionAuthFilter extends OncePerRequestFilter {
    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final TokenService tokens;
    private final CookieService cookies;
    private final SessionService sessionService;
    private final AppProperties props;

    public SessionAuthFilter(AuthSessionRepository sessions, UserRepository users, TokenService tokens,
                             CookieService cookies, SessionService sessionService, AppProperties props) {
        this.sessions = sessions;
        this.users = users;
        this.tokens = tokens;
        this.cookies = cookies;
        this.sessionService = sessionService;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> raw = cookies.read(req);
        if (raw.isPresent()) authenticate(raw.get());
        chain.doFilter(req, res);
    }

    private void authenticate(String rawToken) {
        AuthSession s = sessions.findByTokenHash(tokens.hash(rawToken)).orElse(null);
        if (s == null || s.getRevokedAt() != null) return;
        Instant now = Instant.now();
        User u = users.findById(s.getUserId()).orElse(null);
        if (u == null) return;
        boolean idleExpired = s.getLastActiveAt().plus(props.sessions().idleTimeout()).isBefore(now);
        boolean absoluteExpired = s.getExpiresAt().isBefore(now);
        if (idleExpired || absoluteExpired) {
            sessionService.expire(s.getId(), u.getRole());
            return;
        }
        if (u.getStatus() != UserStatus.ACTIVE) return;   // suspended/deleted accounts lose access immediately
        if (s.getLastActiveAt().plusSeconds(60).isBefore(now)) sessionService.touch(s.getId());

        AuthPrincipal principal = new AuthPrincipal(u.getId(), s.getId(), u.getRole(), u.getEmail());
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }
}
