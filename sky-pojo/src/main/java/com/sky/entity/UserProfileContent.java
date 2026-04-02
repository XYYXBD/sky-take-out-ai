package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户画像结构化内容（存储于 user_profile.profile_json）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileContent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String favoriteCuisine;
    private String recent3Cuisine;

    private String favoriteFlavor;
    private String recent3Flavor;

    private String budgetLevel;
    private String recent3BudgetLevel;

    private String favoriteDishes;
    private String recent3Dishes;
}

