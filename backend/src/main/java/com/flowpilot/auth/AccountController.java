package com.flowpilot.auth;

import com.flowpilot.auth.AuthDtos.*;
import com.flowpilot.common.ClientInfo;
import com.flowpilot.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Self-service account security. Identity always comes from the server-side session, never from the URL. */
@RestController
@RequestMapping("/api/users/me")
public class AccountController {
    private final AuthService auth;

    public AccountController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/sessions")
    public List<SessionView> sessions(@AuthenticationPrincipal AuthPrincipal p) {
        return auth.listSessions(p);
    }

    @DeleteMapping("/sessions/{id}")
    public MessageResponse revoke(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, HttpServletRequest http) {
        auth.revokeSession(p, id, ClientInfo.from(http));
        return new MessageResponse("Session revoked.");
    }

    @PostMapping("/sessions/revoke-others")
    public Map<String, Integer> revokeOthers(@AuthenticationPrincipal AuthPrincipal p, HttpServletRequest http) {
        return Map.of("revoked", auth.revokeOtherSessions(p, ClientInfo.from(http)));
    }

    @PostMapping("/password")
    public MessageResponse changePassword(@AuthenticationPrincipal AuthPrincipal p,
                                          @Valid @RequestBody ChangePasswordRequest req, HttpServletRequest http) {
        auth.changePassword(p, req, ClientInfo.from(http));
        return new MessageResponse("Password changed. Other devices were signed out.");
    }
}
