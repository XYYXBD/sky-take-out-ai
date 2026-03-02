package com.sky.tools;

import com.sky.constant.StatusConstant;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.service.ShoppingCartService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 购物车工具类
 * 提供AI助手操作购物车的能力：添加菜品/套餐、移除商品、查看购物车、批量添加
 */
@Component
@Slf4j
public class ShoppingCartTool {

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加菜品或套餐到购物车
     * 先根据名称模糊查询菜品，如果没有找到再查询套餐
     *
     * @param dishName 菜品或套餐名称（支持模糊匹配）
     * @return 操作结果提示信息
     */
    @Tool("当用户明确表示要点某道菜、加入购物车时调用此工具。参数dishName是菜品或套餐名称，系统会自动查找对应的ID并加入购物车")
    public String addDishToCart(String dishName) {
        try {
            log.info("AI助手尝试添加商品到购物车: {}", dishName);

            // 1. 先查询菜品（只查询起售中的）
            Dish queryDish = Dish.builder()
                    .name(dishName)
                    .status(StatusConstant.ENABLE)
                    .build();
            List<Dish> dishes = dishMapper.selectByCategoryId(queryDish);

            if (dishes != null && !dishes.isEmpty()) {
                // 找到菜品，加入购物车
                Dish dish = dishes.get(0);
                log.info("找到菜品: id={}, name={}, price={}", dish.getId(), dish.getName(), dish.getPrice());

                ShoppingCartDTO shoppingCartDTO = new ShoppingCartDTO();
                shoppingCartDTO.setDishId(dish.getId());
                shoppingCartService.add(shoppingCartDTO);

                // 获取购物车统计信息
                return buildCartSummary(dish.getName(), "菜品");
            }

            // 2. 菜品没找到，查询套餐
            Setmeal querySetmeal = Setmeal.builder()
                    .name(dishName)
                    .status(StatusConstant.ENABLE)
                    .build();
            List<Setmeal> setmeals = setmealMapper.list(querySetmeal);

            if (setmeals != null && !setmeals.isEmpty()) {
                // 找到套餐，加入购物车
                Setmeal setmeal = setmeals.get(0);
                log.info("找到套餐: id={}, name={}, price={}", setmeal.getId(), setmeal.getName(), setmeal.getPrice());

                ShoppingCartDTO shoppingCartDTO = new ShoppingCartDTO();
                shoppingCartDTO.setSetmealId(setmeal.getId());
                shoppingCartService.add(shoppingCartDTO);

                // 获取购物车统计信息
                return buildCartSummary(setmeal.getName(), "套餐");
            }

            // 3. 都没找到
            log.warn("未找到菜品或套餐: {}", dishName);
            return "抱歉，没有找到名为「" + dishName + "」的菜品或套餐，请确认名称是否正确～";

        } catch (Exception e) {
            log.error("添加商品到购物车失败: dishName={}, error={}", dishName, e.getMessage(), e);
            return "添加失败，系统繁忙，请稍后再试～";
        }
    }

    /**
     * 从购物车移除菜品或套餐
     * 先根据名称查询菜品/套餐，然后从购物车中减少数量或删除
     *
     * @param dishName 菜品或套餐名称
     * @return 操作结果提示信息
     */
    @Tool("当用户明确表示要移除某道菜、删除、取消时调用此工具。参数dishName是菜品或套餐名称，系统会自动查找对应的ID并从购物车移除")
    public String removeDishFromCart(String dishName) {
        try {
            log.info("AI助手尝试从购物车移除商品: {}", dishName);

            // 1. 先查询菜品
            Dish queryDish = Dish.builder()
                    .name(dishName)
                    .build();
            List<Dish> dishes = dishMapper.selectByCategoryId(queryDish);

            String itemName = dishName;

            if (dishes != null && !dishes.isEmpty()) {
                // 找到菜品
                Dish dish = dishes.get(0);
                itemName = dish.getName();

                ShoppingCartDTO shoppingCartDTO = new ShoppingCartDTO();
                shoppingCartDTO.setDishId(dish.getId());
                shoppingCartService.sub(shoppingCartDTO);

                log.info("成功从购物车移除菜品: {}", itemName);
                return buildRemoveResult(itemName);
            }

            // 2. 菜品没找到，查询套餐
            Setmeal querySetmeal = Setmeal.builder()
                    .name(dishName)
                    .build();
            List<Setmeal> setmeals = setmealMapper.list(querySetmeal);

            if (setmeals != null && !setmeals.isEmpty()) {
                // 找到套餐
                Setmeal setmeal = setmeals.get(0);
                itemName = setmeal.getName();

                ShoppingCartDTO shoppingCartDTO = new ShoppingCartDTO();
                shoppingCartDTO.setSetmealId(setmeal.getId());
                shoppingCartService.sub(shoppingCartDTO);

                log.info("成功从购物车移除套餐: {}", itemName);
                return buildRemoveResult(itemName);
            }

            // 都没找到
            log.warn("未找到菜品或套餐: {}", dishName);
            return "抱歉，没有找到名为「" + dishName + "」的菜品或套餐";

        } catch (RuntimeException e) {
            log.error("从购物车移除商品失败: dishName={}, error={}", dishName, e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("购物车中无此商品")) {
                return "购物车中没有【" + dishName + "】哦～";
            }
            return "移除失败，系统繁忙，请稍后再试～";
        } catch (Exception e) {
            log.error("从购物车移除商品失败: dishName={}, error={}", dishName, e.getMessage(), e);
            return "移除失败，系统繁忙，请稍后再试～";
        }
    }

