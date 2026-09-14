package com.company.itam.publication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BilingualPdfGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();

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
