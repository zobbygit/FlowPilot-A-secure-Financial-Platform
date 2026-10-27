package com.flowpilot.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.Locale;
import org.slf4j.Marker;

/**
 * Last line of defence for logs: any log call whose message (or one of its arguments) contains a database connection
 * string or credential marker is dropped BEFORE it is formatted or written. This covers third-party libraries too
 * (e.g. Flyway's "Database: jdbc:postgresql://<host>/<db>" banner), whatever their log level.
 * Stack traces are not touched so real errors stay debuggable.
 */
public class SecretLogFilter extends TurboFilter {

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (leaks(format)) return FilterReply.DENY;
        if (params != null) {
            for (Object p : params) {
                if (p instanceof CharSequence && leaks(p.toString())) return FilterReply.DENY;
            }
        }
        return FilterReply.NEUTRAL;
    }

    static boolean leaks(String s) {
        if (s == null || s.length() < 8) return false;
        String x = s.toLowerCase(Locale.ROOT);
        return x.contains("jdbc:") || x.contains("postgresql://") || x.contains("postgres://") || x.contains("neon.tech")
                || x.contains("password=") || x.contains("api-key") && x.contains("xkeysib") || x.contains("xkeysib-");
    }
}