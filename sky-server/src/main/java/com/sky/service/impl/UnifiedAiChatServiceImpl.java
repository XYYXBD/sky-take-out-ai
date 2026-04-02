package com.sky.service.impl;

import com.sky.aiService.RagAgent;
import com.sky.aiService.IntentRouterAiService;
import com.sky.aiService.OrderAgent;
import com.sky.context.AgentUserContextRegistry;
import com.sky.entity.RouteDecision;
import com.sky.service.UnifiedAiChatService;
import com.sky.service.UserProfileService;
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
    @Autowired
    private UserProfileService userProfileService;

    @Override
    public Flux<String> chat(String userMessage, Long userId) {
        String orderMemoryId = "chat:order:user:" + userId;
        String qaMemoryId = "chat:qa:user:" + userId;
        agentUserContextRegistry.bind(orderMemoryId, userId);
        agentUserContextRegistry.bind(qaMemoryId, userId);

        String messageWithProfile = buildMessageWithProfile(userMessage, userId);

        log.info("AI chat start, userId={}, orderMemoryId={}, qaMemoryId={}, message={}",
                userId, orderMemoryId, qaMemoryId, userMessage);

        if (isCartIntent(userMessage)) {
            log.info("AI chat shortcut CART, userId={}, memoryId={}", userId, orderMemoryId);
            return orderAgent.handleOrderQuery(orderMemoryId, messageWithProfile);
        }
        RouteDecision decision = intentRouterAiService.route(userMessage);
        if (decision == null || decision.getIntent() == null) {
            log.info("AI chat route fallback QA, userId={}, memoryId={}", userId, qaMemoryId);
            return ragAgent.chat(qaMemoryId, userMessage);
        }
        log.info("AI chat route decision={}, userId={}, orderMemoryId={}, qaMemoryId={}",
                decision.getIntent(), userId, orderMemoryId, qaMemoryId);
        return switch (decision.getIntent()) {
            case CART -> orderAgent.handleOrderQuery(orderMemoryId, messageWithProfile);
            case BOTH -> orderAgent.handleOrderQuery(orderMemoryId, messageWithProfile);
            case QA -> ragAgent.chat(qaMemoryId, userMessage);
        };
    }

    private String buildMessageWithProfile(String userMessage, Long userId) {
        if (!needProfileInjection(userMessage)) {
            return userMessage;
        }
        try {
            String profileSummary = userProfileService.buildProfileSummary(userId);
            String promptContext = "[用户画像摘要] " + profileSummary + "\n"
                    + "[使用约束] 仅在推荐、点单、加购相关回复中参考画像；若画像与当前明确需求冲突，以当前需求为准。\n"
                    + "[用户当前消息] " + userMessage;
            log.info("AI chat profile injected, userId={}, memoryId={}, summary={}",
                    userId, "chat:order:user:" + userId, profileSummary);
            return promptContext;
        } catch (Exception e) {
            log.warn("AI chat profile injection failed, userId={}, error={}", userId, e.getMessage());
            return userMessage;
        }
    }

    private boolean needProfileInjection(String userMessage) {
        if (userMessage == null) {
            return false;
        }
        return userMessage.contains("推荐")
                || userMessage.contains("点")
                || userMessage.contains("下单")
                || userMessage.contains("购物车")
                || userMessage.contains("加")
                || userMessage.contains("来一份");
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
