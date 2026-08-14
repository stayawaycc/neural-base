package com.neuralbase.controller;

import com.neuralbase.common.Result;
import com.neuralbase.dto.ConversationView;
import com.neuralbase.dto.MessageView;
import com.neuralbase.entity.Conversation;
import com.neuralbase.service.ConversationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public Result<Conversation> create(@RequestBody(required = false) CreateConversationRequest req) {
        String title = req == null ? null : req.title();
        Long kbId = req == null ? null : req.kbId();
        return Result.ok(conversationService.create(title, kbId));
    }

    @GetMapping
    public Result<List<ConversationView>> list() {
        return Result.ok(conversationService.list());
    }

    @GetMapping("/{id}/messages")
    public Result<List<MessageView>> messages(@PathVariable Long id) {
        return Result.ok(conversationService.messages(id));
    }

    @PutMapping("/{id}")
    public Result<ConversationView> rename(@PathVariable Long id, @RequestBody RenameRequest req) {
        return Result.ok(conversationService.rename(id, req == null ? null : req.title()));
    }

    @PutMapping("/{id}/favorite")
    public Result<Boolean> toggleFavorite(@PathVariable Long id) {
        return Result.ok(conversationService.toggleFavorite(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        conversationService.delete(id);
        return Result.ok(null);
    }

    public record CreateConversationRequest(String title, Long kbId) {
    }

    public record RenameRequest(String title) {
    }
}
