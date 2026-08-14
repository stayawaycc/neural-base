package com.neuralbase.dto;

import java.time.OffsetDateTime;

public record ConversationView(Long id, String title, Long kbId, Boolean favorite,
                               OffsetDateTime createdAt, OffsetDateTime updatedAt,
                               String lastMessageContent) {
}
