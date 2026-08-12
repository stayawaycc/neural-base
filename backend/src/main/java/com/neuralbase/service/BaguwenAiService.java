package com.neuralbase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BaguwenAiService {

    private static final int MAX_CONTENT_CHARS = 12_000;
    private static final String SYSTEM_PROMPT =
        "请将以下技术文章整理为面试题格式，提取其中的问题和答案。要求："
        + "1. 每个问题简洁明了；2. 答案要点清晰，分条列出；"
        + "3. 标注难度等级（简单/中等/困难）；"
        + "4. 同时识别文章所属技术领域标签（如 Java/Spring/DB/算法/架构 等），一般 1~5 个；"
        + "5. 只输出 JSON，不要 markdown 代码块，格式："
        + "{\"qa_pairs\":[{\"question\":\"…\",\"answer\":\"…\",\"difficulty\":\"简单\"}],\"tags\":[\"Java\"]}";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public BaguwenAiService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public ExtractionResult extract(String content, String title) {
        String text = (title == null ? "" : title + "\n") + content;
        if (text.length() > MAX_CONTENT_CHARS) {
            text = text.substring(0, MAX_CONTENT_CHARS);
        }
        ChatResponse resp = chatModel.call(new Prompt(
            new SystemMessage(SYSTEM_PROMPT),
            new UserMessage(text)));
        String raw = resp.getResult() == null ? null : resp.getResult().getOutput().getText();
        return parse(raw);
    }

    private ExtractionResult parse(String raw) {
        if (raw == null) {
            throw new IllegalStateException("AI 未返回内容");
        }
        String json = raw.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("```\\s*$", "").strip();
        }
        try {
            ExtractionResult result = objectMapper.readValue(json, ExtractionResult.class);
            if (result.qaPairs() == null || result.qaPairs().isEmpty()) {
                throw new IllegalStateException("AI 没有提取到任何问答对");
            }
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new IllegalStateException("AI 返回的 JSON 无法解析: " + raw, e);
        }
    }

    /** qaPairs 组件加 @JsonAlias：AI 按提示词输出 snake_case 的 qa_pairs，而 record 属性名是 camelCase。 */
    public record ExtractionResult(
        @com.fasterxml.jackson.annotation.JsonAlias("qa_pairs") List<QaPair> qaPairs,
        List<String> tags) {
    }
    public record QaPair(String question, String answer, String difficulty) {
    }
}
