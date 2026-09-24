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

    private ObjectNode recovery() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {"returnerName":"Nguyễn Văn A","returnerEmail":"user@itam.example","recoveryDate":"2026-09-17",
                 "receivingLocationName":"Kho Hà Nội","reason":"Nghỉ việc", "lines":[
                 {"assetTag":"RAM-001","name":"Memory","category":"COMPONENT","parentAssetId":7,
                  "details":{"componentAction":"DETACH"},"allocations":[]},
                 {"assetTag":"LIC-001","name":"Office package","category":"LICENSE","seats":2,
                  "allocations":[{"allocationId":11,"seats":1,"action":"RESERVED","assignmentType":"OEM"},
                                 {"allocationId":12,"seats":1,"action":"RELEASED","assignmentType":"PER_USER"},
                                 {"allocationId":13,"seats":1,"action":"UNLINKED","assignmentType":"PER_USER"}],
                  "details":{"allocationDecisions":[
                    {"action":"RESERVED","before":{"allocationId":11,"deviceId":7,"relationshipId":101}},
                    {"action":"RELEASED","before":{"allocationId":12,"userId":8,"deviceId":7,"relationshipId":102}},
                    {"action":"UNLINKED","before":{"allocationId":13,"userId":9,"deviceId":7,"relationshipId":103}}]}}
                 ]}
                """);
    }

    private ObjectNode disposal() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {"actorName":"System Administrator","reason":"Beyond repair","disposalDate":"2026-09-15",
                 "assets":[{"assetId":7,"assetTag":"LAP-007","name":"Laptop mẫu","category":"DEVICE","serialNumber":"SER-007","autoAdded":false}],
                 "oemAllocations":[{"allocationId":11,"licenseAssetId":9,"assetTag":"OEM-009","deviceId":7,"deviceTag":"LAP-007","seats":1}],
                 "perUserDecisions":[{"action":"UNLINKED","before":{"allocationId":12,"assetTag":"USR-012","userName":"Nguyễn Văn A","deviceTag":"LAP-007","seats":1}}]}
                """);
    }

    private int[] pixels(ObjectNode snapshot) throws Exception {
        byte[] bytes = new BilingualPdfGenerator().generate("HO-TEST", "HANDOVER", Instant.parse("2026-09-17T00:00:00Z"), "IT Demo", snapshot);
        try (var document = Loader.loadPDF(bytes)) {
            var image = new PDFRenderer(document).renderImage(0);
            return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        }
    }

    private int[] recoveryPixels(ObjectNode snapshot) throws Exception {
        byte[] bytes = new BilingualPdfGenerator().generate("RC-TEST", "RECOVERY", Instant.parse("2026-09-17T00:00:00Z"), "IT Demo", snapshot);
        try (var document = Loader.loadPDF(bytes)) {
            var image = new PDFRenderer(document).renderImage(0);
            return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        }
    }

    private int[] disposalPixels(ObjectNode snapshot, String actor) throws Exception {
        byte[] bytes = new BilingualPdfGenerator().generate("DI-TEST", "DISPOSAL", Instant.parse("2026-09-17T00:00:00Z"), actor, snapshot);
        try (var document = Loader.loadPDF(bytes)) {
            var image = new PDFRenderer(document).renderImage(0);
            return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        }
    }

    @Test
    void disposalReportRendersFrozenBusinessFieldsAndAllocationEvidence() throws Exception {
        var original = disposal();
        String previewPath = System.getProperty("t18.pdf.preview");
        if (previewPath != null) Files.write(Path.of(previewPath), new BilingualPdfGenerator().generate(
                "DI-T18-REVIEW", "DISPOSAL", Instant.parse("2026-09-17T00:00:00Z"),
                "System Administrator", original));
        var reason = original.deepCopy(); reason.put("reason", "Changed reason");
        var date = original.deepCopy(); date.put("disposalDate", "2027-01-01");
        var serial = original.deepCopy(); ((ObjectNode) serial.path("assets").get(0)).put("serialNumber", "CHANGED-SERIAL");
        var oem = original.deepCopy(); ((ObjectNode) oem.path("oemAllocations").get(0)).put("allocationId", 999);
        var decision = original.deepCopy(); ((ObjectNode) decision.path("perUserDecisions").get(0)).put("action", "RELEASED");
        var decisionSeats = original.deepCopy(); ((ObjectNode) decisionSeats.path("perUserDecisions").get(0).path("before")).put("seats", 9);
        var decisionPackage = original.deepCopy(); ((ObjectNode) decisionPackage.path("perUserDecisions").get(0).path("before")).put("assetTag", "CHANGED-PACKAGE");
        int[] pixels = disposalPixels(original, "System Administrator");
        assertThat(Arrays.equals(pixels, disposalPixels(reason, "System Administrator"))).isFalse();
        assertThat(Arrays.equals(pixels, disposalPixels(date, "System Administrator"))).isFalse();
        assertThat(Arrays.equals(pixels, disposalPixels(serial, "System Administrator"))).isFalse();
        assertThat(Arrays.equals(pixels, disposalPixels(oem, "System Administrator"))).isFalse();
        assertThat(Arrays.equals(pixels, disposalPixels(decision, "System Administrator"))).isFalse();
        assertThat(Arrays.equals(pixels, disposalPixels(decisionSeats, "System Administrator"))).isFalse();
        assertThat(Arrays.equals(pixels, disposalPixels(decisionPackage, "System Administrator"))).isFalse();
        assertThat(Arrays.equals(pixels, disposalPixels(original, "Changed actor"))).isFalse();
    }

    @Test
    void recoveryReportRendersFrozenContextAndEachAllocationOutcome() throws Exception {
        var original = recovery();
        String previewPath = System.getProperty("t16.pdf.preview");
        if (previewPath != null) Files.write(Path.of(previewPath), new BilingualPdfGenerator().generate(
                "RC-TEST", "RECOVERY", Instant.parse("2026-09-17T00:00:00Z"), "IT Demo", original));
        var changedReason = original.deepCopy(); changedReason.put("reason", "Thay đổi lý do");
        var changedComponent = original.deepCopy(); ((ObjectNode) changedComponent.path("lines").get(0).path("details")).put("componentAction", "KEEP_ATTACHED");
        var changedAllocation = original.deepCopy();
        ((ObjectNode) changedAllocation.path("lines").get(1).path("allocations").get(2)).put("action", "RELEASED");
        ((ObjectNode) changedAllocation.path("lines").get(1).path("details").path("allocationDecisions").get(2)).put("action", "RELEASED");
        assertThat(Arrays.equals(recoveryPixels(original), recoveryPixels(changedReason))).isFalse();
        assertThat(Arrays.equals(recoveryPixels(original), recoveryPixels(changedComponent))).isFalse();
        assertThat(Arrays.equals(recoveryPixels(original), recoveryPixels(changedAllocation))).isFalse();
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
