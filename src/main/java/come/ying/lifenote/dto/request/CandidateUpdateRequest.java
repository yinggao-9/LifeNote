package come.ying.lifenote.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Edit payload for one draft; endpoints 5 (single edit) and 6 (bulk confirm)
 * share this class. dueDate may be null here - a date is only mandatory at
 * confirm time, endpoint 6 enforces that.
 */
@Data
public class CandidateUpdateRequest {
    /** Draft id echoed back by the front end; the backend locates task_candidates by it. */
    private UUID id;

    @Size(max = 200, message = "title must be shorter than 200 characters")
    private String title;

    private LocalDate dueDate;

    private LocalTime dueTime;

    @Size(max = 200)
    private String place;

    private List<String> documentsToBring;

    private String notes;
}
