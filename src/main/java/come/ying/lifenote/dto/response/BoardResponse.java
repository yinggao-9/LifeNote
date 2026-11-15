package come.ying.lifenote.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * One day's board: four columns plus progress counts.
 * The progress denominator is todo + done + cancelled; postponed is excluded -
 * that task has already "moved away", so counting it would keep today's
 * completion rate permanently low.
 */
@Data
@Builder
public class BoardResponse {
    private LocalDate date;
    private int total;
    private int todo;
    private int done;
    private int cancelled;
    private int postponed;
    private List<TaskResponse> todoTasks;
    private List<TaskResponse> doneTasks;
    private List<TaskResponse> cancelledTasks;
    private List<TaskResponse> postponedTasks;
}
