package com.flowpilot.files;

import com.flowpilot.common.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Trust nothing from the client: the declared MIME type and filename are ignored. The type is decided from the
 * file's own leading bytes, size is re-checked on the real content, and images get a dimension cap
 * (decompression-bomb guard). Stored object keys are generated server-side, never derived from the filename.
 */
@Component
public class FileValidator {
    public enum Kind { JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), WEBP("image/webp", "webp"), PDF("application/pdf", "pdf");
        public final String mime, ext;
        Kind(String mime, String ext) { this.mime = mime; this.ext = ext; }
        public boolean isImage() { return this != PDF; }
    }

    public record Validated(byte[] bytes, Kind kind, String sha256, String safeName) {}

    static final int MAX_PIXELS = 25_000_000;

    public Validated validate(MultipartFile file, java.util.Set<Kind> allowed, long maxBytes) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("FILE_REQUIRED", "Choose a file to upload.");
        if (file.getSize() > maxBytes) throw tooLarge(maxBytes);
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw ApiException.badRequest("FILE_UNREADABLE", "The file could not be read."); }
        return validate(bytes, file.getOriginalFilename(), allowed, maxBytes);
    }

    public Validated validate(byte[] bytes, String originalName, java.util.Set<Kind> allowed, long maxBytes) {
        if (bytes.length == 0) throw ApiException.badRequest("FILE_REQUIRED", "Choose a file to upload.");
        if (bytes.length > maxBytes) throw tooLarge(maxBytes);
        Kind kind = sniff(bytes);
        if (kind == null || !allowed.contains(kind)) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE", "This file type is not allowed. Allowed: " + describe(allowed) + ".");
        if (kind.isImage()) checkImage(bytes, kind);
        else checkPdf(bytes);
        return new Validated(bytes, kind, sha256(bytes), safeName(originalName, kind));
    }

    static Kind sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return Kind.JPEG;
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return Kind.PNG;
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return Kind.WEBP;
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return Kind.PDF;
        return null;
    }

    private static void checkImage(byte[] bytes, Kind kind) {
        if (kind == Kind.WEBP) return;   // no JDK decoder; size + signature checks apply
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw ApiException.badRequest("INVALID_IMAGE", "That image could not be read.");
            ImageReader r = readers.next();
            try {
                r.setInput(in, true, true);
                long pixels = (long) r.getWidth(0) * r.getHeight(0);       // header only: never decodes the full bitmap
                if (pixels > MAX_PIXELS) throw ApiException.badRequest("IMAGE_TOO_LARGE", "That image has too many pixels.");
            } finally { r.dispose(); }
        } catch (IOException e) {
            throw ApiException.badRequest("INVALID_IMAGE", "That image could not be read.");
        }
    }

    /** Rejects PDFs that carry active content. This is a coarse filter, not a malware scanner. */
    private static void checkPdf(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        for (String bad : new String[]{"/JavaScript", "/JS ", "/JS(", "/Launch", "/EmbeddedFile", "/OpenAction", "/AA"}) {
            if (text.contains(bad)) throw ApiException.badRequest("ACTIVE_CONTENT", "PDFs with scripts or embedded files are not accepted.");
        }
    }

    static String safeName(String name, Kind kind) {
        String base = name == null ? "file" : name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        if (base.isBlank() || base.startsWith(".")) base = "file";
        if (base.length() > 80) base = base.substring(0, 80);
        return base.toLowerCase().endsWith("." + kind.ext) ? base : base + "." + kind.ext;
    }

    private static String describe(java.util.Set<Kind> allowed) {
        return allowed.stream().map(k -> k.ext.toUpperCase()).sorted().collect(java.util.stream.Collectors.joining(", "));
    }

    private static ApiException tooLarge(long max) {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "That file is too large. The limit is " + (max / (1024 * 1024)) + " MB.");
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
