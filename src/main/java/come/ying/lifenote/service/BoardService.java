package come.ying.lifenote.service;

import come.ying.lifenote.dto.response.BoardResponse;
import come.ying.lifenote.dto.response.PostponementResponse;
import come.ying.lifenote.dto.response.TaskResponse;
import come.ying.lifenote.entity.Task;
import come.ying.lifenote.repository.PostponementRepository;
import come.ying.lifenote.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Read-only side: the day board (17) and the postpone log (18), both pure queries. */
@Service
public class BoardService {

    private final TaskRepository taskRepository;
    private final PostponementRepository postponementRepository;

    public BoardService(TaskRepository taskRepository, PostponementRepository postponementRepository) {
        this.taskRepository = taskRepository;
        this.postponementRepository = postponementRepository;
    }

    /** One day's board; date omitted = today. */
    public BoardResponse board(UUID userId, LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();

        // one range query (from == to == the day), bucketed in memory -
        // four separate SQLs would be slower and could show four different instants
        List<Task> tasks = taskRepository
                .findByUserIdAndDueDateBetweenOrderByDueDateAscDueTimeAsc(userId, d, d);

        List<TaskResponse> todo = new ArrayList<>();
        List<TaskResponse> done = new ArrayList<>();
        List<TaskResponse> cancelled = new ArrayList<>();
        List<TaskResponse> postponed = new ArrayList<>();

        for (Task task : tasks) {
            switch (task.getStatus()) {
                case TODO      -> todo.add(TaskResponse.from(task));
                case DONE      -> done.add(TaskResponse.from(task));
                case CANCELLED -> cancelled.add(TaskResponse.from(task));
                case POSTPONED -> postponed.add(TaskResponse.from(task));
            }
        }

        int total = todo.size() + done.size() + cancelled.size();   // denominator excludes postponed
        return BoardResponse.builder()
                .date(d)
                .total(total)
                .todo(todo.size()).done(done.size())
                .cancelled(cancelled.size()).postponed(postponed.size())
                .todoTasks(todo).doneTasks(done)
                .cancelledTasks(cancelled).postponedTasks(postponed)
                .build();
    }

    /**
     * The postpone log; from/to omitted = first to last day of this
     * month. Filtered by old_due_date rather than createdAt: the question is
     * "which day's plan moved", not "when did I click".
     */
    public List<PostponementResponse> postponements(UUID userId, LocalDate from, LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate t = to != null ? to : f.withDayOfMonth(f.lengthOfMonth());
        if (t.isBefore(f)) {
            LocalDate tmp = f; f = t; t = tmp;
        }
        return postponementRepository
                .findByUserIdAndOldDueDateBetweenOrderByCreatedAtDesc(userId, f, t)
                .stream()
                .map(PostponementResponse::from)
                .toList();
    }
}
