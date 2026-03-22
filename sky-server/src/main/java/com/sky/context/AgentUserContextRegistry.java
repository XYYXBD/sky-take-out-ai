package com.sky.context;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护 AI memoryId 与 userId 的稳定映射，避免在异步链路依赖 ThreadLocal。
 */
@Component
public class AgentUserContextRegistry {

    private final Map<String, Long> memoryUserMapping = new ConcurrentHashMap<>();

    public void bind(String memoryId, Long userId) {
        if (memoryId == null || memoryId.isBlank() || userId == null) {
            return;
        }
        memoryUserMapping.put(memoryId, userId);
    }

    public Long resolve(String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            return null;
        }
        return memoryUserMapping.get(memoryId);
    }
}

