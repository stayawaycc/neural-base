package com.neuralbase.dto;

public record RagSource(Long docId, String title, String content, double score, Long kbId, String kbName) {
}
