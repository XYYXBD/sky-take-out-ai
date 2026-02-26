package com.sky.repository;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
public class RedisChatMemoryStore implements ChatMemoryStore {
    //注入RedisTemplate等操作Redis的工具类
    @Autowired
    private StringRedisTemplate redisTemplate;


    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        //获取会话
        String json = redisTemplate.opsForValue().get(memoryId);
        //json转List<ChatMessage>
        List<ChatMessage> list = ChatMessageDeserializer.messagesFromJson(json);
        return list;
    }

    /**
     * 更新回话消息
     * @param memoryId
     * @param list
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        //String转json
        String json = ChatMessageSerializer.messagesToJson(list);
        //json存储到Redis中
        redisTemplate.opsForValue().set(memoryId.toString(), json, Duration.ofDays(1));

    }

    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(memoryId.toString());
    }
}
