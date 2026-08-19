package com.neuralbase.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockChatConfig;
import com.neuralbase.dto.ChatRequest;
import com.neuralbase.dto.RagSource;
import com.neuralbase.entity.Conversation;
import com.neuralbase.entity.Document;
import com.neuralbase.entity.Message;
import com.neuralbase.mapper.ConversationMapper;
import com.neuralbase.mapper.MessageMapper;
import com.neuralbase.service.ChatService;
import com.neuralbase.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ChatServiceTest {

    @Autowired private ChatService chatService;
    @Autowired private ConversationMapper conversationMapper;
    @Autowired private MessageMapper messageMapper;
    @Autowired private DocumentService documentService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearPrompts() {
        MockChatConfig.PROMPTS.clear();
        // 共享 test 库的 vector_store/document 会被其他测试类（DocumentSearchTest 等）累积污染，
        // 而 RAG 用例要求 kb=1 的检索结果中必须命中 rag.txt，因此每例前清理，保证确定性。
        jdbcTemplate.update("DELETE FROM vector_store");
        jdbcTemplate.update("DELETE FROM document");
    }

    @Test
    void firstMessageCreatesConversationAndPersistsBothSides() throws Exception {
        List<ServerSentEvent<String>> events =
            chatService.stream(new ChatRequest(null, null, "你好", null)).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.get(0).event()).isEqualTo("meta");
        long convId = objectMapper.readTree(events.get(0).data()).get("conversationId").asLong();

        List<String> deltas = events.stream()
            .filter(e -> "delta".equals(e.event())).map(ServerSentEvent::data).toList();
        assertThat(deltas).containsExactly("mock 回答", "片段二");
        assertThat(events.get(events.size() - 1).event()).isEqualTo("done");

        Conversation conv = conversationMapper.selectById(convId);
        assertThat(conv.getTitle()).isEqualTo("你好");

        List<Message> messages = messageMapper.selectList(
            new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, convId).orderByAsc(Message::getId));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo("USER");
        assertThat(messages.get(0).getContent()).isEqualTo("你好");
        assertThat(messages.get(1).getRole()).isEqualTo("ASSISTANT");
        assertThat(messages.get(1).getContent()).isEqualTo("mock 回答片段二");
    }

    @Test
    void ragModeRetrievesAndStoresSources() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "rag.txt", "text/plain",
            "NeuralBase RAG 测试内容。".repeat(200).getBytes());
        Document doc = documentService.upload(file, 1L);
        documentService.process(doc.getId());

        List<ServerSentEvent<String>> events =
            chatService.stream(new ChatRequest(null, 1L, "NeuralBase RAG 测试内容。", true)).collectList().block();
        assertThat(events).isNotNull();

        ServerSentEvent<String> sourcesEvent = events.stream()
            .filter(e -> "sources".equals(e.event())).findFirst().orElse(null);
        assertThat(sourcesEvent).isNotNull();
        List<RagSource> sources = objectMapper.readValue(sourcesEvent.data(),
            new TypeReference<List<RagSource>>() {});
        assertThat(sources).isNotEmpty();

        long convId = objectMapper.readTree(events.get(0).data()).get("conversationId").asLong();
        List<Message> messages = messageMapper.selectList(
            new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, convId).orderByAsc(Message::getId));
        assertThat(messages.get(1).getSources()).contains("NeuralBase RAG 测试内容");
    }

    @Test
    void secondTurnLoadsHistoryServerSide() throws Exception {
        List<ServerSentEvent<String>> first =
            chatService.stream(new ChatRequest(null, null, "问题一", null)).collectList().block();
        long convId = objectMapper.readTree(first.get(0).data()).get("conversationId").asLong();

        chatService.stream(new ChatRequest(convId, null, "问题二", null)).collectList().block();

        Prompt lastPrompt = MockChatConfig.PROMPTS.get(MockChatConfig.PROMPTS.size() - 1);
        assertThat(lastPrompt.getInstructions()).anySatisfy(m -> {
            if (m instanceof UserMessage u) {
                assertThat(u.getText()).isEqualTo("问题一");
            }
        });
        assertThat(lastPrompt.getInstructions()).anySatisfy(m -> {
            if (m instanceof AssistantMessage a) {
                assertThat(a.getText()).isEqualTo("mock 回答片段二");
            }
        });
    }
}
