package com.sky.tools;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.aiService.UserProfileInferenceAiService;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.UserProfileContent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 画像工具：负责从订单抽取事实并调用大模型推断 favorite 字段。
 */
@Component
@Slf4j
public class ProfileTool {

    @Autowired
    private UserProfileInferenceAiService userProfileInferenceAiService;

    public OrderFacts buildOrderFacts(Orders order, List<OrderDetail> details) {
        List<String> dishNames = extractDishNames(details);
        String cuisine = inferCuisineByAi(dishNames);
        String flavor = inferFlavor(order, details);
        String budget = inferBudget(order == null ? null : order.getAmount());
        String dishes = dishNames.isEmpty() ? "未提供" : String.join("，", dishNames.subList(0, Math.min(3, dishNames.size())));

        return OrderFacts.builder()
                .recentCuisine(cuisine)
                .recentFlavor(flavor)
                .recentBudgetLevel(budget)
                .recentDishes(dishes)
                .dishNames(dishNames)
                .orderRemark(order == null ? null : order.getRemark())
                .build();
    }

    public FavoriteInferenceResult inferFavoriteFields(UserProfileContent current, OrderFacts facts) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("currentProfile", current == null ? new UserProfileContent() : current);
            payload.put("orderFacts", facts);
            String result = userProfileInferenceAiService.inferFavoriteFields(payload.toJSONString());
            FavoriteInferenceResult parsed = JSON.parseObject(result, FavoriteInferenceResult.class);
            if (parsed == null) {
                return new FavoriteInferenceResult();
            }
            return parsed;
        } catch (Exception e) {
            log.warn("profile ai inference failed, fallback keep-old, error={}", e.getMessage());
            return new FavoriteInferenceResult();
        }
    }

    public String buildSummary(UserProfileContent content) {
        if (content == null) {
            return "暂无用户画像。";
        }
        return "用户偏好摘要："
                + "偏好菜系=" + safe(content.getFavoriteCuisine(), "未稳定")
                + "；偏好口味=" + safe(content.getFavoriteFlavor(), "未稳定")
                + "；预算带=" + safe(content.getBudgetLevel(), "未稳定")
                + "；偏好菜品=" + safe(content.getFavoriteDishes(), "未稳定")
                + "；最近3次菜系=" + safe(content.getRecent3Cuisine(), "未提供")
                + "；最近3次口味=" + safe(content.getRecent3Flavor(), "未提供")
                + "；最近3次预算=" + safe(content.getRecent3BudgetLevel(), "未提供")
                + "；最近3次菜品=" + safe(content.getRecent3Dishes(), "未提供") + "。";
    }

    public String mergeRecent3(String oldValue, String latest) {
        String normalizedLatest = normalize(latest);
        if (normalizedLatest == null) {
            normalizedLatest = "未提供";
        }
        List<String> history = splitByComma(oldValue);
        List<String> merged = new ArrayList<>();
        merged.add(normalizedLatest);
        for (String item : history) {
            if (merged.size() >= 3) {
                break;
            }
            merged.add(item);
        }
        return String.join("，", merged);
    }

    public String mergeFavoriteDishesFallback(String oldValue, List<String> orderDishNames) {
        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(splitByComma(oldValue));
        if (orderDishNames != null) {
            merged.addAll(orderDishNames);
        }
        return merged.stream().limit(6).collect(Collectors.joining("，"));
    }

    private List<String> extractDishNames(List<OrderDetail> details) {
        if (details == null || details.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (OrderDetail detail : details) {
            if (detail == null || detail.getName() == null || detail.getName().isBlank()) {
                continue;
            }
            names.add(detail.getName().trim());
        }
        return new ArrayList<>(names);
    }

    private String inferFlavor(Orders order, List<OrderDetail> details) {
        if (details != null) {
            for (OrderDetail detail : details) {
                String flavor = normalize(detail == null ? null : detail.getDishFlavor());
                if (flavor != null) {
                    return flavor;
                }
            }
        }
        String remark = normalize(order == null ? null : order.getRemark());
        return remark == null ? "未提供" : remark;
    }

    private String inferBudget(BigDecimal amount) {
        if (amount == null) {
            return "未提供";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private String inferCuisineByAi(List<String> dishNames) {
        if (dishNames == null || dishNames.isEmpty()) {
            return "未提供";
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("dishNames", dishNames);
            String result = userProfileInferenceAiService.inferRecentCuisine(payload.toJSONString());
            if (result == null || result.isBlank()) {
                return "未提供";
            }

            try {
                JSONObject jsonObject = JSON.parseObject(result);
                String cuisine = normalize(jsonObject.getString("recentCuisine"));
                return cuisine == null ? "未提供" : cuisine;
            } catch (Exception ignore) {
                String cuisine = normalize(result);
                return cuisine == null ? "未提供" : cuisine;
            }
        } catch (Exception e) {
            log.warn("infer recent cuisine by ai failed, error={}", e.getMessage());
            return "未提供";
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> splitByComma(String value) {
        List<String> items = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return items;
        }
        String[] arr = value.split("[,，]");
        for (String item : arr) {
            String normalized = normalize(item);
            if (normalized != null) {
                items.add(normalized);
            }
        }
        return items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderFacts {
        private String recentCuisine;
        private String recentFlavor;
        private String recentBudgetLevel;
        private String recentDishes;
        private List<String> dishNames;
        private String orderRemark;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FavoriteInferenceResult {
        private String favoriteCuisine;
        private String favoriteFlavor;
        private String budgetLevel;
        private String favoriteDishes;
    }
}


