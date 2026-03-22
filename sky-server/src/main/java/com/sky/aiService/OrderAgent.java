package com.sky.aiService;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "ollamaChatModel",
        streamingChatModel = "ollamaStreamingChatModel",
        //chatMemory = "chatMemory"
        chatMemoryProvider = "chatMemoryProvider",
        //contentRetriever = "contentRetriever",
        // tools = "shoppingCartTool"
         tools = "shoppingCartTool"
)
public interface OrderAgent {

    @SystemMessage("""
            你负责添加到购物车的功能。
            """)
    Flux<String> handleOrderQuery(
            @MemoryId String memoryId,
            @UserMessage String message);
}
