package com.sky.service;

import com.sky.entity.Orders;
import com.sky.entity.UserProfile;

public interface UserProfileService {

    UserProfile getByUserId(Long userId);

    UserProfile getByUserIdWithCache(Long userId);

    String buildProfileSummary(Long userId);

    void updateProfileAfterOrder(Long userId, Orders order);

    void evictProfileCache(Long userId);
}

