package come.ying.lifenote.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class PostponeRequest {
    @NotNull(message = "dueDate is required")
    private LocalDate dueDate;   // new date: must be in the future and differ from the current one (service checks)

    private LocalTime dueTime;   // omitted means keep the original time
}
