package com.company.itam.publication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BilingualPdfGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode handover() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {"recipientName":"Nguyễn Văn A","recipientEmail":"user@itam.example",
                 "handoverDate":"2026-09-10","destinationLocationName":"Văn phòng Hà Nội",
                 "lines":[{"assetTag":"LIC-001","name":"Office package","category":"LICENSE","seats":1,
                 "allocations":[{"allocationId":11,"deviceId":7,"deviceTag":"LAP-007","deviceName":"Laptop mẫu","seats":1,"assignmentType":"PER_USER"}]}]}
                """);
    }

    private int[] pixels(ObjectNode snapshot) throws Exception {
        byte[] bytes = new BilingualPdfGenerator().generate("HO-TEST", "HANDOVER", Instant.parse("2026-09-17T00:00:00Z"), "IT Demo", snapshot);
        try (var document = Loader.loadPDF(bytes)) {
            var image = new PDFRenderer(document).renderImage(0);
            return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"recipientName", "recipientEmail", "handoverDate", "destinationLocationName",
            "allocationId", "deviceTag", "deviceName", "seats", "assignmentType"})
    void rendersFrozenHandoverFields(String field) throws Exception {
        var original = handover();
        var changed = original.deepCopy();
        if (changed.has(field)) changed.put(field, "Changed " + field);
        else {
            var allocation = (ObjectNode) changed.path("lines").get(0).path("allocations").get(0);
            if (field.equals("allocationId") || field.equals("seats")) allocation.put(field, 22);
            else allocation.put(field, field.equals("assignmentType") ? "OEM" : "Changed " + field);
        }
        assertThat(Arrays.equals(pixels(original), pixels(changed))).as("PDF must render %s", field).isFalse();
    }

    @Test void paginatesEveryAllocationAndRendersTheLastOne() throws Exception {
        var snapshot = handover();
        snapshot.put("recipientName", "Người nhận có tên rất dài ".repeat(12));
        var allocations = ((ObjectNode) snapshot.path("lines").get(0)).put("seats", 40).putArray("allocations");
        for (int i = 0; i < 40; i++) allocations.addObject().put("allocationId", i + 1).put("seats", 1)
                .put("deviceTag", "LAP-" + i).put("deviceName", "Thiết bị mẫu " + i).put("assignmentType", "OEM");
        var generator = new BilingualPdfGenerator();
        var instant = Instant.parse("2026-09-17T00:00:00Z");
        byte[] bytes = generator.generate("HO-MANY", "HANDOVER", instant, "IT Demo", snapshot);
        String previewPath = System.getProperty("t15.pdf.preview");
        if (previewPath != null) Files.write(Path.of(previewPath), bytes);
        try (var document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            ((ObjectNode) allocations.get(39)).put("allocationId", 999999);
            try (var changed = Loader.loadPDF(generator.generate("HO-MANY", "HANDOVER", instant, "IT Demo", snapshot))) {
                int last = document.getNumberOfPages() - 1;
                var a = new PDFRenderer(document).renderImage(last);
                var b = new PDFRenderer(changed).renderImage(last);
                assertThat(Arrays.equals(a.getRGB(0, 0, a.getWidth(), a.getHeight(), null, 0, a.getWidth()),
                        b.getRGB(0, 0, b.getWidth(), b.getHeight(), null, 0, b.getWidth()))).isFalse();
            }
        }
    }

    @Test
    void createsReadableNonBlankBilingualReportWithAssetData() throws Exception {
        var snapshot = mapper.readTree("""
                {"lines":[{"assetTag":"AST-001","name":"Laptop mẫu","category":"DEVICE",
                "seats":0,"details":{"serialNumber":"SN-001"}}]}
                """);
        byte[] bytes = new BilingualPdfGenerator().generate("HO-001", "HANDOVER", Instant.parse("2026-09-14T01:00:00Z"), "IT Demo", snapshot);
        String previewPath = System.getProperty("t17.pdf.preview");
        if (previewPath != null) Files.write(Path.of(previewPath), bytes);

        assertThat(bytes).startsWith("%PDF".getBytes());
        try (var document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            var image = new PDFRenderer(document).renderImageWithDPI(0, 72);
            long nonWhite = 0;
            for (int y = 0; y < image.getHeight(); y += 8)
                for (int x = 0; x < image.getWidth(); x += 8)
                    if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) nonWhite++;
            assertThat(nonWhite).isGreaterThan(100);
        }
    }

    @Test
    void paginatesLargeTransactions() throws Exception {
        var root = mapper.createObjectNode();
        var lines = root.putArray("assets");
        for (int i = 1; i <= 30; i++) lines.addObject().put("assetTag", "AST-" + i).put("name", "Asset " + i);
        byte[] bytes = new BilingualPdfGenerator().generate("IMP-001", "IMPORT", Instant.now(), "PUR Demo", root);
        try (var document = Loader.loadPDF(bytes)) { assertThat(document.getNumberOfPages()).isEqualTo(3); }
    }
}
