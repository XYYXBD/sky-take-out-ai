package com.sky.mapper;

import com.sky.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserProfileMapper {

    @Select("select * from user_profile where user_id = #{userId}")
    UserProfile selectByUserId(Long userId);

    int upsert(UserProfile userProfile);
}

