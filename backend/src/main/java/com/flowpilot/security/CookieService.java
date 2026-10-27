package com.flowpilot.security;

import com.flowpilot.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieService {
    private final AppProperties.Cookie cfg;

    public CookieService(AppProperties props) {
        this.cfg = props.cookie();
    }

    public Optional<String> read(HttpServletRequest req) {
        jakarta.servlet.http.Cookie[] cookies = req.getCookies();
        if (cookies == null) return Optional.empty();
        for (jakarta.servlet.http.Cookie c : cookies) {
            if (cfg.name().equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    /** HttpOnly so scripts can never read the session credential; SameSite/Secure come from config. */
    public String issue(String rawToken, Duration maxAge) {
        return build(rawToken, maxAge);
    }

    public String clear() {
        return build("", Duration.ZERO);
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(cfg.name(), value)
                .httpOnly(true).secure(cfg.secure()).sameSite(cfg.sameSite())
                .path("/").maxAge(maxAge).build().toString();
    }
}
