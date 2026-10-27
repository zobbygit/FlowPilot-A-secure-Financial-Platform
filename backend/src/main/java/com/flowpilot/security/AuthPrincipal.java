package com.flowpilot.security;

import com.flowpilot.domain.Role;
import java.util.UUID;

/** Identity derived server-side from a validated session. Never built from request data. */
public record AuthPrincipal(UUID userId, UUID sessionId, Role role, String email) {}
