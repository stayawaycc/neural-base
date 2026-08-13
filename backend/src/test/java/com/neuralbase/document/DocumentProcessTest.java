package com.neuralbase.document;

import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.entity.Document;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(MockEmbeddingConfig.class)
class DocumentProcessTest {

    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentMapper documentMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void uploadThenProcessMarksReadyAndStoresChunks() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "tutorial.md", "text/markdown",
            ("NeuralBase 教程\n\n" + "Java 虚拟线程详解内容。".repeat(300)).getBytes());
        Document doc = documentService.upload(file, 1L);
        assertThat(doc.getStatus()).isEqualTo("PENDING");

        documentService.process(doc.getId());
        documentService.process(doc.getId()); // 幂等：重复处理不应产生重复 chunk

        Document after = documentMapper.selectById(doc.getId());
        assertThat(after.getStatus()).isEqualTo("READY");
        assertThat(after.getChunkCount()).isGreaterThan(0);
        assertThat(after.getFilePath()).isNotBlank();
        assertThat(after.getContent()).contains("Java 虚拟线程详解内容");

        Integer stored = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vector_store WHERE metadata->>'docId' = ?",
            Integer.class, String.valueOf(doc.getId()));
        assertThat(stored).isEqualTo(after.getChunkCount());
    }
}
