package come.ying.lifenote.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Manual create form (endpoint 11): title and date required, the rest optional. */
@Data
public class TaskCreateRequest {
    @NotBlank(message = "title must not be blank")
    @Size(max = 200, message = "title must be shorter than 200 characters")
    private String title;

    @NotNull(message = "dueDate is required")
    private LocalDate dueDate;

    private LocalTime dueTime;

    @Size(max = 200)
    private String place;

    private List<String> documentsToBring;

    private String notes;
}
