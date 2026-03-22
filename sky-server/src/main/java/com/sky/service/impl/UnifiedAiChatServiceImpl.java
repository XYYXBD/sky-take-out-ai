package com.sky.service.impl;

import com.sky.aiService.RagAgent;
import com.sky.aiService.IntentRouterAiService;
import com.sky.aiService.OrderAgent;
import com.sky.context.AgentUserContextRegistry;
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
    @Autowired
    private AgentUserContextRegistry agentUserContextRegistry;

    @Override
    public Flux<String> chat(String userMessage, Long userId) {
        String orderMemoryId = "chat:order:user:" + userId;
        String qaMemoryId = "chat:qa:user:" + userId;
        agentUserContextRegistry.bind(orderMemoryId, userId);
        agentUserContextRegistry.bind(qaMemoryId, userId);

        log.info("AI chat start, userId={}, orderMemoryId={}, qaMemoryId={}, message={}",
                userId, orderMemoryId, qaMemoryId, userMessage);

        if (isCartIntent(userMessage)) {
            log.info("AI chat shortcut CART, userId={}, memoryId={}", userId, orderMemoryId);
            return orderAgent.handleOrderQuery(orderMemoryId, userMessage);
        }
        RouteDecision decision = intentRouterAiService.route(userMessage);
        if (decision == null || decision.getIntent() == null) {
            log.info("AI chat route fallback QA, userId={}, memoryId={}", userId, qaMemoryId);
            return ragAgent.chat(qaMemoryId, userMessage);
        }
        log.info("AI chat route decision={}, userId={}, orderMemoryId={}, qaMemoryId={}",
                decision.getIntent(), userId, orderMemoryId, qaMemoryId);
        return switch (decision.getIntent()) {
            case CART -> orderAgent.handleOrderQuery(orderMemoryId, userMessage);
            case BOTH -> orderAgent.handleOrderQuery(orderMemoryId, userMessage);
            case QA -> ragAgent.chat(qaMemoryId, userMessage);
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
