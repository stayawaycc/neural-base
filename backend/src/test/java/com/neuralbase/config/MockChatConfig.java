package com.neuralbase.config;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Configuration
public class MockChatConfig {

    /** 每次 prompt 调用都会记录（含 SystemMessage 的 RAG 上下文与多轮历史），测试断言用。测试前请 clear。 */
    public static final List<Prompt> PROMPTS = new CopyOnWriteArrayList<>();

    @Bean
    @Primary
    public ChatModel mockChatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                PROMPTS.add(prompt);
                return new ChatResponse(List.of(new Generation(new AssistantMessage("mock 回复"))));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                PROMPTS.add(prompt);
                return Flux.just("mock 回答", "片段二")
                    .map(text -> new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
            }
        };
    }
}
