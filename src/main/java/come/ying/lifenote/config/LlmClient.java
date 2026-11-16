package come.ying.lifenote.config;

import org.springframework.ai.chat.client.ChatClient;

/**
 * One configured LLM provider. The key doubles as the settings value users
 * pick (gpt / deepseek); modelName is what lands in ai_runs.model so audit
 * rows name the real model rather than the provider alias.
 */
public record LlmClient(String key, String modelName, ChatClient client) {
}
