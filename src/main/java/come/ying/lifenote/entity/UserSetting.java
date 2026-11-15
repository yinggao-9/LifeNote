package come.ying.lifenote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "user_settings")
public class UserSetting {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID id;

    // Entity defaults are required: register() only sets the id, but these columns are NOT NULL.
    // @ColumnDefault alone would still bind NULL on INSERT.

    @Size(max = 50)
    @NotNull
    @ColumnDefault("'Europe/Dublin'")
    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone = "Europe/Dublin";

    @NotNull
    @ColumnDefault("'21:00:00'")
    @Column(name = "evening_reminder_time", nullable = false)
    private LocalTime eveningReminderTime = LocalTime.of(21, 0);

    @NotNull
    @ColumnDefault("false")
    @Column(name = "email_reminders_enabled", nullable = false)
    private Boolean emailRemindersEnabled = false;

    @Size(max = 30)
    @NotNull
    @ColumnDefault("'default'")
    @Column(name = "ai_model", nullable = false, length = 30)
    private String aiModel = "default";


}