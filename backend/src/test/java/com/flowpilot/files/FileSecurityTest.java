package com.flowpilot.files;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.common.ApiException;
import com.flowpilot.files.FileValidator.Kind;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FileSecurityTest {
    private final FileValidator validator = new FileValidator();
    private static final Set<Kind> ALL = Set.of(Kind.JPEG, Kind.PNG, Kind.WEBP, Kind.PDF);

    @Test
    void cloudinarySignatureMatchesTheDocumentedExample() {
        String sig = CloudinaryClient.sign(Map.of("eager", "w_400,h_300,c_pad|w_260,h_200,c_crop", "public_id", "sample_image", "timestamp", "1315060510"), "abcd");
        assertEquals("bfd09f95f331f558cbd1320e67aa8d488770583e", sig);
    }

    @Test
    void typeIsDecidedFromContentNotFromTheFilename() {
        byte[] exe = "MZ\u0090\u0000not an image".getBytes(StandardCharsets.ISO_8859_1);
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(exe, "invoice.pdf", ALL, 1000));
        assertEquals("UNSUPPORTED_FILE", ex.code());
    }

    @Test
    void acceptsARealPdfSignatureAndSanitisesTheName() {
        byte[] pdf = "%PDF-1.7\n1 0 obj<<>>endobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
        var v = validator.validate(pdf, "../../etc/Pass wd?.pdf", ALL, 10_000);
        assertEquals(Kind.PDF, v.kind());
        assertEquals("Pass wd_.pdf", v.safeName());
        assertEquals(64, v.sha256().length());
    }

    @Test
    void rejectsPdfsWithActiveContent() {
        byte[] bad = "%PDF-1.7\n<< /OpenAction << /S /JavaScript /JS (app.alert(1)) >> >>".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("ACTIVE_CONTENT", assertThrows(ApiException.class, () -> validator.validate(bad, "a.pdf", ALL, 10_000)).code());
    }

    @Test
    void enforcesSizeAndTheAllowList() {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertEquals("FILE_TOO_LARGE", assertThrows(ApiException.class, () -> validator.validate(png, "a.png", ALL, 4)).code());
        assertEquals("UNSUPPORTED_FILE", assertThrows(ApiException.class, () -> validator.validate("%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1), "a.pdf", Set.of(Kind.PNG), 1000)).code());
        assertEquals("FILE_REQUIRED", assertThrows(ApiException.class, () -> validator.validate(new byte[0], "a", ALL, 10)).code());
    }
}
