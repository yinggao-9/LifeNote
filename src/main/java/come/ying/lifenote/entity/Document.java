package come.ying.lifenote.entity;

import come.ying.lifenote.common.enums.DocType;
import come.ying.lifenote.common.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One piece of source material: pasted text or an uploaded image/PDF.
 * Every task created from AI drafts points back here through task_documents.
 */
@Getter
@Setter
@Entity
@Table(name = "documents")
public class Document {
    // generated locally (not by the DB) because the S3 object key needs it before insert
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 16)
    private DocType docType;

    @Column(name = "s3_key", length = 512)
    private String s3Key;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    /** Pasted text or PDFBox output; null for IMAGE because the model reads the pixels directly. */
    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.PENDING_CONFIRM;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
