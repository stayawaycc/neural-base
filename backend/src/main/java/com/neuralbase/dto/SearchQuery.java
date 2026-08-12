package com.neuralbase.dto;

public record SearchQuery(String query, Long kbId, Integer topK) {
}
