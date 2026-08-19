package com.neuralbase.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.entity.Document;
import com.neuralbase.entity.Kb;
import com.neuralbase.service.DocumentService;
import com.neuralbase.service.KbService;
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
    @Autowired
    private KbService kbService;

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
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void searchDeduplicatesChunksPerDocument() throws Exception {
        MockMultipartFile multi = new MockMultipartFile(
            "file", "multi.md", "text/markdown",
            ("ABCDEFGH 内容一,同一文档的第一段。\n\n" +
                "ABCDEFGH 内容二,同一文档的第二段,前缀与第一段相同。").getBytes());
        Document multiDoc = documentService.upload(multi, 1L);
        documentService.process(multiDoc.getId());

        mvc.perform(post("/api/document/search")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    java.util.Map.of("query", "ABCDEFGH 内容一", "topK", 5))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].docId").value(multiDoc.getId()));
    }

    @Test
    void searchReturnsKbName() throws Exception {
        mvc.perform(post("/api/document/search")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    java.util.Map.of("query", "NeuralBase 测试文档内容，用于检索验证。", "topK", 3))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].kbName").isNotEmpty());
    }

    @Test
    void kbIdFilterIsolatesAcrossKbs() throws Exception {
        Kb kb2 = new Kb();
        kb2.setName("kb2");
        kbService.save(kb2);
        MockMultipartFile other = new MockMultipartFile(
            "file", "other.txt", "text/plain",
            ("OTHER 前缀独立内容，与 kb1 无关。".repeat(200)).getBytes());
        Document otherDoc = documentService.upload(other, kb2.getId());
        documentService.process(otherDoc.getId());

        String q = "OTHER 前缀独立内容，与 kb1 无关。";
        mvc.perform(post("/api/document/search")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("query", q, "kbId", kb2.getId(), "topK", 3))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].docId").value(otherDoc.getId()));
        mvc.perform(post("/api/document/search")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("query", q, "kbId", 1, "topK", 3))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.docId == " + otherDoc.getId() + ")]").doesNotExist());
    }

    @Test
    void searchWithInvalidInputReturns400() throws Exception {
        mvc.perform(post("/api/document/search")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"\",\"topK\":3}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/document/search")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"x\",\"topK\":0}"))
            .andExpect(status().isBadRequest());
    }
}
