package com.neuralbase.ai;

import com.neuralbase.config.MockEmbeddingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Import(MockEmbeddingConfig.class)
class AiContextTest {

    @Autowired
    private VectorStore vectorStore;

    @Test
    void vectorStoreBeanAvailable() {
        assertNotNull(vectorStore);
    }
}
