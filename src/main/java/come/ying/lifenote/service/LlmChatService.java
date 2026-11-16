package come.ying.lifenote.service;

import come.ying.lifenote.config.LlmClient;
import come.ying.lifenote.entity.UserSetting;
import come.ying.lifenote.exception.BusinessException;
import come.ying.lifenote.repository.UserSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Single door to every LLM call. Picks the provider the user chose in
 * settings, and when that provider fails - network, bad key, empty balance -
 * tries the other one before giving up with 1103.
 */
@Slf4j
@Service
public class LlmChatService {

    /** text: the model's raw reply; provider/model: who actually answered. */
    public record Reply(String text, String provider, String model) {}

    private final Map<String, LlmClient> clients;
    private final UserSettingsRepository settingsRepository;

    public LlmChatService(Map<String, LlmClient> clients,
                          UserSettingsRepository settingsRepository) {
        this.clients = clients;
        this.settingsRepository = settingsRepository;
    }

    /**
     * The provider key for this user: their settings choice when it is
     * configured, otherwise whatever is available ("default" and unknown
     * values land here too - older rows never got migrated).
     */
    public String preferredModelFor(UUID userId) {
        String preferred = settingsRepository.findById(userId)
                .map(UserSetting::getAiModel)
                .orElse("gpt");
        if (clients.containsKey(preferred)) {
            return preferred;
        }
        return firstAvailableKey();
    }

    /**
     * Runs one prompt against the preferred provider and falls back to the
     * other one on any call-level failure. The userSpec builds the user
     * message so callers can attach images as well as text.
     */
    public Reply chat(String preferred, String system,
                      Consumer<ChatClient.ChatClientRequestSpec> userSpec) {
        requireConfigured();
        LlmClient primary = clients.getOrDefault(preferred, firstClient());
        try {
            return call(primary, system, userSpec);
        } catch (Exception e) {
            log.warn("provider {} failed, trying the other one: {}", primary.key(), e.getMessage());
        }

        LlmClient secondary = otherClient(primary.key());
        if (secondary != null) {
            try {
                return call(secondary, system, userSpec);
            } catch (Exception e) {
                log.warn("provider {} failed too: {}", secondary.key(), e.getMessage());
                throw new BusinessException(1103, "AI service error: " + e.getMessage());
            }
        }
        throw new BusinessException(1103,
                "AI service error: no other provider configured to fall back to.");
    }

    /** The other configured provider key, or null when only one exists. */
    public String otherThan(String provider) {
        LlmClient other = otherClient(provider);
        return other == null ? null : other.key();
    }

    private LlmClient otherClient(String provider) {
        return clients.values().stream()
                .filter(c -> !c.key().equals(provider))
                .findFirst()
                .orElse(null);
    }

    private Reply call(LlmClient client, String system,
                       Consumer<ChatClient.ChatClientRequestSpec> userSpec) {
        ChatClient.ChatClientRequestSpec spec = client.client().prompt().system(system);
        userSpec.accept(spec);
        String text = spec.call().content();
        return new Reply(text, client.key(), client.modelName());
    }

    private void requireConfigured() {
        if (clients.isEmpty()) {
            throw new BusinessException(1103, "No AI provider is configured.");
        }
    }

    private String firstAvailableKey() {
        requireConfigured();
        return clients.keySet().iterator().next();
    }

    private LlmClient firstClient() {
        return clients.values().iterator().next();
    }
}
