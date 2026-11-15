package come.ying.lifenote.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One audit row per LLM call, success or failure. The proposal requires the model
 * name, prompt version, raw output and duration to be kept for every run.
 */
@Getter
@Setter
@Entity
@Table(name = "ai_runs")
public class AiRun {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    // duplicated so "list my AI calls" needs no join and cascades with the user
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;

    /** SUCCESS / FAILED, stored as a plain string to avoid another enum for two values. */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** The model's verbatim reply, before any JSON cleanup. */
    @Column(name = "raw_output", columnDefinition = "text")
    private String rawOutput;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
