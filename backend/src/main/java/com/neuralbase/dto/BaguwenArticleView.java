package com.neuralbase.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BaguwenArticleView(Long id, String title, String url, List<String> tags,
                                 OffsetDateTime createdAt) {
}
