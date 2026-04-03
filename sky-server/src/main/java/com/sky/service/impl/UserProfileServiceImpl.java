package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.UserProfile;
import com.sky.entity.UserProfileContent;
import com.sky.exception.UserNotLoginException;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.UserProfileMapper;
import com.sky.service.UserProfileService;
import com.sky.tools.ProfileTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class UserProfileServiceImpl implements UserProfileService {

    private static final String PROFILE_CACHE_KEY_PREFIX = "user:profile:";
    private static final Duration PROFILE_CACHE_TTL = Duration.ofHours(12);

    @Autowired
    private UserProfileMapper userProfileMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ProfileTool profileTool;

    @Override
    public UserProfile getByUserId(Long userId) {
        requireUserId(userId);
        return ensureProfileExists(userId);
    }

    @Override
    public UserProfile getByUserIdWithCache(Long userId) {
        requireUserId(userId);
        String key = buildCacheKey(userId);
        String cachedJson = stringRedisTemplate.opsForValue().get(key);
        if (cachedJson != null && !cachedJson.isBlank()) {
            try {
                UserProfile cached = JSON.parseObject(cachedJson, UserProfile.class);
                if (cached != null) {
                    return cached;
                }
            } catch (Exception e) {
                log.warn("profile cache parse failed, userId={}, key={}, error={}", userId, key, e.getMessage());
            }
        }

        UserProfile dbProfile = ensureProfileExists(userId);
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(dbProfile), PROFILE_CACHE_TTL);
        return dbProfile;
    }

    @Override
    public String buildProfileSummary(Long userId) {
        requireUserId(userId);
        UserProfile profile = getByUserIdWithCache(userId);
        if (profile.getProfileSummary() != null && !profile.getProfileSummary().isBlank()) {
            return profile.getProfileSummary();
        }
        UserProfileContent content = parseContent(profile.getProfileJson());
        return profileTool.buildSummary(content);
    }

    @Override
    public void updateProfileAfterOrder(Long userId, Orders order) {
        requireUserId(userId);
        if (order == null || order.getId() == null) {
            log.warn("skip profile update because order is null, userId={}", userId);
            return;
        }

        List<OrderDetail> details = orderDetailMapper.listByOrderId(order.getId());
        UserProfile existing = getByUserIdWithCache(userId);
        UserProfileContent current = existing == null ? new UserProfileContent() : parseContent(existing.getProfileJson());

        ProfileTool.OrderFacts facts = profileTool.buildOrderFacts(order, details);
        ProfileTool.FavoriteInferenceResult aiFavorite = profileTool.inferFavoriteFields(current, facts);

        UserProfileContent merged = mergeContent(current, facts, aiFavorite);
        String summary = profileTool.buildSummary(merged);
        String profileJson = JSON.toJSONString(merged);

        int nextVersion = existing == null || existing.getVersion() == null ? 1 : existing.getVersion() + 1;
        UserProfile toSave = UserProfile.builder()
                .userId(userId)
                .profileSummary(summary)
                .profileJson(profileJson)
                .version(nextVersion)
                .updatedAt(LocalDateTime.now())
                .build();

        userProfileMapper.upsert(toSave);
        evictProfileCache(userId);

        log.info("user profile updated, userId={}, orderId={}, version={}, summary={}",
                userId, order.getId(), nextVersion, summary);
    }

    @Override
    public void evictProfileCache(Long userId) {
        requireUserId(userId);
        String key = buildCacheKey(userId);
        stringRedisTemplate.delete(key);
    }

    private UserProfileContent mergeContent(UserProfileContent current,
                                            ProfileTool.OrderFacts facts,
                                            ProfileTool.FavoriteInferenceResult aiFavorite) {
        UserProfileContent merged = new UserProfileContent();

        merged.setRecent3Cuisine(profileTool.mergeRecent3(current.getRecent3Cuisine(), facts.getRecentCuisine()));
        merged.setRecent3Flavor(profileTool.mergeRecent3(current.getRecent3Flavor(), facts.getRecentFlavor()));
        merged.setRecent3BudgetLevel(profileTool.mergeRecent3(current.getRecent3BudgetLevel(), facts.getRecentBudgetLevel()));
        merged.setRecent3Dishes(profileTool.mergeRecent3(current.getRecent3Dishes(), facts.getRecentDishes()));

        merged.setFavoriteCuisine(pick(aiFavorite.getFavoriteCuisine(), current.getFavoriteCuisine()));
        merged.setFavoriteFlavor(pick(aiFavorite.getFavoriteFlavor(), current.getFavoriteFlavor()));
        merged.setBudgetLevel(pick(aiFavorite.getBudgetLevel(), current.getBudgetLevel()));

        String aiFavoriteDishes = pick(aiFavorite.getFavoriteDishes(), null);
        if (aiFavoriteDishes != null) {
            merged.setFavoriteDishes(aiFavoriteDishes);
        } else {
            merged.setFavoriteDishes(profileTool.mergeFavoriteDishesFallback(current.getFavoriteDishes(), facts.getDishNames()));
        }

        return merged;
    }

    private UserProfileContent parseContent(String profileJson) {
        if (profileJson == null || profileJson.isBlank()) {
            return new UserProfileContent();
        }
        try {
            UserProfileContent content = JSON.parseObject(profileJson, UserProfileContent.class);
            return content == null ? new UserProfileContent() : content;
        } catch (Exception e) {
            log.warn("profile_json parse failed, fallback empty content, error={}", e.getMessage());
            return new UserProfileContent();
        }
    }

    private String buildCacheKey(Long userId) {
        return PROFILE_CACHE_KEY_PREFIX + userId;
    }

    private UserProfile ensureProfileExists(Long userId) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile != null) {
            return profile;
        }

        UserProfileContent emptyContent = new UserProfileContent();
        String summary = profileTool.buildSummary(emptyContent);
        UserProfile initProfile = UserProfile.builder()
                .userId(userId)
                .profileSummary(summary)
                .profileJson(JSON.toJSONString(emptyContent))
                .version(1)
                .updatedAt(LocalDateTime.now())
                .build();
        userProfileMapper.upsert(initProfile);

        log.info("init user profile because not found, userId={}", userId);
        return initProfile;
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new UserNotLoginException("用户未登录，无法读取或更新画像");
        }
    }

    private String pick(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}

