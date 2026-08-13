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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MockEmbeddingConfig.class)
class DocumentContentTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentMapper documentMapper;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    private Document uploadDoc(String name, String text) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", name, MediaType.TEXT_MARKDOWN_VALUE, text.getBytes());
        return documentService.upload(file, 1L);
    }

    @Test
    void contentReturnsTextForReadyDoc() throws Exception {
        Document doc = uploadDoc("readme.md", "# 文档阅读器\n\n这是正文内容。");
        documentService.process(doc.getId());

        mvc.perform(get("/api/document/" + doc.getId() + "/content")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.id").value(doc.getId()))
            .andExpect(jsonPath("$.data.fileType").value("md"))
            .andExpect(jsonPath("$.data.content").value(containsString("这是正文内容")));
    }

    @Test
    void contentForPendingReturns400() throws Exception {
        Document doc = uploadDoc("pending.md", "x");
        mvc.perform(get("/api/document/" + doc.getId() + "/content")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value(containsString("尚未处理完成")));
    }

    @Test
    void contentForFailedReturns400WithError() throws Exception {
        Document doc = uploadDoc("bad.md", "x");
        doc.setStatus("FAILED");
        doc.setError("Tika 解析失败");
        documentMapper.updateById(doc);
        mvc.perform(get("/api/document/" + doc.getId() + "/content")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("处理失败")));
    }

    @Test
    void contentForMissingDocReturns400() throws Exception {
        mvc.perform(get("/api/document/999999/content")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("不存在")));
    }

    @Test
    void contentEmptyReturns400() throws Exception {
        Document doc = uploadDoc("legacy.md", "x");
        doc.setStatus("READY");
        documentMapper.updateById(doc); // 模拟 V5 之前的存量文档:READY 但 content 为空
        mvc.perform(get("/api/document/" + doc.getId() + "/content")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("内容缺失")));
    }

    @Test
    void contentWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/document/1/content"))
            .andExpect(status().isUnauthorized());
    }
}
