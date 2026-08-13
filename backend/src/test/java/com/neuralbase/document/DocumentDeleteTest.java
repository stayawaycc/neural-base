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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(MockEmbeddingConfig.class)
class DocumentDeleteTest {

    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentMapper documentMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deleteRemovesVectorsFileAndRow() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "delete-me.md", "text/markdown",
            ("删除测试内容。".repeat(200)).getBytes());
        Document doc = documentService.upload(file, 1L);
        documentService.process(doc.getId());

        Document before = documentMapper.selectById(doc.getId());
        assertThat(before.getStatus()).isEqualTo("READY");
        assertThat(before.getChunkCount()).isGreaterThan(0);

        documentService.delete(doc.getId());

        assertThat(documentMapper.selectById(doc.getId())).isNull();
        Integer vectors = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vector_store WHERE metadata->>'docId' = ?",
            Integer.class, String.valueOf(doc.getId()));
        assertThat(vectors).isZero();
        assertThat(Files.exists(Path.of(before.getFilePath()))).isFalse();
    }

    @Test
    void deleteMissingDocumentThrows() {
        assertThatThrownBy(() -> documentService.delete(999_999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("文档不存在");
    }
}
