package come.ying.lifenote.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * PATCH semantics: null = leave this field alone, non-null = replace it, so no
 * @NotNull anywhere and the service checks field by field. Because due_date is
 * NOT NULL in the database, "clear the date" is not a reachable state - passing
 * null means "keep it", never "set it to NULL".
 */
@Data
public class TaskUpdateRequest {
    @Size(max = 200)
    private String title;
    private LocalDate dueDate;
    private LocalTime dueTime;
    private String place;
    private List<String> documentsToBring;
    private String notes;
}
