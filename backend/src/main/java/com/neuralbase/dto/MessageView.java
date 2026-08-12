package com.neuralbase.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record MessageView(Long id, String role, String content, List<RagSource> sources, OffsetDateTime createdAt) {
}
