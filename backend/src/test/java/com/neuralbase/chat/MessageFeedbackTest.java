package com.neuralbase.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MockEmbeddingConfig.class)
class MessageFeedbackTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    private long seedConversationAndMessage(String tk) throws Exception {
        long cid = objectMapper.readTree(mvc.perform(post("/api/conversation")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"feedback-test\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
            .get("data").get("id").asLong();
        jdbcTemplate.update(
            "INSERT INTO message (conversation_id, role, content, created_at) VALUES (?, 'ASSISTANT', 'hi', now())",
            cid);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM message WHERE conversation_id = ?", Long.class, cid);
    }

    @Test
    void feedbackSetSwitchCancel() throws Exception {
        String tk = token();
        long mid = seedConversationAndMessage(tk);

        mvc.perform(post("/api/message/" + mid + "/feedback")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"LIKE\"}"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/message/" + mid + "/feedback")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"DISLIKE\"}"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/message/" + mid + "/feedback")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":null}"))
            .andExpect(status().isOk());

        String fb = jdbcTemplate.queryForObject(
            "SELECT feedback FROM message WHERE id = ?", String.class, mid);
        assertThat(fb).isNull();
    }

    @Test
    void feedbackRejectsInvalidAndMissing() throws Exception {
        String tk = token();
        long mid = seedConversationAndMessage(tk);

        mvc.perform(post("/api/message/" + mid + "/feedback")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"LOVE\"}"))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/message/999999/feedback")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"LIKE\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("不存在")));
    }

    @Test
    void messagesExposeFeedback() throws Exception {
        String tk = token();
        long cid = jdbcTemplate.queryForObject(
            "SELECT conversation_id FROM message WHERE id = ?", Long.class,
            seedConversationAndMessage(tk));

        mvc.perform(get("/api/conversation/" + cid + "/messages")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].feedback").doesNotExist())
            .andReturn();

        long mid = jdbcTemplate.queryForObject(
            "SELECT id FROM message WHERE conversation_id = ?", Long.class, cid);
        mvc.perform(post("/api/message/" + mid + "/feedback")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"LIKE\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/conversation/" + cid + "/messages")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].feedback").value("LIKE"));
    }
}
