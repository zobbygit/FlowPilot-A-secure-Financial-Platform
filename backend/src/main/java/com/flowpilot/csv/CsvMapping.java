package com.flowpilot.csv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

/** Column mapping + safe value parsing. Anything that does not parse cleanly is reported, never guessed. */
public record CsvMapping(boolean hasHeader, int dateCol, int descCol, Integer amountCol, Integer debitCol, Integer creditCol, Integer refCol, String dateFormat, String decimal) {

    public static final java.util.Map<String, String> DATE_FORMATS = java.util.Map.of("YMD_DASH", "uuuu-MM-dd", "DMY_SLASH", "dd/MM/uuuu", "MDY_SLASH", "MM/dd/uuuu", "DMY_DASH", "dd-MM-uuuu", "DMY_DOT", "dd.MM.uuuu");

    public boolean valid(int columns) {
        boolean amountOk = (amountCol != null) ^ (debitCol != null || creditCol != null);
        for (Integer c : new Integer[]{(Integer) dateCol, (Integer) descCol, amountCol, debitCol, creditCol, refCol}) if (c != null && (c < 0 || c >= columns)) return false;
        return amountOk && DATE_FORMATS.containsKey(dateFormat) && ("." .equals(decimal) || ",".equals(decimal));
    }

    /** Returns null if the text is not a real date in the chosen format (strict: no 31/02, no lenient roll-over). */
    public LocalDate parseDate(String s) {
        try { return LocalDate.parse(s.trim(), DateTimeFormatter.ofPattern(DATE_FORMATS.get(dateFormat), Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)); }
        catch (RuntimeException e) { return null; }
    }

    /** Parses "1,234.50", "(12.00)", "-12,5", "$ 9.99". Returns null when ambiguous or malformed. */
    public BigDecimal parseAmount(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        boolean negative = false;
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length() - 1); }
        s = s.replaceAll("[\\p{Sc}\\s\\u00A0]", "").replaceAll("[A-Za-z]{3}$", "");
        if (s.startsWith("-")) { negative = !negative; s = s.substring(1); } else if (s.startsWith("+")) s = s.substring(1);
        String thousands = decimal.equals(".") ? "," : "\\.";
        s = s.replaceAll(thousands, "").replace(decimal, ".");
        if (!s.matches("^\\d{1,15}(\\.\\d{1,4})?$")) return null;
        BigDecimal v = new BigDecimal(s);
        return negative ? v.negate() : v;
    }

    /** Signed amount for a row: positive = money in, negative = money out. */
    public BigDecimal signedAmount(java.util.List<String> row) {
        if (amountCol != null) return parseAmount(cell(row, amountCol));
        BigDecimal debit = debitCol == null || cell(row, debitCol).isBlank() ? null : parseAmount(cell(row, debitCol));
        BigDecimal credit = creditCol == null || cell(row, creditCol).isBlank() ? null : parseAmount(cell(row, creditCol));
        boolean debitBad = debitCol != null && !cell(row, debitCol).isBlank() && debit == null, creditBad = creditCol != null && !cell(row, creditCol).isBlank() && credit == null;
        if (debitBad || creditBad || (debit != null && credit != null) || (debit == null && credit == null)) return null;
        return debit != null ? debit.abs().negate() : credit.abs();
    }

    public static String cell(java.util.List<String> row, Integer col) { return col == null || col >= row.size() ? "" : row.get(col); }
}
