package com.neuralbase.dto;

public record SearchResult(Long docId, String title, String content, double score, Long kbId, String kbName) {
}
