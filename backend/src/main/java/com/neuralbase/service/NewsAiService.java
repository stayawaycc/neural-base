package com.neuralbase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class NewsAiService {

    private static final Set<String> CATEGORIES = Set.of("大模型", "框架", "工具", "论文", "其他");
    private static final String SYSTEM_PROMPT =
        "你是 AI 技术日报编辑。根据给定的文章标题(可能附带一句话描述),"
        + "输出 JSON:{\"summary\":\"一句话中文摘要(不超过 60 字)\",\"category\":\"大模型|框架|工具|论文|其他\"}。"
        + "只输出 JSON,不要 markdown 代码块。";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public NewsAiService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public SummaryResult summarize(String title) {
        ChatResponse resp = chatModel.call(new Prompt(
            new SystemMessage(SYSTEM_PROMPT), new UserMessage(title)));
        String raw = resp.getResult() == null ? null : resp.getResult().getOutput().getText();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("AI 未返回内容");
        }
        String json = raw.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("```\\s*$", "").strip();
        }
        try {
            SummaryResult result = objectMapper.readValue(json, SummaryResult.class);
            String category = result.category() == null ? "其他" : result.category().strip();
            if (!CATEGORIES.contains(category)) {
                category = "其他";
            }
            String summary = result.summary() == null ? "" : result.summary().strip();
            return new SummaryResult(summary, category);
        } catch (Exception e) {
            throw new IllegalStateException("AI 返回的 JSON 无法解析: " + raw, e);
        }
    }

    public record SummaryResult(String summary, String category) {
    }
}
