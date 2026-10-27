package com.flowpilot.payments;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class QrTest {
    @Test
    void producesAStandaloneSvgForARequestLink() {
        String svg = Qr.svg("https://app.example.test/app/payments?request=FP-ABCDEFGHJK");
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("viewBox=\"0 0 "));
        assertTrue(svg.contains("<path"));
        assertFalse(svg.contains("<script"));
        assertFalse(svg.contains("payments?request"), "the payload is encoded as modules, never embedded as text");
    }
}
