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
        contentRetriever = "contentRetriever"
//        tools = {"retrievalTool", "shoppingCartTool"}
)
public interface RagAgent {

    /// 与AI模型进行对话
    @SystemMessage(fromResource = "static/system/ragAgent.txt")
    Flux<String> chat(
            @MemoryId String memoryId,
            @UserMessage String message);
}
