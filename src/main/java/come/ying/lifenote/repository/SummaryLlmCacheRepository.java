package come.ying.lifenote.repository;

import come.ying.lifenote.entity.SummaryLlmCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SummaryLlmCacheRepository extends JpaRepository<SummaryLlmCache, UUID> {

    Optional<SummaryLlmCache> findByUserIdAndMonth(UUID userId, String month);
}
