package com.flowpilot.auth;

import com.flowpilot.common.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Server-side password rules (the frontend mirrors them for UX only). */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;
    private static final Set<String> COMMON = Set.of("password1234", "123456789012", "qwertyuiop12", "letmein12345", "flowpilot1234");

    private PasswordPolicy() {}

    public static List<String> violations(String password, String email) {
        List<String> v = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) { v.add("Use at least " + MIN_LENGTH + " characters."); return v; }
        if (password.length() > MAX_LENGTH) v.add("Use at most " + MAX_LENGTH + " characters.");
        int classes = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) classes++;
        if (password.chars().anyMatch(Character::isUpperCase)) classes++;
        if (password.chars().anyMatch(Character::isDigit)) classes++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) classes++;
        if (classes < 3) v.add("Mix at least three of: lowercase, uppercase, digits, symbols.");
        if (COMMON.contains(password.toLowerCase())) v.add("That password is too common.");
        if (email != null) {
            String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            if (local.length() >= 4 && password.toLowerCase().contains(local.toLowerCase())) v.add("Don't include your email name in the password.");
        }
        return v;
    }

    public static void requireValid(String password, String email) {
        List<String> v = violations(password, email);
        if (!v.isEmpty()) throw ApiException.badRequest("WEAK_PASSWORD", String.join(" ", v));
    }
}
