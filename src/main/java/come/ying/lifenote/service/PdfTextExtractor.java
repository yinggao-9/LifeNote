package come.ying.lifenote.service;

import come.ying.lifenote.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class PdfTextExtractor {

    /**
     * Pulls the text layer out of a PDF. Scanned PDFs have no text layer and
     * yield an empty string - tell the user to upload a screenshot instead of
     * sending an empty prompt to the model.
     */
    public String extractText(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            String text = new PDFTextStripper().getText(doc);
            if (text == null || text.isBlank()) {
                throw new BusinessException(1204,
                        "No text layer in this PDF. Please upload a screenshot instead.");
            }
            return text;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(1204, "Failed to read PDF: " + e.getMessage());
        }
    }
}
