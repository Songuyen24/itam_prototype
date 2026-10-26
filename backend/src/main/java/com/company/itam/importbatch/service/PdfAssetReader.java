package com.company.itam.importbatch.service;

import com.company.itam.common.exception.AppException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Local-only extraction. Unknown layouts are rejected instead of guessing column meanings. */
@Service
public class PdfAssetReader {
    public static final int MAX_PAGES = 10;
    private static final int MAX_TEXT = 500_000;
    private final Semaphore capacity = new Semaphore(2);
    private final String executable;
    private final String languages;

    public PdfAssetReader(@Value("${itam.ocr.executable:tesseract}") String executable,
                          @Value("${itam.ocr.languages:eng+vie}") String languages) {
        this.executable = executable;
        this.languages = languages;
    }

    public List<Map<String,Object>> read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw error("FILE_EMPTY");
        if (file.getSize() > ExcelHelperService.MAX_FILE_SIZE) throw error("FILE_TOO_LARGE");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".pdf"))
            throw error("OCR_PDF_REQUIRED");
        if (!capacity.tryAcquire()) throw new AppException(HttpStatus.TOO_MANY_REQUESTS,"OCR_BUSY","OCR_BUSY");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) throw error("OCR_INVALID_PDF");
            // shortcut: PDFBox shares this JVM; isolate it in a resource-limited worker before accepting hostile public uploads.
            try (var document = Loader.loadPDF(bytes)) {
                if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent()) throw error("OCR_INVALID_PDF");
                if (document.getNumberOfPages() == 0 || document.getNumberOfPages() > MAX_PAGES) throw error("OCR_PAGE_LIMIT");
                var pages = new ArrayList<String>();
                var stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                int length = 0;
                for (int page = 0; page < document.getNumberOfPages(); page++) {
                    if (System.nanoTime() > deadline) throw error("OCR_TIMEOUT");
                    stripper.setStartPage(page+1); stripper.setEndPage(page+1);
                    var writer = new StringWriter() {
                        @Override public void write(String value, int offset, int count) {
                            if (getBuffer().length() + count > MAX_TEXT) throw error("OCR_TEXT_LIMIT");
                            super.write(value, offset, count);
                        }
                        @Override public void write(char[] value, int offset, int count) {
                            if (getBuffer().length() + count > MAX_TEXT) throw error("OCR_TEXT_LIMIT");
                            super.write(value, offset, count);
                        }
                    };
                    stripper.writeText(document,writer);
                    String text = writer.toString();
                    // A scan may have a text page number; only trust a usable table header.
                    if (!hasHeader(text)) {
                        var box = document.getPage(page).getCropBox();
                        double pixels = box.getWidth() * box.getHeight() * Math.pow(200d / 72, 2);
                        if (!Double.isFinite(pixels) || pixels <= 0 || pixels > 12_000_000) throw error("OCR_PAGE_LIMIT");
                        var image = new PDFRenderer(document).renderImageWithDPI(page, 200);
                        try { text = recognize(image, deadline); } finally { image.flush(); }
                    }
                    length += text.length();
                    if (length > MAX_TEXT) throw error("OCR_TEXT_LIMIT");
                    pages.add(text);
                }
                return parsePages(pages);
            }
        } catch (IOException ex) {
            throw error("OCR_INVALID_PDF");
        } finally { capacity.release(); }
    }

    private String recognize(java.awt.image.BufferedImage image, long deadline) throws IOException {
        Path folder = Files.createTempDirectory("itam-ocr-");
        Process process = null;
        try {
            Path input = folder.resolve("page.png");
            Path output = folder.resolve("result");
            ImageIO.write(image,"png",input.toFile());
            try {
                process = new ProcessBuilder(executable,input.toString(),output.toString(),"-l",languages,"--psm","6")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException ex) { throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,"OCR_UNAVAILABLE","OCR_UNAVAILABLE"); }
            long wait = Math.min(30_000, TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));
            if (wait <= 0 || !process.waitFor(wait,TimeUnit.MILLISECONDS)) throw error("OCR_TIMEOUT");
            Path result = folder.resolve("result.txt");
            if (process.exitValue() != 0 || !Files.isRegularFile(result)) throw error("OCR_FAILED");
            if (Files.size(result) > MAX_TEXT) throw error("OCR_TEXT_LIMIT");
            return Files.readString(result,StandardCharsets.UTF_8);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw error("OCR_TIMEOUT");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(2,TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
            try (var files = Files.list(folder)) { for (Path path : files.toList()) Files.deleteIfExists(path); }
            Files.deleteIfExists(folder);
        }
    }

    boolean hasHeader(String text) {
        return text.lines().anyMatch(line -> header(cells(line)) != null);
    }

    List<Map<String,Object>> parsePages(List<String> pages) {
        var rows = new ArrayList<Map<String,Object>>();
        for (int page = 0; page < pages.size(); page++) {
            List<String> fields = null;
            for (String line : pages.get(page).split("\\R")) {
                if (line.isBlank()) continue;
                String[] cells = cells(line);
                var candidate = header(cells);
                if (candidate != null) { fields = candidate; continue; }
                if (fields == null) continue; // Title before the table, never an asset row.
                if (rows.size() >= ExcelHelperService.MAX_ROWS) throw error("EXCEEDS_MAX_ROWS");
                var row = new LinkedHashMap<String,Object>();
                row.put("rowNumber",rows.size()+2);
                row.put("sourcePage",page+1);
                row.put("sourceText",line.strip());
                // Preserve uncertain lines as errors for manual repair; never silently discard them.
                if (cells.length == fields.size()) {
                    for (int c=0;c<cells.length;c++) row.put(fields.get(c),cells[c].strip().equals("-") ? null : cells[c].strip());
                }
                rows.add(row);
            }
            if (fields == null) throw error("OCR_LAYOUT_UNSUPPORTED");
        }
        if (rows.isEmpty()) throw error("EMPTY_DATA");
        return rows;
    }

    private String[] cells(String line) {
        String value = line.strip();
        if (value.startsWith("|")) value = value.substring(1);
        if (value.endsWith("|")) value = value.substring(0,value.length()-1);
        return value.contains("|") ? value.split("\\|",-1) : value.split("\\t| {2,}",-1);
    }

    private List<String> header(String[] labels) {
        var keys = new ArrayList<String>();
        for (String label : labels) {
            String key = null;
            for (int i=0;i<ExcelHelperService.FIELD_KEYS.length;i++) {
                if (normalize(label).equals(normalize(ExcelHelperService.FIELD_KEYS[i]))
                    || normalize(label).equals(normalize(ExcelHelperService.HEADERS[i]))
                    || normalize(label).equals(normalize(ExcelHelperService.EN_HEADERS[i]))) {
                    key = ExcelHelperService.FIELD_KEYS[i]; break;
                }
            }
            if (key == null || keys.contains(key)) return null;
            keys.add(key);
        }
        return keys.contains("name") && keys.contains("type") ? keys : null;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","")
            .toLowerCase(Locale.ROOT).replace('đ','d').replaceAll("[^a-z0-9]","");
    }
    private AppException error(String code) { return new AppException(HttpStatus.BAD_REQUEST,code,code); }
}
