package com.saas.quote;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads the parts import template (.xlsx or .csv) and checks every row. Saving is done by PartController. */
@Service
public class PartImportService {
    public static final int MAX_ROWS = 2000;
    public static final List<String> HEADERS = List.of("Part name", "Category", "Make", "Specification", "UOM", "Unit price", "HSN", "Line");

    /** One row of the file: the values as typed, the cleaned values to save, and what is wrong with it. */
    public record Row(int row, String name, String category, String make, String specification, String uom, String unitPriceText,
                      String hsn, String lineText, BigDecimal unitPrice, String line, List<String> errors) {}

    public List<Row> read(MultipartFile f) {
        List<List<String>> grid = Sheets.grid(f);
        Map<String, Integer> col = new HashMap<>();
        List<String> head = grid.get(0);
        for (int i = 0; i < head.size(); i++) col.put(UserImportService.norm(head.get(i)), i);
        if (!col.containsKey(UserImportService.norm("Part name")))
            throw bad("Column \"Part name\" is missing. Download the template and use its header row.");
        List<Row> out = new ArrayList<>();
        for (int r = 1; r < grid.size(); r++) {
            List<String> c = grid.get(r);
            if (c.stream().allMatch(x -> x == null || x.isBlank())) continue;
            out.add(check(r + 1, c, col));
            if (out.size() > MAX_ROWS) throw bad("A file can have at most " + MAX_ROWS + " parts. Split it and import in parts.");
        }
        if (out.isEmpty()) throw bad("No part rows found under the header row");
        return out;
    }

    private Row check(int rowNo, List<String> c, Map<String, Integer> col) {
        String name = UserImportService.get(c, col, "Part name"), category = UserImportService.get(c, col, "Category"),
               make = UserImportService.get(c, col, "Make"), spec = UserImportService.get(c, col, "Specification"),
               uom = UserImportService.get(c, col, "UOM"), priceText = UserImportService.get(c, col, "Unit price"),
               hsn = UserImportService.get(c, col, "HSN"), lineText = UserImportService.get(c, col, "Line");
        List<String> e = new ArrayList<>();
        if (name.isBlank()) e.add("Part name is required");
        BigDecimal price = BigDecimal.ZERO;
        String p = priceText.replaceAll("[₹,\\s]|Rs\\.?", "");
        if (!p.isBlank()) {
            try { price = new BigDecimal(p); if (price.signum() < 0) e.add("Unit price cannot be negative"); }
            catch (NumberFormatException x) { e.add("Unit price \"" + priceText + "\" is not a number"); }
        }
        if (!hsn.isBlank() && !hsn.matches("\\d{4,8}")) e.add("HSN / SAC code must be 4 to 8 digits");
        String line = line(lineText);
        if (line == null) e.add("Line must be Solar, Genset or Both");
        return new Row(rowNo, name, category, make, spec, uom, priceText, hsn, lineText, price, line == null ? Part.BOTH : line, e);
    }

    /** Solar / Genset / Both (or empty = Both); also accepts "Solar EPC", "DG", "gen set". Null when not recognised. */
    static String line(String s) {
        String v = s == null ? "" : s.trim().toLowerCase().replaceAll("[^a-z]", "");
        if (v.isEmpty() || v.equals("both") || v.equals("all")) return Part.BOTH;
        if (v.startsWith("solar")) return BusinessLine.SOLAR.name();
        if (v.startsWith("genset") || v.equals("dg") || v.startsWith("dgset") || v.startsWith("generator")) return BusinessLine.GENSET.name();
        return null;
    }

    public byte[] templateXlsx(List<String> categories) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Parts");
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont(); f.setBold(true); bold.setFont(f);
            int[] widths = {9000, 4500, 4500, 9000, 3000, 4000, 3500, 3500};
            org.apache.poi.ss.usermodel.Row h = sh.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) { Cell c = h.createCell(i); c.setCellValue(HEADERS.get(i)); c.setCellStyle(bold); sh.setColumnWidth(i, widths[i]); }
            String[][] ex = {
                {"AMF panel 125 kVA", "Panel", "Accurate", "Auto mains failure panel with ATS", "Nos.", "95000", "8537", "Genset"},
                {"Mono PERC module 540 Wp", "Module", "Waaree", "Bifacial, 144 cells", "Nos.", "14500", "8541", "Solar"},
                {"Power cable 3.5C x 70 sq mm", "Cable", "Polycab", "Armoured, aluminium", "Mtr", "850", "8544", "Both"}};
            for (int r = 0; r < ex.length; r++) {
                org.apache.poi.ss.usermodel.Row row = sh.createRow(r + 1);
                for (int i = 0; i < ex[r].length; i++) {
                    if (i == 5) row.createCell(i).setCellValue(Double.parseDouble(ex[r][i])); else row.createCell(i).setCellValue(ex[r][i]);
                }
            }
            Sheet help = wb.createSheet("Instructions");
            String[] lines = {
                "Part name is mandatory. All other columns are optional. Delete the example rows before uploading.",
                "Do not rename or reorder the header row.",
                "Line: Solar, Genset or Both (empty means Both). A Genset product can only use Genset and Both parts.",
                "Unit price: a number without GST, for example 14500 or 14,500.",
                "HSN / SAC: 4 to 8 digits.",
                "A part is identified by Part name + Make. With \"Update existing parts\" ticked, matching rows update that part's details and price;",
                "otherwise they are reported as already existing and skipped.",
                "Categories already in use: " + (categories.isEmpty() ? "-" : String.join(", ", categories))};
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            help.setColumnWidth(0, 30000);
            wb.write(bo);
            return bo.toByteArray();
        }
    }

    public byte[] templateCsv() {
        return ("Part name,Category,Make,Specification,UOM,Unit price,HSN,Line\r\n"
                + "AMF panel 125 kVA,Panel,Accurate,Auto mains failure panel with ATS,Nos.,95000,8537,Genset\r\n"
                + "Mono PERC module 540 Wp,Module,Waaree,\"Bifacial, 144 cells\",Nos.,14500,8541,Solar\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
