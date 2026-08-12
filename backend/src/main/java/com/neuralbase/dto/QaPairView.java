package com.neuralbase.dto;

import com.neuralbase.entity.BaguwenQaPair;

public record QaPairView(Long id, String question, String answer, String difficulty) {
    public static QaPairView from(BaguwenQaPair q) {
        return new QaPairView(q.getId(), q.getQuestion(), q.getAnswer(), q.getDifficulty());
    }
}
