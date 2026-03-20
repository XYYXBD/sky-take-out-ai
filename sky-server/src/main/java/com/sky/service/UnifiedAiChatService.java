package com.sky.service;

import reactor.core.publisher.Flux;

public interface UnifiedAiChatService {
    Flux<String> chat(String userMessage, Long userId);
}
