package com.neuralbase.baguwen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.service.BaguwenAiService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaguwenAiServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChatModel chatModelReturning(String text) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
            }
            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.empty();
            }
        };
    }

    @Test
    void parsesJsonWithMarkdownFence() {
        String raw = "```json\n"
            + "{\"qa_pairs\":[{\"question\":\"什么是 JVM 内存模型？\",\"answer\":\"堆、栈、方法区等。\",\"difficulty\":\"中等\"}],"
            + "\"tags\":[\"Java\",\"JVM\"]}\n```";
        BaguwenAiService service = new BaguwenAiService(chatModelReturning(raw), objectMapper);

        BaguwenAiService.ExtractionResult result = service.extract("正文内容", "标题");

        assertThat(result.tags()).containsExactly("Java", "JVM");
        assertThat(result.qaPairs()).hasSize(1);
        assertThat(result.qaPairs().get(0).question()).contains("JVM 内存模型");
        assertThat(result.qaPairs().get(0).difficulty()).isEqualTo("中等");
    }

    @Test
    void throwsWhenJsonMalformed() {
        BaguwenAiService service = new BaguwenAiService(chatModelReturning("这不是 JSON"), objectMapper);

        assertThatThrownBy(() -> service.extract("正文", "标题"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("无法解析");
    }

    @Test
    void throwsWhenNoQaPairs() {
        BaguwenAiService service = new BaguwenAiService(
            chatModelReturning("{\"qa_pairs\":[],\"tags\":[]}"), objectMapper);

        assertThatThrownBy(() -> service.extract("正文", "标题"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("没有提取到任何问答对");
    }
}
