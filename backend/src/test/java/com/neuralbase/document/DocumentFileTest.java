package com.neuralbase.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.entity.Document;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MockEmbeddingConfig.class)
class DocumentFileTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentMapper documentMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    @Test
    void fileStreamsOriginalBytesWithInlineDisposition() throws Exception {
        byte[] bytes = "# 标题\n\n正文".getBytes();
        Document doc = documentService.upload(new MockMultipartFile(
            "file", "笔记.md", MediaType.TEXT_MARKDOWN_VALUE, bytes), 1L);

        mvc.perform(get("/api/document/" + doc.getId() + "/file")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("text/markdown")))
            .andExpect(header().string("Content-Disposition", containsString("inline")))
            .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8")))
            .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(bytes));
    }

    @Test
    void fileReturnsPdfContentTypeWithoutProcessing() throws Exception {
        Document doc = documentService.upload(new MockMultipartFile(
            "file", "paper.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.4 fake".getBytes()), 1L);

        mvc.perform(get("/api/document/" + doc.getId() + "/file")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("application/pdf")));
    }

    @Test
    void fileForDocWithoutOriginalReturns400() throws Exception {
        Document doc = documentService.upload(new MockMultipartFile(
            "file", "favorite.md", MediaType.TEXT_MARKDOWN_VALUE, "x".getBytes()), 1L);
        // 注意:MyBatis-Plus updateById 默认忽略 null 字段,置空 file_path 必须走 SQL
        jdbcTemplate.update("UPDATE document SET file_path = NULL WHERE id = ?", doc.getId());

        mvc.perform(get("/api/document/" + doc.getId() + "/file")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isBadRequest())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("无原文件"));
    }

    @Test
    void fileForMissingDiskFileReturns404() throws Exception {
        Document doc = documentService.upload(new MockMultipartFile(
            "file", "gone.md", MediaType.TEXT_MARKDOWN_VALUE, "x".getBytes()), 1L);
        Files.deleteIfExists(Path.of(doc.getFilePath()));

        mvc.perform(get("/api/document/" + doc.getId() + "/file")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isNotFound());
    }

    @Test
    void fileWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/document/1/file"))
            .andExpect(status().isUnauthorized());
    }
}
