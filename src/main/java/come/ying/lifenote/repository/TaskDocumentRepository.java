package come.ying.lifenote.repository;

import come.ying.lifenote.entity.TaskDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskDocumentRepository extends JpaRepository<TaskDocument, UUID> {

    /** Task detail page: which sources this task was built from. */
    List<TaskDocument> findByTaskId(UUID taskId);
}
