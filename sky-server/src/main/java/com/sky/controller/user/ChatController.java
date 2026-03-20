package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.service.UnifiedAiChatService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;


@RequestMapping(value = "/user/chat", produces = "text/html;charset=UTF-8")
@Api
@RestController
@Slf4j
public class ChatController {

    @Autowired
    private UnifiedAiChatService unifiedAiChatService;

    @GetMapping
    public Flux<String> chat(@RequestParam String message) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new IllegalStateException("未登录或token无效");
        }

        // memoryId 由后端基于当前登录用户生成，避免前端越权指定
        String memoryId = "chat:user:" + userId;

        log.info("收到对话消息, userId: {}, memoryId: {}", userId, memoryId);
        return unifiedAiChatService.chat(message, userId);
    }
}
