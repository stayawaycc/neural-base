package com.neuralbase.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ConversationCrudTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    @Test
    void createListMessagesDelete() throws Exception {
        String tk = token();

        MvcResult created = mvc.perform(post("/api/conversation")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"第一个会话\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.title").value("第一个会话"))
            .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asLong();

        MvcResult list = mvc.perform(get("/api/conversation").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        JsonNode arr = objectMapper.readTree(list.getResponse().getContentAsString()).get("data");
        assertThat(arr).anySatisfy(n -> assertThat(n.get("id").asLong()).isEqualTo(id));

        mvc.perform(get("/api/conversation/" + id + "/messages").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());

        mvc.perform(delete("/api/conversation/" + id).header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        // 删除后再查消息 → 会话不存在 → 400（GlobalExceptionHandler 处理 IllegalArgumentException）
        mvc.perform(get("/api/conversation/" + id + "/messages").header("Authorization", "Bearer " + tk))
            .andExpect(status().isBadRequest());
    }

    @Test
    void renameValidatesAndPersists() throws Exception {
        String tk = token();
        long id = objectMapper.readTree(mvc.perform(post("/api/conversation")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"旧标题\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
            .get("data").get("id").asLong();

        mvc.perform(put("/api/conversation/" + id)
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"新标题\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("新标题"));

        mvc.perform(put("/api/conversation/" + id)
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("标题")));

        mvc.perform(put("/api/conversation/" + id)
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + "x".repeat(101) + "\"}"))
            .andExpect(status().isBadRequest());

        mvc.perform(put("/api/conversation/999999")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void favoriteToggles() throws Exception {
        String tk = token();
        long id = objectMapper.readTree(mvc.perform(post("/api/conversation")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"收藏测试\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
            .get("data").get("id").asLong();

        mvc.perform(put("/api/conversation/" + id + "/favorite")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));

        mvc.perform(put("/api/conversation/" + id + "/favorite")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(false));

        mvc.perform(get("/api/conversation").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void conversationEndpointsRequireAuth() throws Exception {
        mvc.perform(get("/api/conversation")).andExpect(status().isUnauthorized());
    }
}