    /**
     * 批量添加菜品到购物车
     * 用于AI推荐多个菜品后，用户确认添加的场景
     * 注意：这个方法接收菜品名称列表，而不是DTO列表，因为Tool只能接收基本类型参数
     *
     * @param dishNames 菜品或套餐名称列表，用逗号分隔，例如："宫保鸡丁,麻婆豆腐,红烧肉"
     * @return 操作结果提示信息
     */
    @Tool("当用户确认要添加AI推荐的多个菜品时调用此工具。参数dishNames是菜品名称列表，用逗号分隔")
    @Transactional(rollbackFor = Exception.class)
    public String addDishesToCart(String dishNames) {
        try {
            log.info("AI助手尝试批量添加商品到购物车: {}", dishNames);

            if (dishNames == null || dishNames.trim().isEmpty()) {
                return "请提供要添加的菜品名称～";
            }

            // 分割菜品名称
            String[] nameArray = dishNames.split("[,，、]");
            List<String> successList = new ArrayList<>();
            List<String> failList = new ArrayList<>();

            for (String name : nameArray) {
                String dishName = name.trim();
                if (dishName.isEmpty()) {
                    continue;
                }

                try {
                    // 尝试添加单个菜品
                    boolean added = addSingleDish(dishName);
                    if (added) {
                        successList.add(dishName);
                    } else {
                        failList.add(dishName);
                    }
                } catch (Exception e) {
                    log.error("添加菜品失败: {}, error={}", dishName, e.getMessage());
                    failList.add(dishName);
                }
            }

            // 构建返回结果
            StringBuilder result = new StringBuilder();

            if (!successList.isEmpty()) {
                result.append("成功添加 ").append(successList.size()).append(" 道菜：\n");
                for (String name : successList) {
                    result.append("✅ ").append(name).append("\n");
                }
            }

            if (!failList.isEmpty()) {
                result.append("\n未找到以下菜品：\n");
                for (String name : failList) {
                    result.append("❌ ").append(name).append("\n");
                }
            }

            // 添加购物车统计
            List<ShoppingCart> cartItems = shoppingCartService.list();
            if (!cartItems.isEmpty()) {
                int totalItems = cartItems.stream().mapToInt(ShoppingCart::getNumber).sum();
                BigDecimal totalAmount = cartItems.stream()
                        .map(item -> item.getAmount().multiply(new BigDecimal(item.getNumber())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                result.append("\n————————————\n");
                result.append(String.format("购物车共 %d 件商品，总计 ¥%.2f", totalItems, totalAmount.doubleValue()));
            }

            log.info("批量添加完成: 成功{}个, 失败{}个", successList.size(), failList.size());
            return result.toString();

        } catch (Exception e) {
            log.error("批量添加商品到购物车失败: dishNames={}, error={}", dishNames, e.getMessage(), e);
            return "批量添加失败，系统繁忙，请稍后再试～";
        }
    }

    /**
     * 查看购物车内容
     * 返回购物车中所有商品的详细信息，包括名称、数量、单价、小计和总计
     *
     * @return 购物车详情信息
     */
    @Tool("当用户询问购物车内容、已点菜品、当前订单时调用此工具，返回购物车中所有商品的详细信息")
    public String getCartItems() {
        try {
            log.info("AI助手查看购物车");

            List<ShoppingCart> cartItems = shoppingCartService.list();

            if (cartItems == null || cartItems.isEmpty()) {
                log.info("购物车为空");
                return "购物车还是空的，快来点些好吃的吧！🍜";
            }

            StringBuilder result = new StringBuilder("您的购物车 🛒\n\n");
            BigDecimal totalAmount = BigDecimal.ZERO;

            for (int i = 0; i < cartItems.size(); i++) {
                ShoppingCart item = cartItems.get(i);
                BigDecimal itemTotal = item.getAmount().multiply(new BigDecimal(item.getNumber()));
                totalAmount = totalAmount.add(itemTotal);

                result.append(i + 1).append(". ")
                      .append(item.getName())
                      .append(" x").append(item.getNumber())
                      .append(" - ¥").append(String.format("%.2f", itemTotal.doubleValue()));

                // 如果有口味信息，显示出来
                if (item.getDishFlavor() != null && !item.getDishFlavor().isEmpty()) {
                    result.append(" (").append(item.getDishFlavor()).append(")");
                }

                result.append("\n");
            }

            result.append("————————————\n");
            result.append("总计：¥").append(String.format("%.2f", totalAmount.doubleValue()));

            log.info("购物车查询成功，共{}件商品，总金额¥{}",
                    cartItems.stream().mapToInt(ShoppingCart::getNumber).sum(),
                    totalAmount.doubleValue());

            return result.toString();

        } catch (Exception e) {
            log.error("查看购物车失败: error={}", e.getMessage(), e);
            return "查看购物车失败，系统繁忙，请稍后再试～";
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 添加单个菜品到购物车（内部方法）
     *
     * @param dishName 菜品或套餐名称
     * @return 是否添加成功
     */
    private boolean addSingleDish(String dishName) {
        // 1. 先查询菜品
        Dish queryDish = Dish.builder()
                .name(dishName)
                .status(StatusConstant.ENABLE)
                .build();
        List<Dish> dishes = dishMapper.selectByCategoryId(queryDish);

        if (dishes != null && !dishes.isEmpty()) {
            Dish dish = dishes.get(0);
            ShoppingCartDTO shoppingCartDTO = new ShoppingCartDTO();
            shoppingCartDTO.setDishId(dish.getId());
            shoppingCartService.add(shoppingCartDTO);
            return true;
        }

        // 2. 查询套餐
        Setmeal querySetmeal = Setmeal.builder()
                .name(dishName)
                .status(StatusConstant.ENABLE)
                .build();
        List<Setmeal> setmeals = setmealMapper.list(querySetmeal);

        if (setmeals != null && !setmeals.isEmpty()) {
            Setmeal setmeal = setmeals.get(0);
            ShoppingCartDTO shoppingCartDTO = new ShoppingCartDTO();
            shoppingCartDTO.setSetmealId(setmeal.getId());
            shoppingCartService.add(shoppingCartDTO);
            return true;
        }

        return false;
    }

    /**
     * 构建购物车摘要信息
     *
     * @param itemName 商品名称
     * @param itemType 商品类型（菜品/套餐）
     * @return 摘要信息
     */
    private String buildCartSummary(String itemName, String itemType) {
        List<ShoppingCart> cartItems = shoppingCartService.list();
        int totalItems = cartItems.stream().mapToInt(ShoppingCart::getNumber).sum();
        BigDecimal totalAmount = cartItems.stream()
                .map(item -> item.getAmount().multiply(new BigDecimal(item.getNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return String.format("已将%s【%s】加入购物车 ✅\n您的购物车现在有 %d 件商品，总计 ¥%.2f",
                itemType, itemName, totalItems, totalAmount.doubleValue());
    }

    /**
     * 构建移除结果信息
     *
     * @param itemName 商品名称
     * @return 结果信息
     */
    private String buildRemoveResult(String itemName) {
        List<ShoppingCart> cartItems = shoppingCartService.list();

        if (cartItems.isEmpty()) {
            return "已将【" + itemName + "】从购物车移除 ✅\n购物车已清空";
        }

        StringBuilder result = new StringBuilder("已将【" + itemName + "】从购物车移除 ✅\n\n当前购物车：\n");
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (ShoppingCart item : cartItems) {
            BigDecimal itemTotal = item.getAmount().multiply(new BigDecimal(item.getNumber()));
            totalAmount = totalAmount.add(itemTotal);

            result.append("- ").append(item.getName())
                  .append(" x").append(item.getNumber())
                  .append(" ¥").append(String.format("%.2f", itemTotal.doubleValue()))
                  .append("\n");
        }

        result.append("————————————\n");
        result.append("总计：¥").append(String.format("%.2f", totalAmount.doubleValue()));

        return result.toString();
    }
}
