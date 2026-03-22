package com.sky.service.impl;

import com.sky.aiService.RagAgent;
import com.sky.aiService.IntentRouterAiService;
import com.sky.aiService.OrderAgent;
import com.sky.entity.RouteDecision;
import com.sky.service.UnifiedAiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class UnifiedAiChatServiceImpl implements UnifiedAiChatService {
    @Autowired
    private RagAgent ragAgent;
    @Autowired
    private OrderAgent orderAgent;
    @Autowired
    private IntentRouterAiService intentRouterAiService;

    @Override
    public Flux<String> chat(String userMessage, Long userId) {
        String memoryId = "chat:user:" + userId;
        if (isCartIntent(userMessage)) {
            return orderAgent.handleOrderQuery(memoryId, userMessage);
        }
        RouteDecision decision = intentRouterAiService.route(userMessage);
        if (decision == null || decision.getIntent() == null) {
            return ragAgent.chat(memoryId, userMessage);
        }
        return switch (decision.getIntent()) {
            case CART -> orderAgent.handleOrderQuery(memoryId, userMessage);
            case BOTH -> orderAgent.handleOrderQuery(memoryId, userMessage);
            case QA -> ragAgent.chat(memoryId, userMessage);
        };
    }

    private boolean isCartIntent(String userMessage) {
        return userMessage.contains("加入购物车")
                || userMessage.contains("加到购物车")
                || userMessage.contains("来一份")
                || userMessage.contains("来两份")
                || userMessage.contains("下单")
                || userMessage.contains("再来一个")
                || userMessage.contains("帮我点")
                || userMessage.contains("我要点");
    }
}
