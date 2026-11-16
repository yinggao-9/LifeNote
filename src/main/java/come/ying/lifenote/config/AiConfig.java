package come.ying.lifenote.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The two LLM providers, side by side. GPT rides the OpenAI starter's
 * auto-configured model; DeepSeek is built by hand because its starter
 * aborts startup when no key is configured. The chat-client auto-config is
 * excluded in application.yml - it injects ChatModel by type and would be
 * ambiguous with two of them present.
 */
@Configuration
public class AiConfig {

    @Bean("gpt")
    @ConditionalOnProperty(name = "spring.ai.openai.api-key")
    public LlmClient gpt(@Qualifier("openAiChatModel") ChatModel openAiChatModel,
                         @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String modelName) {
        return new LlmClient("gpt", modelName, ChatClient.builder(openAiChatModel).build());
    }

    @Bean("deepseek")
    @ConditionalOnProperty(name = "spring.ai.deepseek.api-key")
    public LlmClient deepseek(@Value("${spring.ai.deepseek.api-key}") String apiKey,
                              @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl) {
        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        // separate statements: model() and temperature() have unrelated return
        // types in the two builder hierarchies, but both mutate this instance
        DeepSeekChatOptions.Builder options = DeepSeekChatOptions.builder();
        options.model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH);
        options.temperature(0.7);
        options.maxTokens(3000);

        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .options(options.build())
                .build();
        return new LlmClient("deepseek", "deepseek-v4-flash", ChatClient.builder(model).build());
    }
}
