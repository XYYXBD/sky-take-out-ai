package com.sky.controller.user;

import com.sky.aiService.AgentService;
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
    private AgentService agentService;

    @GetMapping
    public Flux<String> chat(String memoryId, String message) {
        log.info("收到对话消息");
        return agentService.chat(memoryId, message);
    }
}
