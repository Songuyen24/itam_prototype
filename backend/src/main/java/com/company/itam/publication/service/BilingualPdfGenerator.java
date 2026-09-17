package com.company.itam.publication.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;

@Component
public class BilingualPdfGenerator {
    static final String TEMPLATE_VERSION = "T17-3";
    private static final int WIDTH = 1240;
    private static final int HEIGHT = 1754;
    private static final int MARGIN = 90;
    private static final int HANDOVER_LINES_PER_PAGE = 34;

    public byte[] generate(String code, String type, Instant issuedAt, String actor, JsonNode snapshot) {
        try (var document = new PDDocument()) {
            var lines = assetLines(snapshot);
            boolean detailed = "HANDOVER".equals(type) || "RECOVERY".equals(type);
            List<TextLayout> detailLines = List.of();
            int offset = 0;
            int total = lines.size();
            do {
                var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics();
                configure(g);
                g.setColor(Color.WHITE); g.fillRect(0, 0, WIDTH, HEIGHT);
                int y = header(g, code, type, issuedAt, actor, document.getNumberOfPages() + 1);
                int end;
                if (detailed) {
                    if (offset == 0) detailLines = "HANDOVER".equals(type)
                            ? handoverLines(g, snapshot, lines) : recoveryLines(g, snapshot, lines);
                    total = detailLines.size();
                    end = Math.min(offset + HANDOVER_LINES_PER_PAGE, total);
                    g.setColor(new Color(25, 30, 35));
                    for (int i = offset; i < end; i++) {
                        detailLines.get(i).draw(g, MARGIN, y);
                        y += 31;
                    }
                } else {
                    end = Math.min(offset + 14, total);
                    for (int i = offset; i < end; i++) y = drawAsset(g, y, i + 1, lines.get(i));
                    if (lines.isEmpty()) drawText(g, "Không có tài sản / No assets", MARGIN, y + 20, 28, false);
                }
                footer(g);
                g.dispose();

                var page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                var pdImage = LosslessFactory.createFromImage(document, image);
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.drawImage(pdImage, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                }
                offset = end;
            } while (offset < total);
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
        String detailsTitle = "HANDOVER".equals(type) ? "CHI TIẾT BÀN GIAO / HANDOVER DETAILS"
                : "RECOVERY".equals(type) ? "CHI TIẾT THU HỒI / RECOVERY DETAILS" : "DANH SÁCH TÀI SẢN / ASSET LIST";
        drawText(g, detailsTitle, MARGIN, 405, 27, true);
        return 445;
    }

    private List<TextLayout> handoverLines(Graphics2D g, JsonNode snapshot, List<AssetLine> assets) {
        var result = new ArrayList<TextLayout>();
        appendWrapped(g, result, "Người nhận / Recipient: " + value(first(snapshot, "recipientName")), true);
        appendWrapped(g, result, "Email: " + value(first(snapshot, "recipientEmail")), false);
        appendWrapped(g, result, "Ngày bàn giao / Handover date: " + value(first(snapshot, "handoverDate")), false);
        appendWrapped(g, result, "Vị trí nhận / Destination: " + value(first(snapshot, "destinationLocationName")), false);
        if (!first(snapshot, "notes").isBlank()) appendWrapped(g, result, "Ghi chú / Notes: " + first(snapshot, "notes"), false);
        appendWrapped(g, result, " ", false);
        appendWrapped(g, result, "DANH SÁCH TÀI SẢN / ASSET LIST", true);
        for (int i = 0; i < assets.size(); i++) {
            var asset = assets.get(i);
            appendWrapped(g, result, (i + 1) + ". " + value(asset.tag) + " - " + value(asset.name), true);
            String category = switch (asset.category) {
                case "DEVICE" -> "Thiết bị / Device";
                case "COMPONENT" -> "Linh kiện / Component";
                case "LICENSE" -> "Gói license / License package";
                default -> value(asset.category);
            };
            appendWrapped(g, result, category + " | Serial: " + value(asset.serial)
                    + (asset.seats != null && asset.seats > 0 ? " | Tổng suất / Total seats: " + asset.seats : ""), false);
            for (var allocation : snapshot.path("lines").path(i).path("allocations")) {
                var allocationLines = new ArrayList<TextLayout>();
                String assignment = first(allocation, "assignmentType");
                if ("PER_USER".equals(assignment)) assignment = "Theo người dùng / Per-user";
                appendWrapped(g, allocationLines, "Cấp phát / Allocation: " + value(first(allocation, "allocationId"))
                        + " | " + value(assignment) + " | Số suất / Seats: " + value(first(allocation, "seats")), false);
                String deviceTag = first(allocation, "deviceTag");
                String deviceName = first(allocation, "deviceName");
                String device = deviceTag.isBlank() ? first(allocation, "deviceId") : deviceTag;
                appendWrapped(g, allocationLines, device.isBlank() && deviceName.isBlank()
                        ? "Cấp trực tiếp cho người nhận / Assigned to recipient"
                        : "Thiết bị / Device: " + value(device) + " - " + value(deviceName), false);
                // Keep an allocation and its device together when the block fits on one page.
                int remaining = HANDOVER_LINES_PER_PAGE - result.size() % HANDOVER_LINES_PER_PAGE;
                if (allocationLines.size() <= HANDOVER_LINES_PER_PAGE && allocationLines.size() > remaining) {
                    for (int blank = 0; blank < remaining; blank++) appendWrapped(g, result, " ", false);
                }
                result.addAll(allocationLines);
            }
            appendWrapped(g, result, " ", false);
        }
        if (assets.isEmpty()) appendWrapped(g, result, "Không có tài sản / No assets", false);
        return result;
    }

    private List<TextLayout> recoveryLines(Graphics2D g, JsonNode snapshot, List<AssetLine> assets) {
        var result = new ArrayList<TextLayout>();
        appendWrapped(g, result, "Người trả / Returner: " + value(first(snapshot, "returnerName")), true);
        appendWrapped(g, result, "Email: " + value(first(snapshot, "returnerEmail")), false);
        appendWrapped(g, result, "Ngày thu hồi / Recovery date: " + value(first(snapshot, "recoveryDate")), false);
        appendWrapped(g, result, "Vị trí nhận / Receiving location: " + value(first(snapshot, "receivingLocationName")), false);
        appendWrapped(g, result, "Lý do / Reason: " + value(first(snapshot, "reason")), false);
        appendWrapped(g, result, " ", false);
        appendWrapped(g, result, "KẾT QUẢ THU HỒI / RECOVERY OUTCOMES", true);
        for (int i = 0; i < assets.size(); i++) {
            var asset = assets.get(i);
            JsonNode source = snapshot.path("lines").path(i);
            appendWrapped(g, result, (i + 1) + ". " + value(asset.tag) + " - " + value(asset.name), true);
            if ("COMPONENT".equals(asset.category)) {
                String action = first(source.path("details"), "componentAction");
                String parent = first(source, "parentAssetId");
                appendWrapped(g, result, "Linh kiện / Component | Quyết định / Decision: " + componentAction(action)
                        + " | Tài sản cha ban đầu / Original parent asset: " + value(parent), false);
            } else if ("LICENSE".equals(asset.category)) {
                appendWrapped(g, result, "Gói license / License package", false);
                for (JsonNode allocation : source.path("allocations")) {
                    JsonNode decision = allocationDecision(source.path("details").path("allocationDecisions"), allocation);
                    String assignment = assignment(first(allocation, "assignmentType"));
                    appendWrapped(g, result, "Cấp phát / Allocation: " + value(first(allocation, "allocationId"))
                            + " | " + assignment + " | Số suất / Seats: " + value(first(allocation, "seats")), false);
                    appendWrapped(g, result, allocationOutcome(allocation, decision), false);
                }
            } else {
                appendWrapped(g, result, "Thiết bị / Device | Serial: " + value(asset.serial), false);
            }
            appendWrapped(g, result, " ", false);
        }
        if (assets.isEmpty()) appendWrapped(g, result, "Không có tài sản / No assets", false);
        return result;
    }

    private JsonNode allocationDecision(JsonNode decisions, JsonNode allocation) {
        String allocationId = first(allocation, "allocationId");
        for (JsonNode decision : decisions) {
            if (allocationId.equals(first(decision.path("before"), "allocationId"))) return decision;
        }
        return decisions.path("_missing");
    }

    private String allocationOutcome(JsonNode allocation, JsonNode decision) {
        String action = first(allocation, "action");
        if (action.isBlank()) action = first(decision, "action");
        JsonNode before = decision.path("before");
        String relationship = value(first(before, "relationshipId"));
        return switch (action) {
            case "RESERVED" -> "Giữ theo thiết bị / Retained with device: " + value(first(before, "deviceTag", "deviceId"))
                    + " | OEM đã giữ / OEM reserved | Quan hệ gốc / Original relationship: " + relationship;
            case "RELEASED" -> "Đã thu hồi / Recovered seats: " + value(first(allocation, "seats"))
                    + " | Người dùng trước đó / Previous user: " + value(first(before, "userName", "userId"))
                    + " | Quan hệ gốc / Original relationship: " + relationship;
            case "UNLINKED" -> "Vẫn thuộc người dùng, gỡ khỏi thiết bị / Still assigned to user, unlinked from device: "
                    + value(first(before, "userName", "userId")) + " / " + value(first(before, "deviceTag", "deviceId"))
                    + " | Quan hệ gốc / Original relationship: " + relationship;
            default -> "Kết quả / Outcome: " + value(action) + " | Quan hệ gốc / Original relationship: " + relationship;
        };
    }

    private String assignment(String assignment) {
        return switch (assignment) {
            case "OEM" -> "OEM";
            case "PER_USER" -> "Theo người dùng / Per-user";
            default -> value(assignment);
        };
    }

    private String componentAction(String action) {
        return switch (action) {
            case "KEEP_ATTACHED" -> "Giữ theo máy / Keep attached";
            case "DETACH" -> "Tách riêng / Detach";
            default -> value(action);
        };
    }

    private void appendWrapped(Graphics2D g, List<TextLayout> lines, String text, boolean bold) {
        for (String paragraph : text.split("\\R", -1)) {
            var attributed = new AttributedString(paragraph.isEmpty() ? " " : paragraph);
            attributed.addAttribute(TextAttribute.FONT, new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 23));
            var iterator = attributed.getIterator();
            var measurer = new LineBreakMeasurer(iterator, g.getFontRenderContext());
            while (measurer.getPosition() < iterator.getEndIndex()) lines.add(measurer.nextLayout(WIDTH - MARGIN * 2));
        }
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
