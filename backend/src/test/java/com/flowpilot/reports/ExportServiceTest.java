package com.flowpilot.reports;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExportServiceTest {
    @Test
    void csvQuotesCellsAndNeutralisesFormulaInjection() {
        assertEquals("\"=HYPERLINK(\"\"x\"\")\"".replace("\"=", "\"'="), ExportService.line(List.of("=HYPERLINK(\"x\")")));
        assertEquals("\"'@SUM(A1)\",\"a,b\"", ExportService.line(List.of("@SUM(A1)", "a,b")));
        assertEquals("\"'+cmd\"", ExportService.line(List.of("+cmd")));
    }

    @Test
    void legitimateNegativeNumbersAreNotMangled() {
        assertEquals("\"-12.50\",\"3\"", ExportService.line(List.of("-12.50", "3")));
    }
}
