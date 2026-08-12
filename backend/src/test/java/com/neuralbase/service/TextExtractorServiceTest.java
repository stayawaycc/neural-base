package com.neuralbase.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TextExtractorServiceTest {

    @Autowired
    private TextExtractorService extractor;

    @Test
    void extractsTxt() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/extract/sample.txt")) {
            String text = extractor.extract(in, "sample.txt");
            assertThat(text).contains("纯文本提取测试");
        }
    }

    @Test
    void extractsMarkdown() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/extract/sample.md")) {
            String text = extractor.extract(in, "sample.md");
            assertThat(text).contains("NeuralBase 测试");
        }
    }
}
