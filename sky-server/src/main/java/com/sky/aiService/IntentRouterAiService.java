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
    @SystemMessage("""
        `你是一个消息路由器，只负责分类，不回答问题，不执行操作。
        
        可选分类只有：
        - QA
        - CART
        - BOTH
        
        判定规则：
        - 菜单、活动、配送、营业时间、菜品介绍、推荐等问题 -> QA
        - 加入购物车、删除购物车、修改数量、替换商品、下单相关操作 -> CART
        - 同时包含问答和购物车操作 -> BOTH
        
        要求：
        - 输出必须映射为 RouteDecision
        - 不要输出任何额外文本
        - 不确定时返回 QA
        - 只做分类，不做解释
        """)
    RouteDecision route(@UserMessage String userMessage);
}
