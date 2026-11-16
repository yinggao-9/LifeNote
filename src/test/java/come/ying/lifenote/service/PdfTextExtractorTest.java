package come.ying.lifenote.service;

import come.ying.lifenote.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the PDF text layer reader (endpoint 1's file leg).
 * Scanned pages have no text layer and must be refused with 1204 so the
 * user is told to upload a screenshot instead of silently sending an
 * empty prompt to the model.
 */
class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    private static byte[] pdfWithText(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (!text.isEmpty()) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void extractsTheTextLayer() throws Exception {
        String text = extractor.extractText(pdfWithText("Submit the form by Friday"));

        assertTrue(text.contains("Submit the form by Friday"), text);
    }

    @Test
    void scannedPageWithoutTextLayerIs1204() throws Exception {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> extractor.extractText(pdfWithText("")));

        assertEquals(1204, ex.getCode());
    }

    @Test
    void unreadableBytesAre1204() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> extractor.extractText("this is not a pdf".getBytes()));

        assertEquals(1204, ex.getCode());
    }
}
