package com.company.itam.publication.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class BilingualPdfGenerator {
    static final String TEMPLATE_VERSION = "T17-1";
    private static final int WIDTH = 1240;
    private static final int HEIGHT = 1754;
    private static final int MARGIN = 90;

    public byte[] generate(String code, String type, Instant issuedAt, String actor, JsonNode snapshot) {
        try (var document = new PDDocument()) {
            var lines = assetLines(snapshot);
            int offset = 0;
            do {
                var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics();
                configure(g);
                g.setColor(Color.WHITE); g.fillRect(0, 0, WIDTH, HEIGHT);
                int y = header(g, code, type, issuedAt, actor, offset / 14 + 1);
                int end = Math.min(offset + 14, lines.size());
                for (int i = offset; i < end; i++) y = drawAsset(g, y, i + 1, lines.get(i));
                if (lines.isEmpty()) drawText(g, "Không có tài sản / No assets", MARGIN, y + 20, 28, false);
                footer(g);
                g.dispose();

                var page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                var pdImage = LosslessFactory.createFromImage(document, image);
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.drawImage(pdImage, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                }
                offset = end;
            } while (offset < lines.size());
            var output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot generate transaction PDF", ex);
        }
    }

    private void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private int header(Graphics2D g, String code, String type, Instant issuedAt, String actor, int page) {
        drawText(g, title(type), MARGIN, 105, 34, true);
        drawText(g, "BIÊN BẢN GIAO DỊCH / TRANSACTION REPORT", MARGIN, 150, 25, true);
        g.setColor(new Color(45, 55, 65)); g.fillRect(MARGIN, 175, WIDTH - MARGIN * 2, 3);
        drawText(g, "Mã phiếu / Transaction code: " + value(code), MARGIN, 225, 24, false);
        drawText(g, "Loại / Type: " + value(type), MARGIN, 263, 24, false);
        drawText(g, "Ngày phát hành / Issued: " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.systemDefault()).format(issuedAt), MARGIN, 301, 24, false);
        drawText(g, "Người xử lý / Actor: " + value(actor), MARGIN, 339, 24, false);
        drawText(g, "Trang / Page " + page, WIDTH - 250, 339, 21, false);
        drawText(g, "DANH SÁCH TÀI SẢN / ASSET LIST", MARGIN, 405, 27, true);
        return 445;
    }

    private int drawAsset(Graphics2D g, int y, int number, AssetLine line) {
        g.setColor(number % 2 == 0 ? new Color(244, 247, 249) : Color.WHITE);
        g.fillRect(MARGIN, y, WIDTH - MARGIN * 2, 72);
        g.setColor(new Color(215, 220, 225)); g.drawRect(MARGIN, y, WIDTH - MARGIN * 2, 72);
        int available = WIDTH - MARGIN * 2 - 36;
        drawTextFit(g, number + ". " + value(line.tag) + " - " + value(line.name), MARGIN + 18, y + 29, 23, true, available);
        drawTextFit(g, "Loại / Category: " + value(line.category) + "   |   Serial: " + value(line.serial)
                + (line.seats == null || line.seats <= 0 ? "" : "   |   Số suất / Seats: " + line.seats), MARGIN + 18, y + 59, 20, false, available);
        return y + 76;
    }

    private void footer(Graphics2D g) {
        int y = HEIGHT - 190;
        drawText(g, "ĐẠI DIỆN BÊN GIAO / ISSUING PARTY", MARGIN, y, 21, true);
        drawText(g, "ĐẠI DIỆN BÊN NHẬN / RECEIVING PARTY", WIDTH / 2 + 50, y, 21, true);
        drawText(g, "Ký và ghi rõ họ tên / Signature and full name", MARGIN, y + 42, 18, false);
        drawText(g, "Ký và ghi rõ họ tên / Signature and full name", WIDTH / 2 + 50, y + 42, 18, false);
        drawText(g, "Biên bản mẫu dùng cho prototype / Prototype sample report", MARGIN, HEIGHT - 42, 17, false);
    }

    private List<AssetLine> assetLines(JsonNode snapshot) {
        JsonNode source = snapshot.path("lines");
        if (!source.isArray()) source = snapshot.path("assets");
        var result = new ArrayList<AssetLine>();
        if (!source.isArray()) return result;
        for (JsonNode node : source) {
            JsonNode input = node.path("input").isObject() ? node.path("input") : node;
            JsonNode details = node.path("details");
            String tag = first(input, "assetTag", "asset_tag");
            String name = first(input, "name");
            String category = first(node, "category");
            if (category.isBlank()) category = first(input.path("type"), "category", "name");
            String serial = first(input, "serialNumber", "serial_number");
            if (serial.isBlank()) serial = first(details, "serialNumber", "serial_number");
            Integer seats = null;
            if (node.hasNonNull("seats")) seats = node.path("seats").asInt();
            else if (input.hasNonNull("seatCount")) seats = input.path("seatCount").asInt();
            else if (input.path("license").hasNonNull("seatCount")) seats = input.path("license").path("seatCount").asInt();
            result.add(new AssetLine(tag, name, category, serial, seats));
        }
        return result;
    }

    private String first(JsonNode node, String... fields) {
        for (String field : fields) if (node.hasNonNull(field) && !node.path(field).asText().isBlank()) return node.path(field).asText();
        return "";
    }

    private String title(String type) {
        return switch (type) {
            case "IMPORT" -> "BIÊN BẢN NHẬP KHO / RECEIVING REPORT";
            case "HANDOVER" -> "BIÊN BẢN BÀN GIAO / HANDOVER REPORT";
            case "RECOVERY" -> "BIÊN BẢN THU HỒI / RECOVERY REPORT";
            case "DISPOSAL" -> "BIÊN BẢN THANH LÝ / DISPOSAL REPORT";
            default -> "BIÊN BẢN / REPORT";
        };
    }

    private void drawText(Graphics2D g, String text, int x, int y, int size, boolean bold) {
        g.setColor(new Color(25, 30, 35));
        g.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
        g.drawString(text, x, y);
    }

    private void drawTextFit(Graphics2D g, String text, int x, int y, int size, boolean bold, int maxWidth) {
        g.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
        if (g.getFontMetrics().stringWidth(text) > maxWidth) {
            String suffix = "...";
            int end = text.length();
            while (end > 1 && g.getFontMetrics().stringWidth(text.substring(0, end) + suffix) > maxWidth) end--;
            text = text.substring(0, end) + suffix;
        }
        drawText(g, text, x, y, size, bold);
    }

    private String value(String value) { return value == null || value.isBlank() ? "-" : value; }
    private record AssetLine(String tag, String name, String category, String serial, Integer seats) {}
}
