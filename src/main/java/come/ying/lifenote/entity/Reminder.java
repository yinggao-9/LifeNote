package come.ying.lifenote.entity;

import come.ying.lifenote.common.enums.ReminderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One notification shown in the app. Title and body are pre-rendered on
 * creation so the list endpoint never has to rebuild text, and the email
 * sender can reuse exactly what the user sees.
 */
@Getter
@Setter
@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Null for EVENING digests, which are not tied to a single task. */
    @Column(name = "task_id")
    private UUID taskId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReminderType type;

    /** The day this reminder is about (task due date, or the digest's calendar day). */
    @NotNull
    @Column(name = "remind_for_date", nullable = false)
    private LocalDate remindForDate;

    @NotNull
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @NotNull
    @Column(nullable = false)
    private String body;

    @NotNull
    @Column(name = "is_read", nullable = false)
    private Boolean read = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
