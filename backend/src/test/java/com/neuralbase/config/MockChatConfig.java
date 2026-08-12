package com.neuralbase.config;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
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

    private static final String BAGUWEN_JSON =
        "{\"qa_pairs\":[{\"question\":\"什么是 JVM 内存模型？\",\"answer\":\"分为堆、栈、方法区等。\",\"difficulty\":\"中等\"}],"
        + "\"tags\":[\"Java\",\"JVM\"]}";

    @Bean
    @Primary
    public ChatModel mockChatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                PROMPTS.add(prompt);
                boolean baguwen = prompt.getInstructions().stream().anyMatch(m ->
                    m instanceof SystemMessage sm && sm.getText().contains("整理为面试题格式"));
                String text = baguwen ? BAGUWEN_JSON : "mock 回复";
                return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                PROMPTS.add(prompt);
                // 模拟 DeepSeek 流式返回的 null 文本 chunk（Reactor 会拒绝 Flux 中的字面 null 元素，
                // 因此用空字符串作为哨兵，映射为 AssistantMessage(null)，触发 ChatService 的 null 文本分支）。
                return Flux.just("mock 回答", "", "片段二")
                    .map(text -> new ChatResponse(List.of(new Generation(
                        text.isEmpty() ? new AssistantMessage(null) : new AssistantMessage(text)))));
            }
        };
    }
}
