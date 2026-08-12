package com.neuralbase.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.entity.Document;
import com.neuralbase.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MockEmbeddingConfig.class)
class DocumentSearchTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DocumentService documentService;

    private String token;

    @BeforeEach
    void seedAndLogin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "seed.txt", "text/plain",
            "NeuralBase 测试文档内容，用于检索验证。".repeat(200).getBytes());
        Document doc = documentService.upload(file, 1L);
        documentService.process(doc.getId());

        var r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        token = objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    @Test
    void searchReturnsExactMatchChunk() throws Exception {
        String query = "NeuralBase 测试文档内容，用于检索验证。".repeat(1);
        mvc.perform(post("/api/document/search")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    java.util.Map.of("query", query, "topK", 3))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].content").exists())
            .andExpect(jsonPath("$.data[0].docId").isNumber());
    }

    @Test
    void searchReturnsEmptyForGibberish() throws Exception {
        mvc.perform(post("/api/document/search")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    java.util.Map.of("query", "zzz qqq xxx 无意义", "topK", 3))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }
}
