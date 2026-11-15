package come.ying.lifenote.repository;

import come.ying.lifenote.entity.TaskCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskCandidateRepository extends JpaRepository<TaskCandidate, UUID> {

    List<TaskCandidate> findByDocumentIdAndUserIdOrderByCreatedAt(UUID documentId, UUID userId);

    Optional<TaskCandidate> findByIdAndUserIdAndDocumentId(UUID id, UUID userId, UUID documentId);

    /** Bulk delete used by cancel and re-extract; needs its own transaction (not save/deleteById). */
    @Transactional
    void deleteByDocumentId(UUID documentId);
}
