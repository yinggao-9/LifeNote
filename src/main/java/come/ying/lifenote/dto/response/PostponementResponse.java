package come.ying.lifenote.dto.response;

import come.ying.lifenote.entity.Postponement;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class PostponementResponse {
    private UUID id;
    private UUID taskId;          // the task that was postponed
    private UUID newTaskId;       // the copy created on the new date
    private LocalDate oldDueDate;
    private LocalTime oldDueTime;
    private LocalDate newDueDate;
    private LocalTime newDueTime;
    private Instant createdAt;

    public static PostponementResponse from(Postponement p) {
        return PostponementResponse.builder()
                .id(p.getId()).taskId(p.getTaskId()).newTaskId(p.getNewTaskId())
                .oldDueDate(p.getOldDueDate()).oldDueTime(p.getOldDueTime())
                .newDueDate(p.getNewDueDate()).newDueTime(p.getNewDueTime())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
