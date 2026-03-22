package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {
    /**
     * 添加到购物车
     *
     * @param shoppingCartDTO
     */
    void add(ShoppingCartDTO shoppingCartDTO);

    /**
     * 按指定用户添加到购物车（用于异步链路避免 ThreadLocal 丢失）
     */
    void addForUser(ShoppingCartDTO shoppingCartDTO, Long userId);

    /**
     * 查看购物车列表
     * @return
     */
    List<ShoppingCart> list();

    /**
     * 查询指定用户购物车
     */
    List<ShoppingCart> listForUser(Long userId);

    /**
     * 清空购物车
     */
    void clean();

    /**
     * 清空指定用户购物车
     */
    void cleanForUser(Long userId);

    /**
     * 减少购物车数量
     * @param shoppingCartDTO
     */
    void sub(ShoppingCartDTO shoppingCartDTO);

    /**
     * 按指定用户减少购物车数量
     */
    void subForUser(ShoppingCartDTO shoppingCartDTO, Long userId);
}
