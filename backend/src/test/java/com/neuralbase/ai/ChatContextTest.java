package com.neuralbase.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class ChatContextTest {

    @Autowired
    private ChatModel chatModel;

    @Test
    void chatModelBeanAvailable() {
        assertNotNull(chatModel);
    }
}
