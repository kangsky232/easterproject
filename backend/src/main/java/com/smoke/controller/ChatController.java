package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.ChatRequest;
import com.smoke.dto.ChatResponse;
import com.smoke.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return Result.ok(chatService.answer(request.question(), request.alertId()));
    }
}
