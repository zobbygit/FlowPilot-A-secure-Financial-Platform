package com.flowpilot.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects oversized request bodies before they are read: 256 KB for JSON/forms, 12 MB for uploads (the real per-file limits are
 * enforced again by FileValidator). Chunked requests without a Content-Length are bounded by the servlet container's own limits.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestSizeLimitFilter extends OncePerRequestFilter {
    static final long JSON_MAX = 256L * 1024, UPLOAD_MAX = 12L * 1024 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String m = req.getMethod();
        if (m.equals("POST") || m.equals("PUT") || m.equals("PATCH")) {
            String ct = req.getContentType();
            long limit = ct != null && ct.toLowerCase().startsWith("multipart/") ? UPLOAD_MAX : JSON_MAX;
            if (req.getContentLengthLong() > limit) {
                res.setStatus(413);
                res.setContentType("application/json");
                res.getWriter().write("{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"That request is too large.\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}