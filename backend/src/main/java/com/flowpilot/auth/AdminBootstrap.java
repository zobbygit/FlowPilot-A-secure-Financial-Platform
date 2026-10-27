package com.flowpilot.auth;

import com.flowpilot.audit.AuditService;
import com.flowpilot.config.AppProperties;
import com.flowpilot.domain.*;
import com.flowpilot.repo.UserProfileRepository;
import com.flowpilot.repo.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN only when BOTH bootstrap variables are supplied and no admin exists. No default admin, no hard-coded
 * credential. If the first admin was created with a wrong email or forgotten password, set FLOWPILOT_ADMIN_BOOTSTRAP_RESET=true
 * for one start: the existing first admin's email and password are replaced from the variables. Remove the variables afterwards.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String EMAIL_RE = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final AppProperties.Admin cfg;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public AdminBootstrap(AppProperties props, UserRepository users, UserProfileRepository profiles, PasswordEncoder encoder, AuditService audit, JdbcTemplate jdbc) {
        this.cfg = props.admin(); this.users = users; this.profiles = profiles; this.encoder = encoder; this.audit = audit; this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (cfg == null || blank(cfg.bootstrapEmail()) || blank(cfg.bootstrapPassword())) return;
        String email = AuthService.normalize(cfg.bootstrapEmail());
        if (!email.matches(EMAIL_RE)) throw new IllegalStateException("FLOWPILOT_ADMIN_BOOTSTRAP_EMAIL is not a valid email address (expected something like admin@example.com).");
        PasswordPolicy.requireValid(cfg.bootstrapPassword(), email);   // refuse weak bootstrap passwords

        if (users.existsByRole(Role.ADMIN)) {
            if (!cfg.bootstrapReset()) {
                log.info("An admin account already exists; bootstrap variables are ignored. (Set FLOWPILOT_ADMIN_BOOTSTRAP_RESET=true for one start to replace its email/password.)");
                return;
            }
            UUID id = jdbc.queryForObject("select id from users where role = 'ADMIN' order by created_at limit 1", UUID.class);
            try {
                jdbc.update("update users set email = ?, password_hash = ?, status = 'ACTIVE', failed_login_attempts = 0, locked_until = null, email_verified_at = coalesce(email_verified_at, now()), password_changed_at = now(), updated_at = now() where id = ?",
                        email, encoder.encode(cfg.bootstrapPassword()), id);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw new IllegalStateException("That email address already belongs to another account. Choose a different FLOWPILOT_ADMIN_BOOTSTRAP_EMAIL.");
            }
            jdbc.update("update auth_sessions set revoked_at = now(), revoked_reason = 'ADMIN_RESET' where user_id = ? and revoked_at is null", id);
            audit.record(Role.ADMIN, "ADMIN_BOOTSTRAP_RESET", true, id, null, null, "email and password replaced from environment");
            log.warn("Admin credentials were RESET from the environment. Sign in at /admin/login with {}. Now remove FLOWPILOT_ADMIN_BOOTSTRAP_* from the environment.", email);
            return;
        }
        User admin = new User(email, encoder.encode(cfg.bootstrapPassword()), Role.ADMIN, UserStatus.ACTIVE);
        admin.setEmailVerifiedAt(Instant.now());
        admin = users.save(admin);
        profiles.save(new UserProfile(admin.getId(), blank(cfg.bootstrapName()) ? "Administrator" : cfg.bootstrapName().trim()));
        audit.record(Role.ADMIN, "ADMIN_BOOTSTRAPPED", true, admin.getId(), null, null, null);
        log.warn("Initial admin created. Sign in at /admin/login with {}. Now remove FLOWPILOT_ADMIN_BOOTSTRAP_* from the environment.", email);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}