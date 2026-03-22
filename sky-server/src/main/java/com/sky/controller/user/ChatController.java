package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.exception.UserNotLoginException;
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
            throw new UserNotLoginException("未登录或token无效");
        }

        log.info("收到对话消息, userId: {}", userId);
        return unifiedAiChatService.chat(message, userId);
    }
}
