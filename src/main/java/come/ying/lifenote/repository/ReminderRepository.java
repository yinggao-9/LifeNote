package come.ying.lifenote.repository;

import come.ying.lifenote.common.enums.ReminderType;
import come.ying.lifenote.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    /** Unread first, then newest: the order the bell panel renders. */
    List<Reminder> findByUserIdOrderByReadAscCreatedAtDesc(UUID userId);

    List<Reminder> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndTaskIdAndRemindForDateAndType(
            UUID userId, UUID taskId, LocalDate remindForDate, ReminderType type);

    boolean existsByUserIdAndRemindForDateAndType(
            UUID userId, LocalDate remindForDate, ReminderType type);
}
