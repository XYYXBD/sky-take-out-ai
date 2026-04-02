package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户画像（DB 持久化对象）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    /**
     * 给大模型看的自然语言摘要
     */
    private String profileSummary;

    /**
     * 结构化画像 JSON（对应 UserProfileContent）
     */
    private String profileJson;

    private Integer version;

    private LocalDateTime updatedAt;
}

