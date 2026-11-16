package come.ying.lifenote.service;

import come.ying.lifenote.config.LlmClient;
import come.ying.lifenote.entity.UserSetting;
import come.ying.lifenote.exception.BusinessException;
import come.ying.lifenote.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Provider choice and the fallback chain, with both ChatClients mocked out:
 * no network, no keys, both directions of failure covered.
 */
class LlmChatServiceTest {

    private final UserSettingsRepository settings = mock(UserSettingsRepository.class);

    private ChatClient answering(String text) {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(call);
        when(call.content()).thenReturn(text);
        return client;
    }

    private ChatClient failing(String message) {
        ChatClient client = mock(ChatClient.class);
        when(client.prompt()).thenThrow(new RuntimeException(message));
        return client;
    }

    private Map<String, LlmClient> bothProviders(ChatClient gpt, ChatClient deepseek) {
        Map<String, LlmClient> clients = new LinkedHashMap<>();
        clients.put("gpt", new LlmClient("gpt", "gpt-4o-mini", gpt));
        clients.put("deepseek", new LlmClient("deepseek", "deepseek-chat", deepseek));
        return clients;
    }

    private LlmChatService service(Map<String, LlmClient> clients) {
        return new LlmChatService(clients, settings);
    }

    private static final Consumer<ChatClient.ChatClientRequestSpec> NOOP = spec -> {
    };

    // ---------- preferredModelFor ----------

    @Test
    void preferredModelIsTheUserChoiceWhenConfigured() {
        UserSetting row = new UserSetting();
        row.setAiModel("deepseek");
        when(settings.findById(any(UUID.class))).thenReturn(Optional.of(row));

        String preferred = service(bothProviders(answering("x"), answering("y")))
                .preferredModelFor(UUID.randomUUID());

        assertEquals("deepseek", preferred);
    }

    @Test
    void preferredModelDefaultsToGptWithoutSettings() {
        when(settings.findById(any(UUID.class))).thenReturn(Optional.empty());

        String preferred = service(bothProviders(answering("x"), answering("y")))
                .preferredModelFor(UUID.randomUUID());

        assertEquals("gpt", preferred);
    }

    @Test
    void preferredModelSkipsUnknownLegacyValues() {
        // old rows stored "default"; that key is not a configured provider
        UserSetting row = new UserSetting();
        row.setAiModel("default");
        when(settings.findById(any(UUID.class))).thenReturn(Optional.of(row));

        String preferred = service(bothProviders(answering("x"), answering("y")))
                .preferredModelFor(UUID.randomUUID());

        assertEquals("gpt", preferred);
    }

    // ---------- chat / fallback ----------

    @Test
    void primaryProviderAnswersAndIsReported() {
        ChatClient gpt = answering("hello from gpt");
        LlmChatService svc = service(bothProviders(gpt, answering("other")));

        LlmChatService.Reply reply = svc.chat("gpt", "system", NOOP);

        assertEquals("hello from gpt", reply.text());
        assertEquals("gpt", reply.provider());
        assertEquals("gpt-4o-mini", reply.model());
    }

    @Test
    void gptFailureFallsBackToDeepseek() {
        ChatClient gpt = failing("402 no balance");
        ChatClient deepseek = answering("hello from deepseek");
        LlmChatService svc = service(bothProviders(gpt, deepseek));

        LlmChatService.Reply reply = svc.chat("gpt", "system", NOOP);

        assertEquals("hello from deepseek", reply.text());
        assertEquals("deepseek", reply.provider());
        verify(deepseek, times(1)).prompt();
    }

    @Test
    void deepseekFailureFallsBackToGpt() {
        ChatClient gpt = answering("hello from gpt");
        ChatClient deepseek = failing("connection refused");
        LlmChatService svc = service(bothProviders(gpt, deepseek));

        LlmChatService.Reply reply = svc.chat("deepseek", "system", NOOP);

        assertEquals("gpt", reply.provider());
        verify(gpt, times(1)).prompt();
    }

    @Test
    void bothProvidersFailingRaises1103() {
        LlmChatService svc = service(bothProviders(failing("402"), failing("500")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.chat("gpt", "system", NOOP));

        assertEquals(1103, ex.getCode());
    }

    @Test
    void singleProviderFailureRaises1103WithNoFallback() {
        Map<String, LlmClient> onlyGpt = new LinkedHashMap<>();
        onlyGpt.put("gpt", new LlmClient("gpt", "gpt-4o-mini", failing("boom")));
        LlmChatService svc = service(onlyGpt);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.chat("gpt", "system", NOOP));

        assertEquals(1103, ex.getCode());
    }

    @Test
    void noProviderConfiguredRaises1103() {
        LlmChatService svc = service(new LinkedHashMap<>());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.chat("gpt", "system", NOOP));

        assertEquals(1103, ex.getCode());
    }

    // ---------- otherThan ----------

    @Test
    void otherThanReturnsTheOppositeProvider() {
        LlmChatService svc = service(bothProviders(answering("a"), answering("b")));

        assertEquals("deepseek", svc.otherThan("gpt"));
        assertEquals("gpt", svc.otherThan("deepseek"));
    }

    @Test
    void otherThanIsNullWhenOnlyOneProviderExists() {
        Map<String, LlmClient> onlyGpt = new LinkedHashMap<>();
        onlyGpt.put("gpt", new LlmClient("gpt", "gpt-4o-mini", answering("a")));
        LlmChatService svc = service(onlyGpt);

        assertNull(svc.otherThan("gpt"));
    }
}
