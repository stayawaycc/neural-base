package com.neuralbase.controller;

import com.neuralbase.common.Result;
import com.neuralbase.service.ConversationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    private final ConversationService conversationService;

    public MessageController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/{id}/feedback")
    public Result<Void> feedback(@PathVariable Long id, @RequestBody FeedbackRequest req) {
        conversationService.setFeedback(id, req == null ? null : req.feedback());
        return Result.ok(null);
    }

    public record FeedbackRequest(String feedback) {
    }
}
