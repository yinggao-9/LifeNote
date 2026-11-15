package come.ying.lifenote.repository;

import come.ying.lifenote.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** History list: newest 50 only, no pagination this term. */
    List<Document> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Ownership check folded into the lookup: empty means "not yours or not there". */
    Optional<Document> findByIdAndUserId(UUID id, UUID userId);
}
