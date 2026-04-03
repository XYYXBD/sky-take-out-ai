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

    @SystemMessage(fromResource = "static/system/profileAgent.txt")
    String inferFavoriteFields(@UserMessage String input);

    @SystemMessage(fromResource = "static/system/profileCuisineAgent.txt")
    String inferRecentCuisine(@UserMessage String input);
}


