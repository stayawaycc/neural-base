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
                // 保持 one-hot（同文本→cosine 1，异文本→正交），但对文本前 8 个字符做哈希而非整文本：
                // PgVectorStore 的 COSINE 过滤是 distance < 1 - threshold（默认 threshold=0 → < 1.0），
                // 整文本 one-hot 会让 query 与任何 chunk 正交（distance=1.0）被过滤掉，检索永远为空。
                // 基于前缀哈希后，query 与含同一前缀的 chunk 映射到同一桶 → cosine 1 → 可命中。
                float[] v = new float[4096];
                if (!text.isEmpty()) {
                    v[Math.floorMod(text.substring(0, Math.min(8, text.length())).hashCode(), 4096)] = 1.0f;
                }
                return v;
            }
        };
    }
}
