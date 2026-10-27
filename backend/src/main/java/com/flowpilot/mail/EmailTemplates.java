package com.flowpilot.mail;

/** Plain, accessible transactional templates. All interpolated user text is HTML-escaped. */
public final class EmailTemplates {
    private EmailTemplates() {}

    public static OutgoingEmail verification(String toEmail, String name, String link, long ttlHours) {
        String text = "Hi " + name + ",\n\nConfirm your email to activate your FlowPilot account:\n" + link
                + "\n\nThis link works once and expires in " + ttlHours + " hours. If you didn't create an account, ignore this email.";
        String html = wrap("Verify your email",
                "Hi " + esc(name) + ", confirm your email to activate your FlowPilot account.",
                "Verify email", link,
                "This link works once and expires in " + ttlHours + " hours. If you didn't create an account, you can ignore this message.");
        return new OutgoingEmail(toEmail, name, "Verify your FlowPilot email", html, text);
    }

    public static OutgoingEmail passwordReset(String toEmail, String name, String link, long ttlMinutes) {
        String text = "Hi " + name + ",\n\nReset your FlowPilot password:\n" + link
                + "\n\nThis link works once and expires in " + ttlMinutes + " minutes. If you didn't ask for this, ignore this email - your password is unchanged.";
        String html = wrap("Reset your password",
                "Hi " + esc(name) + ", we received a request to reset your FlowPilot password.",
                "Choose a new password", link,
                "This link works once and expires in " + ttlMinutes + " minutes. If you didn't request it, ignore this message - your password is unchanged.");
        return new OutgoingEmail(toEmail, name, "Reset your FlowPilot password", html, text);
    }

    private static String wrap(String title, String lead, String cta, String link, String footer) {
        return "<div style=\"font-family:system-ui,Segoe UI,Arial,sans-serif;max-width:480px;margin:auto;padding:24px;"
                + "background:#0b0716;color:#f4f1ff;border-radius:12px\">"
                + "<h1 style=\"font-size:20px;margin:0 0 12px\">" + esc(title) + "</h1>"
                + "<p style=\"line-height:1.5\">" + lead + "</p>"
                + "<p><a href=\"" + esc(link) + "\" style=\"display:inline-block;padding:12px 20px;background:#7c3aed;"
                + "color:#fff;border-radius:8px;text-decoration:none;font-weight:600\">" + esc(cta) + "</a></p>"
                + "<p style=\"font-size:13px;color:#b8afd6;line-height:1.5\">" + esc(footer) + "</p></div>";
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
