package come.ying.lifenote.dto.response;

import come.ying.lifenote.entity.Task;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TaskResponse {
    private UUID id;
    private String title;
    private String notes;
    private LocalDate dueDate;
    private LocalTime dueTime;
    private String place;
    private List<String> documentsToBring;
    private String status;        // TODO / DONE / CANCELLED / POSTPONED
    private String source;        // MANUAL / QUICK / AI
    private UUID postponedFromTaskId;
    private Instant completedAt;
    private Instant createdAt;
    private List<SourceResponse> sources;   // filled only by the detail endpoint, empty list elsewhere

    /** Shared by list/board/postpone results; sources stay empty until detail() signs the URLs. */
    public static TaskResponse from(Task t) {
        return TaskResponse.builder()
                .id(t.getId()).title(t.getTitle()).notes(t.getNotes())
                .dueDate(t.getDueDate()).dueTime(t.getDueTime())
                .place(t.getPlace())
                .documentsToBring(t.getDocumentsToBring())
                .status(t.getStatus().name())
                .source(t.getSource().name())
                .postponedFromTaskId(t.getPostponedFromTaskId())
                .completedAt(t.getCompletedAt())
                .createdAt(t.getCreatedAt())
                .sources(List.of())
                .build();
    }
}
