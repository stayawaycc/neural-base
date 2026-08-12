package com.neuralbase.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BaguwenDetail(Long id, String title, String url, String content,
                            List<String> tags, OffsetDateTime createdAt, List<QaPairView> qaPairs) {
}
