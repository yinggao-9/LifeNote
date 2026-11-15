package come.ying.lifenote.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskStatusRequest {
    /** TODO / DONE / CANCELLED - POSTPONED is rejected here, only the postpone action may set it. */
    @NotBlank
    private String status;
}
