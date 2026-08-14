package com.neuralbase.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.service.NewsAiService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsAiServiceTest {

    /** 返回固定 JSON 文本的假 ChatModel(不依赖 Spring 上下文)。 */
    private static class FakeChatModel implements ChatModel {
        private final String output;

        FakeChatModel(String output) {
            this.output = output;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(output))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void summarizeParsesJsonAndKeepsKnownCategory() {
        NewsAiService svc = new NewsAiService(
            new FakeChatModel("{\"summary\":\"一个很酷的新项目\",\"category\":\"工具\"}"),
            new ObjectMapper());
        var r = svc.summarize("cool project");
        assertThat(r.summary()).isEqualTo("一个很酷的新项目");
        assertThat(r.category()).isEqualTo("工具");
    }

    @Test
    void unknownCategoryFallsBackToOther() {
        NewsAiService svc = new NewsAiService(
            new FakeChatModel("{\"summary\":\"s\",\"category\":\"神秘领域\"}"),
            new ObjectMapper());
        assertThat(svc.summarize("x").category()).isEqualTo("其他");
    }

    @Test
    void stripsMarkdownCodeFence() {
        NewsAiService svc = new NewsAiService(
            new FakeChatModel("```json\n{\"summary\":\"摘要\",\"category\":\"论文\"}\n```"),
            new ObjectMapper());
        var r = svc.summarize("x");
        assertThat(r.category()).isEqualTo("论文");
        assertThat(r.summary()).isEqualTo("摘要");
    }
}
