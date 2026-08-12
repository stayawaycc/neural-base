package com.neuralbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neuralbase.entity.Message;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MessageMapper extends BaseMapper<Message> {

    /** sources 列是 jsonb，MyBatis-Plus 默认把 String 当 varchar 绑定会报类型不匹配，这里显式 CAST 为 jsonb。 */
    @Insert("""
        INSERT INTO message (conversation_id, role, content, sources, created_at)
        VALUES (#{conversationId}, #{role}, #{content}, CAST(#{sources} AS jsonb), #{createdAt})
        """)
    int insertWithSources(Message message);

    /** 取某会话最近 limit 条消息，升序返回（子查询倒排截断再正排）。 */
    @Select("""
        SELECT * FROM (
            SELECT * FROM message
            WHERE conversation_id = #{conversationId}
            ORDER BY id DESC
            LIMIT #{limit}
        ) recent ORDER BY id ASC
        """)
    List<Message> selectRecentAsc(@Param("conversationId") Long conversationId, @Param("limit") int limit);

    /** 每个会话的最后一条消息内容（DISTINCT ON + 倒序）。map-underscore-to-camel-case 自动映射 conversation_id→conversationId。 */
    @Select("""
        SELECT DISTINCT ON (conversation_id) conversation_id, content
        FROM message
        ORDER BY conversation_id, id DESC
        """)
    List<LastMessageRow> selectLastMessages();

    class LastMessageRow {
        private Long conversationId;
        private String content;

        public Long getConversationId() { return conversationId; }
        public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
