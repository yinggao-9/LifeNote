package come.ying.lifenote.repository;

import come.ying.lifenote.entity.Postponement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PostponementRepository extends JpaRepository<Postponement, UUID> {

    /** Log view filtered by the date the task was originally due. */
    List<Postponement> findByUserIdAndOldDueDateBetweenOrderByCreatedAtDesc(
            UUID userId, LocalDate from, LocalDate to);
}
