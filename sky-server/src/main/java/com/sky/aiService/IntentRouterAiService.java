package com.sky.aiService;

import com.sky.entity.RouteDecision;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import org.springframework.beans.factory.annotation.Autowired;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "routeModel"
//        streamingChatModel = "ollamaStreamingChatModel",
        //chatMemory = "chatMemory"
//        chatMemoryProvider = "chatMemoryProvider",
//        contentRetriever = "contentRetriever"
//        tools = {"retrievalTool", "shoppingCartTool"}
)
public interface IntentRouterAiService {
    @SystemMessage(fromResource = "static/system/intentRouterAiService.txt")
    RouteDecision route(@UserMessage String userMessage);
}
