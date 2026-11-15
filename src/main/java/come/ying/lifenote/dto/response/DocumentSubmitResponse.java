package come.ying.lifenote.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Result of submitting a document or re-extracting one.
 * A failed AI call still returns HTTP 200 with status=FAILED: the upload
 * succeeded, so the front end shows "analysis failed, retry available"
 * instead of discarding the whole request.
 */
@Data
@Builder
public class DocumentSubmitResponse {
    private UUID documentId;
    private String docType;        // TEXT / IMAGE / PDF
    private String filename;
    private String status;         // PENDING_CONFIRM / FAILED
    private String errorMessage;   // shown to the user when FAILED
    private String extractedText;  // text-layer preview, truncated to 2000 chars
    private List<CandidateResponse> candidates;
}
