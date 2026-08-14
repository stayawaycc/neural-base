package com.neuralbase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.dto.ConversationView;
import com.neuralbase.dto.MessageView;
import com.neuralbase.dto.RagSource;
import com.neuralbase.entity.Conversation;
import com.neuralbase.entity.Message;
import com.neuralbase.mapper.ConversationMapper;
import com.neuralbase.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public ConversationService(ConversationMapper conversationMapper, MessageMapper messageMapper,
                               ObjectMapper objectMapper,
                               org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Conversation create(String title, Long kbId) {
        Conversation c = new Conversation();
        c.setTitle(title == null || title.isBlank() ? "新对话" : title);
        c.setKbId(kbId);
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(c.getCreatedAt());
        conversationMapper.insert(c);
        return c;
    }

    public Conversation get(Long id) {
        Conversation c = conversationMapper.selectById(id);
        if (c == null) {
            throw new IllegalArgumentException("会话不存在: " + id);
        }
        return c;
    }

    public void touch(Long id) {
        Conversation c = get(id);
        c.setUpdatedAt(OffsetDateTime.now());
        conversationMapper.updateById(c);
    }

    public List<ConversationView> list() {
        List<Conversation> conversations = conversationMapper.selectList(
            new LambdaQueryWrapper<Conversation>().orderByDesc(Conversation::getUpdatedAt));
        Map<Long, String> lastByConversation = messageMapper.selectLastMessages().stream()
            .collect(Collectors.toMap(MessageMapper.LastMessageRow::getConversationId,
                MessageMapper.LastMessageRow::getContent, (a, b) -> a));
        return conversations.stream()
            .map(c -> new ConversationView(c.getId(), c.getTitle(), c.getKbId(), c.getFavorite(),
                c.getCreatedAt(), c.getUpdatedAt(), lastByConversation.get(c.getId())))
            .toList();
    }

    public List<MessageView> messages(Long conversationId) {
        get(conversationId);
        List<Message> messages = messageMapper.selectList(
            new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getId));
        return messages.stream().map(this::toView).toList();
    }

    public ConversationView rename(Long id, String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (title.length() > 100) {
            throw new IllegalArgumentException("标题过长(最多 100 字符)");
        }
        Conversation c = get(id);
        c.setTitle(title);
        c.setUpdatedAt(OffsetDateTime.now());
        conversationMapper.updateById(c);
        return list().stream().filter(v -> v.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + id));
    }

    public boolean toggleFavorite(Long id) {
        Conversation c = get(id);
        boolean next = !Boolean.TRUE.equals(c.getFavorite());
        c.setFavorite(next);
        conversationMapper.updateById(c);
        return next;
    }

    public void setFeedback(Long messageId, String feedback) {
        Message m = messageMapper.selectById(messageId);
        if (m == null) {
            throw new IllegalArgumentException("消息不存在: " + messageId);
        }
        if (feedback != null && !feedback.equals("LIKE") && !feedback.equals("DISLIKE")) {
            throw new IllegalArgumentException("feedback 只能是 LIKE/DISLIKE/null");
        }
        if (feedback == null) {
            // MyBatis-Plus updateById 默认忽略 null 字段,取消反馈必须走 SQL
            jdbcTemplate.update("UPDATE message SET feedback = NULL WHERE id = ?", messageId);
        } else {
            m.setFeedback(feedback);
            messageMapper.updateById(m);
        }
    }

    public void delete(Long conversationId) {
        conversationMapper.deleteById(conversationId);   // message 由 DB CASCADE 删除
    }

    public List<RagSource> parseSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RagSource>>() {});
        } catch (Exception e) {
            log.warn("failed to parse sources json: {}", json, e);
            return List.of();
        }
    }

    private MessageView toView(Message m) {
        return new MessageView(m.getId(), m.getRole(), m.getContent(),
            parseSources(m.getSources()), m.getFeedback(), m.getCreatedAt());
    }
}
