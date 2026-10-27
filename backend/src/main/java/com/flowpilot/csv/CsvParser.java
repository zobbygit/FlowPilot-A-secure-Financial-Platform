package com.flowpilot.csv;

import com.flowpilot.common.ApiException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Defensive CSV reader: hard limits on size, rows and columns, UTF-8 only, rejects binary data, handles quoted fields
 * with embedded commas/newlines/escaped quotes, and auto-detects comma / semicolon / tab.
 */
public final class CsvParser {
    public static final int MAX_BYTES = 2 * 1024 * 1024, MAX_ROWS = 5000, MAX_COLS = 30, MAX_CELL = 400;

    private CsvParser() {}

    public static List<List<String>> parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw ApiException.badRequest("CSV_EMPTY", "That file is empty.");
        if (bytes.length > MAX_BYTES) throw ApiException.badRequest("CSV_TOO_LARGE", "CSV files can be at most 2 MB.");
        String text = decode(bytes);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        char delim = detectDelimiter(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') { if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = false; }
                else cell.append(c);
            } else if (c == '"' && cell.length() == 0) quoted = true;
            else if (c == delim) { row.add(finish(cell)); if (row.size() > MAX_COLS) throw tooWide(); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(finish(cell));
                if (!(row.size() == 1 && row.get(0).isEmpty())) { rows.add(row); if (rows.size() > MAX_ROWS + 1) throw ApiException.badRequest("CSV_TOO_MANY_ROWS", "CSV files can have at most " + MAX_ROWS + " rows."); }
                row = new ArrayList<>();
            } else cell.append(c);
            if (cell.length() > MAX_CELL * 4) throw ApiException.badRequest("CSV_MALFORMED", "A cell in this file is far too long.");
        }
        if (quoted) throw ApiException.badRequest("CSV_MALFORMED", "The file has an unclosed quotation mark.");
        if (cell.length() > 0 || !row.isEmpty()) { row.add(finish(cell)); if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row); }
        if (rows.isEmpty()) throw ApiException.badRequest("CSV_EMPTY", "That file has no rows.");
        return rows;
    }

    private static String finish(StringBuilder cell) {
        String s = cell.toString().trim();
        cell.setLength(0);
        return s.length() > MAX_CELL ? s.substring(0, MAX_CELL) : s;
    }

    private static String decode(byte[] bytes) {
        for (byte b : bytes) if (b == 0) throw ApiException.badRequest("CSV_BINARY", "That doesn't look like a text CSV file.");
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw ApiException.badRequest("CSV_ENCODING", "Please save the file as UTF-8 and try again.");
        }
    }

    static char detectDelimiter(String text) {
        int end = text.indexOf('\n'); String head = end < 0 ? text : text.substring(0, end);
        int comma = 0, semi = 0, tab = 0; boolean q = false;
        for (char c : head.toCharArray()) { if (c == '"') q = !q; else if (!q) { if (c == ',') comma++; else if (c == ';') semi++; else if (c == '\t') tab++; } }
        return tab > comma && tab > semi ? '\t' : semi > comma ? ';' : ',';
    }

    private static ApiException tooWide() { return ApiException.badRequest("CSV_TOO_WIDE", "That file has too many columns."); }
}
