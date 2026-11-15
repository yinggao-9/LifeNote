package come.ying.lifenote.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One row per postpone action, storing both the old and the new date.
 * Postponing the same task twice produces two rows.
 * user_id is denormalized so the log query never has to join tasks.
 */
@Getter
@Setter
@Entity
@Table(name = "postponements")
public class Postponement {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "new_task_id", nullable = false)
    private UUID newTaskId;

    @Column(name = "old_due_date", nullable = false)
    private LocalDate oldDueDate;

    @Column(name = "old_due_time")
    private LocalTime oldDueTime;

    @Column(name = "new_due_date", nullable = false)
    private LocalDate newDueDate;

    @Column(name = "new_due_time")
    private LocalTime newDueTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
