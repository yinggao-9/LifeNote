package come.ying.lifenote.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Join table between tasks and documents: one task can cite several sources
 * (a receipt plus a typed sentence), one source can yield several tasks.
 * Separate surrogate id plus a unique constraint is easier to work with than a composite key.
 */
@Getter
@Setter
@Entity
@Table(name = "task_documents",
       uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "document_id"}))
public class TaskDocument {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TaskDocument() { }

    public TaskDocument(UUID taskId, UUID documentId) {
        this.taskId = taskId;
        this.documentId = documentId;
    }
}
