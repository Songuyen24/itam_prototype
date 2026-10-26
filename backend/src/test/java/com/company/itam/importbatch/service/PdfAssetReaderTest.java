package com.company.itam.importbatch.service;

import com.company.itam.common.exception.AppException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

public class PdfAssetReaderTest {
    final PdfAssetReader reader = new PdfAssetReader("missing-ocr-executable", "eng+vie");
    static final String HEADER = "assetTag | name | type | condition | serialNumber | purchaseCost";
    public static final List<String> PAGES = List.of(
        HEADER+"\n- | Prototype laptop A | LAPTOP | NEW | OCR-SN-A | 12000000\n- | Prototype laptop B | LAPTOP | NEW | OCR-SN-B | 10000000",
        HEADER+"\nOCR-DUP | Prototype duplicate A | LAPTOP | NEW | - | 5000000\nOCR-DUP | Prototype duplicate B | LAPTOP | NEW | - | 5000000\n- | - | LAPTOP | NEW | - | -\n- | Invalid catalog | UNKNOWN | NEW | - | -\n- | Invalid cost | LAPTOP | NEW | - | -20");

    public static byte[] pdf(List<String> pages, boolean scan) throws Exception {
        try(var document = new PDDocument();var out = new ByteArrayOutputStream()) {
            for(String text:pages) {
                var page = new PDPage(PDRectangle.A4);document.addPage(page);
                try(var content = new PDPageContentStream(document,page)) {
                    content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER),9);
                    content.newLineAtOffset(30,790);content.setLeading(24);
                    for(String line:text.split("\n")) {content.showText(line);content.newLine();}
                    content.endText();
                }
            }
            if(!scan) {document.save(out);return out.toByteArray();}
            try(var images = new PDDocument()) {
                var renderer = new PDFRenderer(document);
                for(int i=0;i<pages.size();i++) {
                    var page = new PDPage(PDRectangle.A4);images.addPage(page);
                    var image = renderer.renderImageWithDPI(i,200);
                    try(var content=new PDPageContentStream(images,page)) {content.drawImage(LosslessFactory.createFromImage(images,image),0,0,PDRectangle.A4.getWidth(),PDRectangle.A4.getHeight());}
                    image.flush();
                }
                images.save(out);return out.toByteArray();
            }
        }
    }
    public static MockMultipartFile file(byte[] data) {return new MockMultipartFile("file","prototype.pdf","application/pdf",data);}
    @Test void readsAllTextPagesAndRetainsMissingWrongAndDuplicateData() throws Exception {
        var rows = reader.read(file(pdf(PAGES,false)));
        assertThat(rows).hasSize(7);
        assertThat(rows.get(0)).containsEntry("name","Prototype laptop A").containsEntry("sourcePage",1);
        assertThat(rows.get(4).get("name")).isNull();
        assertThat(rows.get(6)).containsEntry("purchaseCost","-20").containsEntry("rowNumber",8);
    }
    @Test void recognizesBilingualHeadersAndPreservesUncertainRowsForRepair() {
        var rows=reader.parsePages(List.of("Tên tài sản (*) | Loại tài sản (*)\nMáy tính | LAPTOP\nwrapped unknown row"));
        assertThat(rows).hasSize(2);assertThat(rows.get(0)).containsEntry("name","Máy tính");
        assertThat(rows.get(1)).doesNotContainKey("name").containsEntry("sourceText","wrapped unknown row");
        assertThatThrownBy(()->reader.parsePages(List.of("a | b\nx | y"))).isInstanceOf(AppException.class).hasMessage("OCR_LAYOUT_UNSUPPORTED");
    }
    @Test void rejectsSpoofedOversizedEncryptedAndTooManyPages() throws Exception {
        assertThatThrownBy(()->reader.read(file("not pdf".getBytes()))).hasMessage("OCR_INVALID_PDF");
        assertThatThrownBy(()->reader.read(file(new byte[(int)ExcelHelperService.MAX_FILE_SIZE+1]))).hasMessage("FILE_TOO_LARGE");
        assertThatThrownBy(()->reader.read(file(pdf(Collections.nCopies(11,HEADER),false)))).hasMessage("OCR_PAGE_LIMIT");
        try(var doc=Loader.loadPDF(pdf(PAGES,false));var out=new ByteArrayOutputStream()) {
            doc.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner","secret",new org.apache.pdfbox.pdmodel.encryption.AccessPermission()));
            doc.save(out);assertThatThrownBy(()->reader.read(file(out.toByteArray()))).hasMessage("OCR_INVALID_PDF");
        }
    }
    @Test void scanFailsClearlyWhenLocalOcrIsUnavailable() throws Exception {
        assertThatThrownBy(()->reader.read(file(pdf(PAGES,true)))).hasMessage("OCR_UNAVAILABLE");
    }
    @Test void generatesPrototypeFixturesAndOptionallyChecksRealLocalOcr() throws Exception {
        Path folder=Path.of("../docs/ocr-prototype");Files.createDirectories(folder);
        Files.write(folder.resolve("asset-list-text.pdf"),pdf(PAGES,false));
        byte[] scan=pdf(PAGES,true);Files.write(folder.resolve("asset-list-scan.pdf"),scan);
        String executable=System.getProperty("ocr.test.executable");
        if(executable!=null) {
            var rows=new PdfAssetReader(executable,"eng+vie").read(file(scan));
            assertThat(rows).hasSize(7);
            assertThat(rows.get(0)).containsEntry("name","Prototype laptop A").containsEntry("type","LAPTOP");
            assertThat(rows.get(4).get("name")).isNull();
            assertThat(rows.get(6)).containsEntry("purchaseCost","-20");
        }
    }
}
