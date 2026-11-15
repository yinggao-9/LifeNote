package come.ying.lifenote.repository;

import come.ying.lifenote.entity.AiRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiRunRepository extends JpaRepository<AiRun, UUID> {

    List<AiRun> findByUserIdAndDocumentIdOrderByCreatedAtDesc(UUID userId, UUID documentId);
}
