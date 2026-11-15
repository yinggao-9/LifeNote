package come.ying.lifenote.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * An AI draft shown in the confirm dialog. Drafts are never tasks: they become
 * tasks only after the user confirms, and cancelling deletes the rows outright.
 */
@Getter
@Setter
@Entity
@Table(name = "task_candidates")
public class TaskCandidate {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    /** Points at the successful ai_runs row; a retry creates a new run instead of overwriting. */
    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Null when the model found no date; the confirm endpoint rejects such drafts. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(name = "place", length = 200)
    private String place;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documents_to_bring")
    private List<String> documentsToBring;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    /** Model confidence 0..1; the UI may highlight low scores. */
    @Column(name = "confidence")
    private Double confidence;

    /** PENDING or CONFIRMED; a cancelled document leaves no candidates behind. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
