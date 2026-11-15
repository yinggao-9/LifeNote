package come.ying.lifenote.service;

import come.ying.lifenote.entity.AiRun;
import come.ying.lifenote.repository.AiRunRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Writes exactly one audit row per LLM call - the proposal requires model,
 * prompt version, raw output and duration for every run, including failures.
 * Only DocumentService calls this.
 */
@Service
public class AiRunService {

    private final AiRunRepository aiRunRepository;

    public AiRunService(AiRunRepository aiRunRepository) {
        this.aiRunRepository = aiRunRepository;
    }

    public UUID recordSuccess(UUID documentId, UUID userId, String model,
                              String rawOutput, long durationMs) {
        AiRun run = new AiRun();
        run.setDocumentId(documentId);
        run.setUserId(userId);
        run.setModel(model);
        run.setPromptVersion(AiExtractionService.PROMPT_VERSION);
        run.setStatus("SUCCESS");
        run.setRawOutput(rawOutput);
        run.setDurationMs((int) durationMs);
        return aiRunRepository.save(run).getId();
    }

    public UUID recordFailure(UUID documentId, UUID userId, String model,
                              Exception e, long durationMs) {
        AiRun run = new AiRun();
        run.setDocumentId(documentId);
        run.setUserId(userId);
        run.setModel(model);
        run.setPromptVersion(AiExtractionService.PROMPT_VERSION);
        run.setStatus("FAILED");
        run.setErrorMessage(e.getMessage());
        run.setDurationMs((int) durationMs);
        return aiRunRepository.save(run).getId();
    }
}
