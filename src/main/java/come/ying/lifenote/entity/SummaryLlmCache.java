package come.ying.lifenote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One generated monthly-summary paragraph for one user and month.
 * Row exists only after a successful LLM call; failures are never
 * cached so the next request can try again.
 */
@Getter
@Setter
@Entity
@Table(name = "summary_llm_cache")
public class SummaryLlmCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Calendar month in yyyy-MM, same string the API accepts. */
    @NotNull
    @Size(max = 7)
    @Column(nullable = false, length = 7)
    private String month;

    @NotNull
    @Column(nullable = false)
    private String content;

    @NotNull
    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt = OffsetDateTime.now();
}
