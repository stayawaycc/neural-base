package com.neuralbase.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class ChatConfig {

    @Bean
    public ChatModel chatModel(
            @Value("${neuralbase.ai.deepseek-api-key}") String apiKey,
            @Value("${neuralbase.ai.deepseek-base-url}") String baseUrl,
            @Value("${neuralbase.ai.deepseek-model}") String model) {
        var api = OpenAiApi.builder()
            .baseUrl(baseUrl)
            .completionsPath("/chat/completions")   // DeepSeek 官方路径不带 /v1，显式覆盖 Spring AI 默认 /v1/chat/completions
            .apiKey(apiKey)
            .build();
        var options = OpenAiChatOptions.builder()
            .model(model)
            .temperature(0.7)
            .build();
        return OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(options)
            .build();
    }
}
