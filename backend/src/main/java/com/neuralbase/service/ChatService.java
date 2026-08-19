package com.neuralbase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.dto.ChatRequest;
import com.neuralbase.dto.RagSource;
import com.neuralbase.entity.Conversation;
import com.neuralbase.entity.Message;
import com.neuralbase.mapper.MessageMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final int HISTORY_WINDOW = 10;
    private static final int TOP_K = 5;

    private final ChatModel chatModel;
    private final ConversationService conversationService;
    private final DocumentService documentService;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public ChatService(ChatModel chatModel, ConversationService conversationService,
                       DocumentService documentService, MessageMapper messageMapper,
                       ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.conversationService = conversationService;
        this.documentService = documentService;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    public Flux<ServerSentEvent<String>> stream(ChatRequest req) {
        long start = System.currentTimeMillis();
        Conversation conv = req.conversationId() == null
            ? conversationService.create(deriveTitle(req.question()), req.kbId())
            : conversationService.get(req.conversationId());
        Long convId = conv.getId();
        log.info("[chat] 请求开始 conversationId={} kbId={} questionLen={} 新会话={}", convId, req.kbId(),
            req.question().length(), req.conversationId() == null);

        Message userMsg = new Message();
        userMsg.setConversationId(convId);
        userMsg.setRole("USER");
        userMsg.setContent(req.question());
        userMsg.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(userMsg);
        conversationService.touch(convId);

        List<Message> history = messageMapper.selectRecentAsc(convId, HISTORY_WINDOW);
        boolean ragMode = Boolean.TRUE.equals(req.rag()) || req.kbId() != null;
        List<RagSource> sources = ragMode ? documentService.search(req.question(), req.kbId(), TOP_K).stream()
            .map(r -> new RagSource(r.docId(), r.title(), r.content(), r.score(), r.kbId(), r.kbName()))
            .toList() : List.of();

        List<org.springframework.ai.chat.messages.Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(buildSystemPrompt(req.kbId(), sources)));
        for (Message m : history) {
            if ("USER".equals(m.getRole())) {
                promptMessages.add(new UserMessage(m.getContent()));
            } else {
                promptMessages.add(new AssistantMessage(m.getContent()));
            }
        }

        StringBuilder full = new StringBuilder();
        AtomicBoolean persisted = new AtomicBoolean(false);

        Flux<ServerSentEvent<String>> stream = chatModel.stream(new Prompt(promptMessages))
            .mapNotNull(resp -> resp.getResult().getOutput().getText())
            .filter(text -> text != null && !text.isBlank())
            .doOnNext(full::append)
            .map(text -> ServerSentEvent.<String>builder(text).event("delta").build())
            .doFinally(sig -> {
                if (persisted.compareAndSet(false, true)) {
                    try {
                        persistAssistant(convId, full.toString(), sources);
                        log.info("[chat] 完成 conversationId={} 字符数={} 引用片段={} 耗时={}ms", convId,
                            full.length(), sources.size(), System.currentTimeMillis() - start);
                    } catch (Exception ex) {
                        log.warn("persist assistant message failed for conversation {}", convId, ex);
                    }
                }
            })
            .onErrorResume(e -> {
                log.error("chat stream error for conversation {}", convId, e);
                return Flux.just(ServerSentEvent.<String>builder("stream error").event("error").build());
            });

        List<ServerSentEvent<String>> prefix = new ArrayList<>();
        prefix.add(ServerSentEvent.<String>builder(toJson(Map.of("conversationId", convId))).event("meta").build());
        if (!sources.isEmpty()) {
            prefix.add(ServerSentEvent.<String>builder(toJson(sources)).event("sources").build());
        }
        return Flux.concat(
            Flux.fromIterable(prefix),
            stream,
            Flux.just(ServerSentEvent.<String>builder("").event("done").build()));
    }

    private String deriveTitle(String question) {
        String t = question.strip();
        return t.length() > 20 ? t.substring(0, 20) : t;
    }

    private String buildSystemPrompt(Long kbId, List<RagSource> sources) {
        if (kbId == null) {
            return "你是 NeuralBase 的 AI 助手，擅长技术问答和知识整理。";
        }
        String context = sources.stream()
            .map(s -> "- " + s.content())
            .collect(Collectors.joining("\n"));
        return "你是 NeuralBase 的 AI 助手。请基于以下检索到的文档片段回答用户问题。"
            + "如果文档中没有相关信息，请明确告知用户。\n\n--- 检索到的文档片段 ---\n"
            + (context.isEmpty() ? "（未检索到相关内容）" : context);
    }

    private void persistAssistant(Long conversationId, String content, List<RagSource> sources) {
        Message assistant = new Message();
        assistant.setConversationId(conversationId);
        assistant.setRole("ASSISTANT");
        assistant.setContent(content);
        assistant.setCreatedAt(OffsetDateTime.now());
        if (!sources.isEmpty()) {
            assistant.setSources(toJson(sources));
        }
        messageMapper.insertWithSources(assistant);
        conversationService.touch(conversationId);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
