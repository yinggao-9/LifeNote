package come.ying.lifenote.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * status = "OK"         parsed cleanly, the task exists, task holds it.
 * status = "NEED_INPUT" local parsing found no date (or no title); title/dueTime
 *                      carry whatever was recognized so the front end can
 *                      pre-fill a form, then the normal create call saves it.
 * NEED_INPUT is not an error: HTTP stays 200 and no exception is thrown.
 */
@Data
@Builder
public class QuickAddResponse {
    private String status;        // OK / NEED_INPUT
    private String title;
    private LocalDate dueDate;    // null when NEED_INPUT and no date was recognized
    private LocalTime dueTime;
    private TaskResponse task;    // non-null only for OK
}
