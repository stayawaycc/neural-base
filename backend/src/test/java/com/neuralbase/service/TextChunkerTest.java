package com.neuralbase.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(2000, 300);

    @Test
    void shortTextYieldsSingleChunk() {
        List<String> chunks = chunker.chunk("第一段。\n\n第二段。");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("第一段", "第二段");
    }

    @Test
    void longTextSplitsIntoMultipleChunks() {
        String text = "字".repeat(5000); // 单段超限 → 硬切
        List<String> chunks = chunker.chunk(text);
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.stream().allMatch(c -> c.length() <= 2000)).isTrue();
    }

    @Test
    void overlapCarriesTailOfPreviousChunk() {
        String text = ("段落A内容".repeat(600)) + "\n\n" + ("段落B内容".repeat(600));
        List<String> chunks = chunker.chunk(text);
        assertThat(chunks.size()).isGreaterThan(1);
        // 后一块应含前一块的尾部重叠
        String prevTail = chunks.get(0).substring(chunks.get(0).length() - 300);
        assertThat(chunks.get(1)).contains(prevTail);
    }

    @Test
    void blankTextYieldsEmpty() {
        assertThat(chunker.chunk("   \n\n  ")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    void rejectsInvalidConfig() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class, () -> new TextChunker(2000, 2000));
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class, () -> new TextChunker(2000, 1999));
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class, () -> new TextChunker(0, 300));
    }
}
