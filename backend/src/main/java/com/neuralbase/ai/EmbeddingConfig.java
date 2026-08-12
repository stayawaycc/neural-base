package com.neuralbase.ai;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class EmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${neuralbase.ai.siliconflow-api-key}") String apiKey,
            @Value("${neuralbase.ai.siliconflow-base-url}") String baseUrl) {
        var api = OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .build();
        var options = OpenAiEmbeddingOptions.builder()
            .model("Qwen/Qwen3-Embedding-8B")
            .dimensions(4096)
            .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }
}
