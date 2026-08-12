package com.neuralbase.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(Long conversationId, Long kbId, @NotBlank(message = "问题不能为空") String question) {
}
