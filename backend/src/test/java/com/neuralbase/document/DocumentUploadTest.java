package com.neuralbase.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MockEmbeddingConfig.class)
class DocumentUploadTest {

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
    void uploadCreatesPendingDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "notes.md", MediaType.TEXT_MARKDOWN_VALUE, "# 标题\n\n正文内容".getBytes());
        MvcResult r = mvc.perform(multipart("/api/document")
                .file(file)
                .param("kbId", "1")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.title").value("notes.md"))
            .andReturn();
        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).get("data");
        assertThat(data.get("id").asLong()).isPositive();
        String status = data.get("status").asText();
        assertThat(status).isIn("PENDING", "PROCESSING", "READY", "FAILED");
    }
}
