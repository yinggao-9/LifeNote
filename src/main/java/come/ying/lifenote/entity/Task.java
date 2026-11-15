package come.ying.lifenote.entity;

import come.ying.lifenote.common.enums.TaskSource;
import come.ying.lifenote.common.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** A confirmed task. Lists, calendar, board and .ics export all read from this table. */
@Getter
@Setter
@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    /** NOT NULL: every view sorts by due_date, so a dateless task must not exist. */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(name = "place", length = 200)
    private String place;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documents_to_bring")
    private List<String> documentsToBring;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private TaskSource source;

    /** Set when status becomes DONE, cleared on restore; feedstock for the monthly summary. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Self-reference: the new task points back at the original one it postpones. */
    @Column(name = "postponed_from_task_id")
    private UUID postponedFromTaskId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
