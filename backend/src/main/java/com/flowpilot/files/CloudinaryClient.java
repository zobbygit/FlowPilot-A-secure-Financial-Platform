package com.flowpilot.files;

import com.flowpilot.common.ApiException;
import com.flowpilot.config.AppProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Signed Cloudinary calls using the JDK HTTP client (no framework converters in the way, so behaviour is predictable).
 * The API secret never leaves the backend. The host is constant and the cloud name is validated (no SSRF / path tricks).
 * Private documents are type=authenticated raw assets, read back only through this backend after an ownership check.
 * When Cloudinary refuses a request, its own error text is logged and shown (it never contains our secret).
 */
@Component
public class CloudinaryClient {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryClient.class);
    private static final Pattern CLOUD = Pattern.compile("[A-Za-z0-9_-]+");

    private final AppProperties.Cloudinary cfg;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();

    public CloudinaryClient(AppProperties props) { this.cfg = props.cloudinary(); }

    public boolean configured() { return cfg != null && cfg.configured(); }

    /** Cloudinary signature: sorted "k=v&k=v" of the signed params + api secret, SHA-1 hex. */
    static String sign(Map<String, String> params, String secret) {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(params).forEach((k, v) -> { if (v != null && !v.isEmpty()) sb.append(sb.length() > 0 ? "&" : "").append(k).append('=').append(v); });
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest((sb + secret).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** Uploads bytes and returns Cloudinary's secure_url (only meaningful for public type). */
    public String upload(byte[] bytes, String filename, String publicId, String resourceType, String type) {
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        Map<String, String> signed = new TreeMap<>(Map.of("public_id", publicId, "timestamp", ts, "type", type));
        String boundary = "----flowpilot" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length + 1024);
        signed.forEach((k, v) -> field(out, boundary, k, v));
        field(out, boundary, "api_key", cfg.apiKey());
        field(out, boundary, "signature", sign(signed, cfg.apiSecret()));
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename.replaceAll("[\\r\\n\"\\\\]", "_") + "\"\r\nContent-Type: application/octet-stream\r\n\r\n");
        out.writeBytes(bytes);
        write(out, "\r\n--" + boundary + "--\r\n");
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/" + resourceType + "/upload")).timeout(Duration.ofSeconds(40))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
        HttpResponse<String> res = send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw rejected("upload", res.statusCode(), res.body());
        String url = json(res.body(), "secure_url");
        if (url == null) {
            log.warn("Cloudinary upload returned HTTP {} without a secure_url: {}", res.statusCode(), abbreviate(res.body()));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "STORAGE_FAILED", "The storage provider did not return a file address.");
        }
        return url;
    }

    public void delete(String publicId, String resourceType, String type) {
        try {
            String ts = String.valueOf(System.currentTimeMillis() / 1000);
            Map<String, String> signed = new TreeMap<>(Map.of("public_id", publicId, "timestamp", ts, "type", type));
            String form = "public_id=" + enc(publicId) + "&timestamp=" + ts + "&type=" + enc(type) + "&api_key=" + enc(cfg.apiKey()) + "&signature=" + sign(signed, cfg.apiSecret());
            HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/" + resourceType + "/destroy")).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<String> res = send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) log.warn("Cloudinary delete failed: HTTP {} {}", res.statusCode(), abbreviate(res.body()));
        } catch (ApiException e) {
            log.warn("Cloudinary delete skipped: {}", e.getMessage());   // metadata is already marked deleted; an orphan clean-up can retry
        }
    }

    /** Fetches a private (authenticated, raw) asset through the signed download endpoint. The caller must already have checked ownership. */
    public byte[] downloadPrivate(String publicId) {
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        Map<String, String> signed = new TreeMap<>(Map.of("public_id", publicId, "timestamp", ts, "type", "authenticated"));
        String url = base() + "/raw/download?public_id=" + enc(publicId) + "&type=authenticated&timestamp=" + ts + "&api_key=" + enc(cfg.apiKey()) + "&signature=" + sign(signed, cfg.apiSecret());
        HttpResponse<byte[]> res = send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() / 100 != 2) throw rejected("download", res.statusCode(), new String(res.body(), StandardCharsets.UTF_8));
        return res.body();
    }

    // ------------------------------------------------------------------ internals

    private String base() {
        if (!configured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED", "File storage is not configured on this server (set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET and restart).");
        if (!CLOUD.matcher(cfg.cloudName().trim()).matches()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED", "CLOUDINARY_CLOUD_NAME contains invalid characters.");
        return "https://api.cloudinary.com/v1_1/" + cfg.cloudName().trim();
    }

    private <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler) {
        try {
            return http.send(req, handler);
        } catch (IOException e) {
            log.warn("Cannot reach Cloudinary: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "STORAGE_UNREACHABLE", "Could not reach the storage provider (" + e.getClass().getSimpleName() + "). Check the server's internet access.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "STORAGE_UNREACHABLE", "The storage request was interrupted. Please try again.");
        }
    }

    private static ApiException rejected(String what, int status, String body) {
        String msg = json(body, "message");
        log.warn("Cloudinary {} rejected: HTTP {} {}", what, status, abbreviate(body));
        return new ApiException(HttpStatus.BAD_GATEWAY, "STORAGE_REJECTED", "The storage provider refused the request (HTTP " + status + (msg == null ? "" : ": " + abbreviate(msg)) + ").");
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) {
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
    }

    private static void write(ByteArrayOutputStream out, String s) { out.writeBytes(s.getBytes(StandardCharsets.UTF_8)); }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String abbreviate(String s) { return s == null ? "" : s.length() > 300 ? s.substring(0, 300) : s; }

    /** Minimal extraction of a JSON string value (first occurrence). Enough for Cloudinary's flat/simple responses. */
    static String json(String body, String key) {
        if (body == null) return null;
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(body);
        return m.find() ? m.group(1).replace("\\/", "/").replace("\\u0026", "&").replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }
}