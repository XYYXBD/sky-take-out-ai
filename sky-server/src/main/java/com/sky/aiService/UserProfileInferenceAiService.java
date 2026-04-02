package com.sky.aiService;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "ollamaChatModel"
)
public interface UserProfileInferenceAiService {

    @SystemMessage("""
            你是用户画像更新助手。请基于输入的旧画像与最新订单事实，输出 JSON：
            {
              "favoriteCuisine": "...",
              "favoriteFlavor": "...",
              "budgetLevel": "...",
              "favoriteDishes": "..."
            }
            约束：
            1) 只输出 JSON，不要输出其他解释。
            2) 如信息不足，保持旧值，不要编造。
            3) favoriteDishes 用逗号分隔，最多 6 个。
            """)
    String inferFavoriteFields(@UserMessage String input);
}

