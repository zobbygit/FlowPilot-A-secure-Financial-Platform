package com.flowpilot.payments;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.Map;

/** Renders a QR code as a self-contained SVG (no image library, nothing external). */
final class Qr {
    private Qr() {}

    static String svg(String content) {
        try {
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            StringBuilder path = new StringBuilder();
            for (int y = 0; y < m.getHeight(); y++) for (int x = 0; x < m.getWidth(); x++) if (m.get(x, y)) path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + m.getWidth() + " " + m.getHeight() + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"Payment request QR code\">"
                    + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path d=\"" + path + "\" fill=\"#000\"/></svg>";
        } catch (WriterException e) {
            throw new IllegalStateException("QR generation failed", e);
        }
    }
}
