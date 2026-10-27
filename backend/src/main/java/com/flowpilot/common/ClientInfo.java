package com.flowpilot.common;

import jakarta.servlet.http.HttpServletRequest;

/** Network metadata used for rate limiting and audit. IP/UA are sensitive: keep retention short. */
public record ClientInfo(String ip, String userAgent) {
    public static ClientInfo from(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        if (ua != null && ua.length() > 400) ua = ua.substring(0, 400);
        // getRemoteAddr() is already corrected by Tomcat's RemoteIpValve only for trusted proxies
        // (server.forward-headers-strategy=native + internal-proxies). We never parse X-Forwarded-For ourselves.
        return new ClientInfo(req.getRemoteAddr(), ua);
    }
}
