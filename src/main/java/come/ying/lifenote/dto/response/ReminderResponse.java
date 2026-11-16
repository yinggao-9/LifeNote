package come.ying.lifenote.dto.response;

import come.ying.lifenote.common.enums.ReminderType;
import come.ying.lifenote.entity.Reminder;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ReminderResponse {

    private UUID id;
    private UUID taskId;            // null for evening digests
    private String type;            // DEADLINE / EVENING
    private LocalDate remindForDate;
    private String title;
    private String body;
    private boolean read;
    private Instant createdAt;

    public static ReminderResponse from(Reminder r) {
        return new ReminderResponse(
                r.getId(),
                r.getTaskId(),
                r.getType().name(),
                r.getRemindForDate(),
                r.getTitle(),
                r.getBody(),
                Boolean.TRUE.equals(r.getRead()),
                r.getCreatedAt());
    }
}
