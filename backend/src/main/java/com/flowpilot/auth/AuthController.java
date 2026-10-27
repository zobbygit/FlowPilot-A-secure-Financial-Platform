package com.flowpilot.auth;

import com.flowpilot.auth.AuthDtos.*;
import com.flowpilot.common.ClientInfo;
import com.flowpilot.domain.Role;
import com.flowpilot.security.AuthPrincipal;
import com.flowpilot.security.CookieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final MessageResponse GENERIC_VERIFY = new MessageResponse(
            "If this address can be registered, a verification link is on its way. Check your inbox.");
    private static final MessageResponse GENERIC_RESET = new MessageResponse(
            "If an account exists for that address, a password-reset link is on its way.");

    private final AuthService auth;
    private final CookieService cookies;

    public AuthController(AuthService auth, CookieService cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    /** SPA fetches this once (and after a CSRF failure) and echoes the token in X-XSRF-TOKEN. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        auth.register(req, ClientInfo.from(http));
        return GENERIC_VERIFY;
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resend(@Valid @RequestBody EmailRequest req, HttpServletRequest http) {
        auth.resendVerification(req.email(), ClientInfo.from(http));
        return GENERIC_VERIFY;
    }

    @PostMapping("/verify-email")
    public MessageResponse verify(@Valid @RequestBody TokenRequest req, HttpServletRequest http) {
        auth.verifyEmail(req.token(), ClientInfo.from(http));
        return new MessageResponse("Email Verified Successfully");
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http, HttpServletResponse res) {
        return signIn(req, Role.USER, http, res);
    }

    @PostMapping("/admin/login")
    public MeResponse adminLogin(@Valid @RequestBody LoginRequest req, HttpServletRequest http, HttpServletResponse res) {
        return signIn(req, Role.ADMIN, http, res);
    }

    private MeResponse signIn(LoginRequest req, Role role, HttpServletRequest http, HttpServletResponse res) {
        AuthService.LoginResult r = auth.login(req, role, ClientInfo.from(http));
        res.addHeader(HttpHeaders.SET_COOKIE, cookies.issue(r.rawToken(), r.maxAge()));
        return r.me();
    }

    /** Public on purpose: always clears the cookie, even if the session is already gone. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest http) {
        auth.logout(principal, ClientInfo.from(http));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return auth.me(principal);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgot(@Valid @RequestBody EmailRequest req, HttpServletRequest http) {
        auth.forgotPassword(req.email(), ClientInfo.from(http));
        return GENERIC_RESET;
    }

    @PostMapping("/reset-password")
    public MessageResponse reset(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        auth.resetPassword(req, ClientInfo.from(http));
        return new MessageResponse("Password updated. Please log in.");
    }
}
