package com.saas.quote;

import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Reads the standard user import template (.xlsx or .csv) and validates every row. */
@Service
public class UserImportService {
    public static final int MAX_ROWS = 200;   // each new user costs one BCrypt hash, so keep a single import quick
    public static final List<String> HEADERS = List.of("Name", "Email", "Mobile", "Designation", "Department", "Role");
    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final Pattern MOBILE = Pattern.compile("^\\+?[0-9][0-9 \\-]{5,18}$");

    public record Row(int row, String name, String email, String mobile, String designation, String department, String role) {}

    public static String digits(String s) { return s == null ? "" : s.replaceAll("\\D", ""); }

    /** Key for "same mobile number": the last 10 digits, so +91 98765 43210, 098765 43210 and 9876543210 all match. */
    public static String mobileKey(String s) { String d = digits(s); return d.length() > 10 ? d.substring(d.length() - 10) : d; }

    /** Shared by single create, edit and bulk import. Returns the problems with these values (empty = fine). */
    public static List<String> check(String name, String email, String mobile, String roleName) {
        List<String> e = new ArrayList<>();
        if (blank(name)) e.add("Name is required");
        if (blank(email)) e.add("Email is required");
        else if (!EMAIL.matcher(email.trim()).matches()) e.add("Email format is invalid");
        if (blank(mobile)) e.add("Mobile number is required");
        else if (!MOBILE.matcher(mobile.trim()).matches() || digits(mobile).length() < 7 || digits(mobile).length() > 15) e.add("Mobile number format is invalid");
        if (blank(roleName)) e.add("Role is required");
        return e;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    public List<Row> read(MultipartFile f) {
        String fn = f.getOriginalFilename() == null ? "" : f.getOriginalFilename().toLowerCase();
        try {
            List<List<String>> grid = fn.endsWith(".csv") ? csv(f.getInputStream()) : fn.endsWith(".xlsx") ? xlsx(f.getInputStream()) : null;
            if (grid == null) throw bad("Upload the standard template as .xlsx or .csv");
            if (grid.isEmpty()) throw bad("The file is empty");
            Map<String, Integer> col = new HashMap<>();
            List<String> head = grid.get(0);
            for (int i = 0; i < head.size(); i++) col.put(norm(head.get(i)), i);
            for (String h : List.of("Name", "Email", "Mobile", "Role"))
                if (!col.containsKey(norm(h))) throw bad("Column \"" + h + "\" is missing. Download the template and use its header row.");
            List<Row> out = new ArrayList<>();
            for (int r = 1; r < grid.size(); r++) {
                List<String> c = grid.get(r);
                if (c.stream().allMatch(x -> x == null || x.isBlank())) continue;   // skip blank lines
                out.add(new Row(r + 1, get(c, col, "Name"), get(c, col, "Email"), get(c, col, "Mobile"),
                        get(c, col, "Designation"), get(c, col, "Department"), get(c, col, "Role")));
                if (out.size() > MAX_ROWS) throw bad("A file can have at most " + MAX_ROWS + " users. Split it and import in parts.");
            }
            if (out.isEmpty()) throw bad("No user rows found under the header row");
            return out;
        } catch (IOException e) {
            throw bad("Could not read the file");
        }
    }

    private static String norm(String s) { return s == null ? "" : s.toLowerCase().replaceAll("[^a-z]", ""); }
    private static String get(List<String> c, Map<String, Integer> col, String h) {
        Integer i = col.get(norm(h));
        return (i == null || i >= c.size() || c.get(i) == null) ? "" : c.get(i).trim();
    }
    private ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    private List<List<String>> xlsx(InputStream in) throws IOException {
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
    private List<List<String>> csv(InputStream in) throws IOException {
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

    public byte[] templateXlsx(List<String> roleNames) throws IOException {
        try (Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Users");
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont(); f.setBold(true); bold.setFont(f);
            org.apache.poi.ss.usermodel.Row h = sh.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) { Cell c = h.createCell(i); c.setCellValue(HEADERS.get(i)); c.setCellStyle(bold); sh.setColumnWidth(i, 6000); }
            String[] ex = {"Asha Patil", "asha.patil@example.com", "9876543210", "Sales Executive", "Sales", roleNames.isEmpty() ? "Sales Executive" : roleNames.get(0)};
            org.apache.poi.ss.usermodel.Row e = sh.createRow(1);
            for (int i = 0; i < ex.length; i++) e.createCell(i).setCellValue(ex[i]);
            Sheet help = wb.createSheet("Instructions");
            String[] lines = {"Name, Email, Mobile and Role are mandatory. Designation and Department are optional.",
                    "Delete the example row before uploading. Do not rename or reorder the header row.",
                    "Email and mobile must be unique. Role must match an existing role name exactly.",
                    "Available roles: " + String.join(", ", roleNames),
                    "Each new user gets a temporary password and must set their own password at first sign-in."};
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            help.setColumnWidth(0, 22000);
            wb.write(bo);
            return bo.toByteArray();
        }
    }

    public byte[] templateCsv(List<String> roleNames) {
        return ("Name,Email,Mobile,Designation,Department,Role\r\nAsha Patil,asha.patil@example.com,9876543210,Sales Executive,Sales,"
                + (roleNames.isEmpty() ? "Sales Executive" : roleNames.get(0)) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
}
