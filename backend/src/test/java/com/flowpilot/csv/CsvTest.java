package com.flowpilot.csv;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.common.ApiException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTest {
    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test
    void parsesQuotedFieldsWithCommasNewlinesAndEscapedQuotes() {
        var rows = CsvParser.parse(b("date,desc,amount\n2026-01-02,\"Coffee, large\",-4.50\n2026-01-03,\"He said \"\"hi\"\"\nthen left\",-1\n"));
        assertEquals(3, rows.size());
        assertEquals("Coffee, large", rows.get(1).get(1));
        assertEquals("He said \"hi\"\nthen left", rows.get(2).get(1));
    }

    @Test
    void detectsSemicolonDelimitersAndStripsBom() {
        var rows = CsvParser.parse(b("\uFEFFdate;desc;amount\n01/02/2026;Rent;-900,00\n"));
        assertEquals(List.of("date", "desc", "amount"), rows.get(0));
        assertEquals("-900,00", rows.get(1).get(2));
    }

    @Test
    void rejectsBinaryUnclosedQuotesBadEncodingAndHugeInputs() {
        assertEquals("CSV_BINARY", assertThrows(ApiException.class, () -> CsvParser.parse(new byte[]{'a', 0, 'b'})).code());
        assertEquals("CSV_MALFORMED", assertThrows(ApiException.class, () -> CsvParser.parse(b("a,b\n\"oops,1\n"))).code());
        assertEquals("CSV_ENCODING", assertThrows(ApiException.class, () -> CsvParser.parse(new byte[]{(byte) 0xC3, (byte) 0x28})).code());
        assertEquals("CSV_TOO_LARGE", assertThrows(ApiException.class, () -> CsvParser.parse(new byte[CsvParser.MAX_BYTES + 1])).code());
        assertEquals("CSV_EMPTY", assertThrows(ApiException.class, () -> CsvParser.parse(new byte[0])).code());
    }

    @Test
    void amountsAndDatesParseStrictly() {
        var m = new CsvMapping(true, 0, 1, 2, null, null, null, "DMY_SLASH", ".");
        assertEquals(0, new BigDecimal("-1234.50").compareTo(m.parseAmount("-1,234.50")));
        assertEquals(0, new BigDecimal("-12.00").compareTo(m.parseAmount("(12.00)")));
        assertEquals(0, new BigDecimal("9.99").compareTo(m.parseAmount("$ 9.99")));
        assertNull(m.parseAmount("12.345678"));
        assertNull(m.parseAmount("1e5"));
        assertNull(m.parseAmount("abc"));
        assertEquals(LocalDate.of(2026, 2, 28), m.parseDate("28/02/2026"));
        assertNull(m.parseDate("31/02/2026"));   // no lenient roll-over
        assertNull(m.parseDate("2026-02-28"));
        var eu = new CsvMapping(true, 0, 1, 2, null, null, null, "DMY_DOT", ",");
        assertEquals(0, new BigDecimal("-1234.50").compareTo(eu.parseAmount("-1.234,50")));
    }

    @Test
    void debitCreditColumnsProduceSignedAmountsAndRejectAmbiguousRows() {
        var m = new CsvMapping(true, 0, 1, null, 2, 3, null, "YMD_DASH", ".");
        assertTrue(m.valid(4));
        assertEquals(0, new BigDecimal("-20").compareTo(m.signedAmount(List.of("2026-01-01", "x", "20", ""))));
        assertEquals(0, new BigDecimal("35").compareTo(m.signedAmount(List.of("2026-01-01", "x", "", "35"))));
        assertNull(m.signedAmount(List.of("2026-01-01", "x", "20", "35")));
        assertNull(m.signedAmount(List.of("2026-01-01", "x", "", "")));
    }

    @Test
    void mappingValidationRejectsOutOfRangeColumnsAndAmbiguousAmountSources() {
        assertFalse(new CsvMapping(true, 0, 1, 9, null, null, null, "YMD_DASH", ".").valid(3));
        assertFalse(new CsvMapping(true, 0, 1, 2, 3, null, null, "YMD_DASH", ".").valid(4));
        assertFalse(new CsvMapping(true, 0, 1, 2, null, null, null, "BAD", ".").valid(3));
    }
}
