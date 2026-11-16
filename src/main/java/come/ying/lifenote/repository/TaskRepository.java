package come.ying.lifenote.repository;

import come.ying.lifenote.common.enums.TaskStatus;
import come.ying.lifenote.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    /** Calendar and list: a date range (from == to means a single day). */
    List<Task> findByUserIdAndDueDateBetweenOrderByDueDateAscDueTimeAsc(
            UUID userId, LocalDate from, LocalDate to);

    List<Task> findByUserIdAndDueDateBetweenAndStatusOrderByDueDateAscDueTimeAsc(
            UUID userId, LocalDate from, LocalDate to, TaskStatus status);

    Optional<Task> findByIdAndUserId(UUID id, UUID userId);

    /** Scheduler: tasks still open on one exact day, any owner (filtered per user later). */
    List<Task> findByStatusAndDueDateBetween(TaskStatus status, LocalDate from, LocalDate to);

    /** Scheduler: one user's open tasks for the evening digest. */
    List<Task> findByUserIdAndDueDateAndStatus(UUID userId, LocalDate dueDate, TaskStatus status);
}
