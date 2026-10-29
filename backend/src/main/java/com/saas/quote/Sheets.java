package com.saas.quote;

import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads the first sheet of an uploaded .xlsx, or a .csv, into rows of cell text. Shared by the user and parts imports. */
final class Sheets {
    private Sheets() {}

    static List<List<String>> grid(MultipartFile f) {
        String fn = f.getOriginalFilename() == null ? "" : f.getOriginalFilename().toLowerCase();
        try {
            List<List<String>> grid = fn.endsWith(".csv") ? csv(f.getInputStream()) : fn.endsWith(".xlsx") ? xlsx(f.getInputStream()) : null;
            if (grid == null) throw bad("Upload the standard template as .xlsx or .csv");
            if (grid.isEmpty()) throw bad("The file is empty");
            return grid;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ResponseStatusException r) throw r;
            throw bad("Could not read the file. Save it as .xlsx (Excel) or .csv and try again.");
        }
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    private static List<List<String>> xlsx(InputStream in) throws IOException {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sh = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();   // keeps mobile numbers as typed instead of 9.8E9
            List<List<String>> rows = new ArrayList<>();
            for (int r = 0; r <= sh.getLastRowNum(); r++) {
                Row_ row = new Row_(sh.getRow(r), fmt);
                rows.add(row.cells());
            }
            return rows;
        }
    }

    private record Row_(org.apache.poi.ss.usermodel.Row row, DataFormatter fmt) {
        List<String> cells() {
            List<String> out = new ArrayList<>();
            if (row == null) return out;
            for (int i = 0; i < Math.max(row.getLastCellNum(), 0); i++) out.add(row.getCell(i) == null ? "" : fmt.formatCellValue(row.getCell(i)));
            return out;
        }
    }

    /** Small CSV reader: quoted fields, doubled quotes, commas and line breaks inside quotes, optional BOM. */
    private static List<List<String>> csv(InputStream in) throws IOException {
        String t = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        if (t.startsWith("\uFEFF")) t = t.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (q) {
                if (ch == '"') { if (i + 1 < t.length() && t.charAt(i + 1) == '"') { sb.append('"'); i++; } else q = false; }
                else sb.append(ch);
            } else if (ch == '"') q = true;
            else if (ch == ',') { cur.add(sb.toString()); sb.setLength(0); }
            else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') i++;
                cur.add(sb.toString()); sb.setLength(0); rows.add(cur); cur = new ArrayList<>();
            } else sb.append(ch);
        }
        if (sb.length() > 0 || !cur.isEmpty()) { cur.add(sb.toString()); rows.add(cur); }
        return rows;
    }

}
