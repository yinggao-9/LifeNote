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
}
