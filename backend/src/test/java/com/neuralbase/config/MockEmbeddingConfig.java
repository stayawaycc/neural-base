package com.neuralbase.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MockEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel mockEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                var embeddings = request.getInstructions().stream()
                    .map(text -> new Embedding(oneHot(text), null))
                    .toList();
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public float[] embed(Document document) {
                return oneHot(document.getText());
            }

            @Override
            public int dimensions() {
                return 4096;
            }

            private float[] oneHot(String text) {
                float[] v = new float[4096];
                v[Math.floorMod(text.hashCode(), 4096)] = 1.0f;
                return v;
            }
        };
    }
}
