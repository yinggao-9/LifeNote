package come.ying.lifenote.dto.response;

import come.ying.lifenote.entity.Document;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Shared by the history list and the detail view; list mode leaves candidates/sourceUrl null. */
@Data
@Builder(toBuilder = true)   // toBuilder required: detail() chains brief(...) to add fields
public class DocumentResponse {
    private UUID id;
    private String docType;
    private String filename;
    private Long fileSize;
    private String status;
    private String errorMessage;
    private String rawText;
    private String sourceUrl;    // 10-minute presigned URL, detail view only
    private Instant createdAt;
    private List<CandidateResponse> candidates;

    public static DocumentResponse brief(Document d) {
        return DocumentResponse.builder()
                .id(d.getId())
                .docType(d.getDocType().name())
                .filename(d.getOriginalFilename())
                .fileSize(d.getFileSize())
                .status(d.getStatus().name())
                .errorMessage(d.getErrorMessage())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
