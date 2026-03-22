package com.sky.tools;

import com.sky.constant.StatusConstant;
import com.sky.context.AgentUserContextRegistry;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.UserNotLoginException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.service.ShoppingCartService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private AgentUserContextRegistry agentUserContextRegistry;

    /**
     * 添加菜品或套餐到购物车
     * 先根据名称模糊查询菜品，如果没有找到再查询套餐
     *
     * @param dishName 菜品或套餐名称（支持模糊匹配）
     * @return 操作结果提示信息
     */
    @Tool("工具A：按菜名查询是否存在。一次只查一个菜名。若存在返回购物车基础DTO信息；若不存在明确说明未查询到")
    public String queryDishByName(String dishName) {
        try {
            log.info("按菜名查询商品: {}", dishName);

            if (dishName == null || dishName.trim().isEmpty()) {
                return "code=INVALID_PARAM; message=请先提供菜名。";
            }

            Dish dish = findEnabledDishByName(dishName);
            if (dish != null) {
                List<DishFlavor> flavorList = dishFlavorMapper.selectByDishId(dish.getId());
                List<String> options = parseFlavorOptions(flavorList);
                boolean needFlavor = !options.isEmpty();

                if (needFlavor) {
                    return "code=DISH_FOUND_FLAVOR_REQUIRED; message=查询到菜品【" + dish.getName()
                            + "】，该菜需要先选择口味。; data={dishId=" + dish.getId()
                            + ", dishName=" + dish.getName() + ", flavors=" + options + "}";
                }
                return "code=DISH_FOUND_NO_FLAVOR; message=查询到菜品【" + dish.getName()
                        + "】，该菜无需补充口味。; data={dishId=" + dish.getId()
                        + ", dishName=" + dish.getName() + "}";
            }

            Setmeal querySetmeal = Setmeal.builder()
                    .name(dishName)
                    .status(StatusConstant.ENABLE)
                    .build();
            List<Setmeal> setmeals = setmealMapper.list(querySetmeal);
            if (setmeals != null && !setmeals.isEmpty()) {
                Setmeal setmeal = pickExactSetmeal(dishName, setmeals);
                return "code=SETMEAL_FOUND; message=查询到套餐【" + setmeal.getName()
                        + "】，可直接加入购物车。; data={setmealId=" + setmeal.getId()
                        + ", setmealName=" + setmeal.getName() + "}";
            }

            return "code=DISH_NOT_FOUND; message=未查询到菜品或套餐【" + dishName + "】。";

        } catch (Exception e) {
            log.error("查询商品失败: dishName={}, error={}", dishName, e.getMessage(), e);
            return "code=SYSTEM_ERROR; message=查询商品失败，请稍后重试。";
        }
    }

    @Tool("工具B：查询菜品可选口味。仅在该菜需要口味时调用。一次只查一个菜名")
    public String queryDishFlavors(String dishName) {
        try {
            if (dishName == null || dishName.trim().isEmpty()) {
                return "code=INVALID_PARAM; message=请输入需要查询口味的菜名。";
            }

            Dish dish = findEnabledDishByName(dishName);
            if (dish == null) {
                return "code=DISH_NOT_FOUND; message=未查询到菜品【" + dishName + "】。";
            }

            List<DishFlavor> flavorList = dishFlavorMapper.selectByDishId(dish.getId());
            List<String> options = parseFlavorOptions(flavorList);
            if (options.isEmpty()) {
                return "code=NO_FLAVOR_REQUIRED; message=菜品【" + dish.getName() + "】无需选择口味。";
            }

            return "code=FLAVOR_OPTIONS; message=菜品【" + dish.getName() + "】可选口味："
                    + String.join(" / ", options) + "。请从中选择 1 个口味。; data={dishId="
                    + dish.getId() + ", dishName=" + dish.getName() + ", flavors=" + options + "}";
        } catch (Exception e) {
            log.error("查询口味失败: dishName={}, error={}", dishName, e.getMessage(), e);
            return "code=SYSTEM_ERROR; message=查询口味失败，请稍后重试。";
        }
    }

    @Tool("工具C：加入购物车。每次只能加入一个。输入菜名和可选口味，系统只执行一次加购")
    public String addOneToCart(@ToolMemoryId String memoryId,
                               @P("菜名") String dishName,
                               @P(value = "口味（选填）", required = false) String dishFlavor) {
        try {
            Long userId = requireUserId(memoryId);
            String normalizedFlavor = normalizeOptionalFlavor(dishFlavor);
            log.info("addOneToCart start, userId={}, memoryId={}, dishName={}, dishFlavor={}",
                    userId, memoryId, dishName, normalizedFlavor);

            if (dishName == null || dishName.trim().isEmpty()) {
                return "code=INVALID_PARAM; message=请先提供菜名。";
            }

            Dish dish = findEnabledDishByName(dishName);
            if (dish != null) {
                List<DishFlavor> flavorList = dishFlavorMapper.selectByDishId(dish.getId());
                List<String> options = parseFlavorOptions(flavorList);
                boolean needFlavor = !options.isEmpty();

                if (!needFlavor) {
                    ShoppingCartDTO shoppingCartDTO = new ShoppingCartDTO();
                    shoppingCartDTO.setDishId(dish.getId());
                    shoppingCartDTO.setDishFlavor(null);
                    shoppingCartService.addForUser(shoppingCartDTO, userId);
                    log.info("addOneToCart no-flavor-needed success, userId={}, memoryId={}, dishId={}, dishFlavor=null",
                            userId, memoryId, dish.getId());
                    return "code=NO_FLAVOR_NEEDED; message=菜品【" + dish.getName() + "】无需选择口味，已加入 x1。";
                }

                if (normalizedFlavor == null) {
                    return "code=FLAVOR_REQUIRED; message=菜品【" + dish.getName()
                            + "】需要选择口味。可选口味：" + String.join(" / ", options) + "。";
                }

                if (!isFlavorValid(normalizedFlavor, options)) {
                    return "code=FLAVOR_INVALID; message=菜品【" + dish.getName() + "】可选口味为："
                            + String.join(" / ", options) + "。你输入的【" + normalizedFlavor
                            + "】不在可选范围内，请重新选择 1 个口味。";
                }

                ShoppingCartDTO shoppingCartDTO = new ShoppingCartDTO();
                shoppingCartDTO.setDishId(dish.getId());
                shoppingCartDTO.setDishFlavor(normalizedFlavor);
                shoppingCartService.addForUser(shoppingCartDTO, userId);
                log.info("addOneToCart add-success, userId={}, memoryId={}, dishId={}, dishFlavor={}",
                        userId, memoryId, dish.getId(), normalizedFlavor);

                return "code=ADD_SUCCESS; message=已加入【" + dish.getName() + "】x1。";
            }

            Setmeal querySetmeal = Setmeal.builder()
                    .name(dishName)
                    .status(StatusConstant.ENABLE)
                    .build();
            List<Setmeal> setmeals = setmealMapper.list(querySetmeal);
            if (setmeals != null && !setmeals.isEmpty()) {
                Setmeal setmeal = pickExactSetmeal(dishName, setmeals);
                ShoppingCartDTO shoppingCartDTO = new ShoppingCartDTO();
                shoppingCartDTO.setSetmealId(setmeal.getId());
                shoppingCartService.addForUser(shoppingCartDTO, userId);
                log.info("addOneToCart setmeal-success, userId={}, memoryId={}, setmealId={}, dishFlavor=null",
                        userId, memoryId, setmeal.getId());
                return "code=ADD_SUCCESS; message=已加入【" + setmeal.getName() + "】x1。";
            }

            return "code=DISH_NOT_FOUND; message=未查询到菜品或套餐【" + dishName + "】。";
        } catch (UserNotLoginException e) {
            log.warn("addOneToCart missing user context, memoryId={}, dishName={}, dishFlavor={}, error={}",
                    memoryId, dishName, dishFlavor, e.getMessage());
            return "code=USER_CONTEXT_MISSING; message=" + e.getMessage() + "。";
        } catch (Exception e) {
            log.error("单次加购失败: memoryId={}, dishName={}, dishFlavor={}, error={}",
                    memoryId, dishName, dishFlavor, e.getMessage(), e);
            return "code=SYSTEM_ERROR; message=加购失败，请稍后重试。";
        }
    }

    /**
     * 从购物车移除菜品或套餐
     * 先根据名称查询菜品/套餐，然后从购物车中减少数量或删除
     *
     * @param dishName 菜品或套餐名称
     * @return 操作结果提示信息
     */
    // 旧能力保留为普通方法，不再作为订单Agent工具暴露
    public String removeDishFromCart(String dishName) {
        try {
            log.info("AI助手尝试从购物车移除商品: {}", dishName);

            Dish queryDish = Dish.builder()
                    .name(dishName)
                    .build();
            List<Dish> dishes = dishMapper.selectByName(queryDish);

            String itemName = dishName;

            if (dishes != null && !dishes.isEmpty()) {
                Dish dish = pickExactDish(dishName, dishes);
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
    // 旧能力保留为普通方法，不再作为订单Agent工具暴露
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
    public String getCartItems(@ToolMemoryId String memoryId) {
        try {
            Long userId = requireUserId(memoryId);
            log.info("AI助手查看购物车, userId={}, memoryId={}", userId, memoryId);

            List<ShoppingCart> cartItems = shoppingCartService.listForUser(userId);

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

        } catch (UserNotLoginException e) {
            log.warn("查看购物车失败，用户上下文缺失, memoryId={}, error={}", memoryId, e.getMessage());
            return "code=USER_CONTEXT_MISSING; message=" + e.getMessage() + "。";
        } catch (Exception e) {
            log.error("查看购物车失败: error={}", e.getMessage(), e);
            return "查看购物车失败，系统繁忙，请稍后再试～";
        }
    }

    @Tool("工具E：清空购物车。当用户明确要求清空，或质疑购物车内容有误时调用")
    public String clearCartItems(@ToolMemoryId String memoryId) {
        try {
            Long userId = requireUserId(memoryId);
            log.info("清空购物车, userId={}, memoryId={}", userId, memoryId);
            shoppingCartService.cleanForUser(userId);
            return "已为你清空购物车。为了避免再次出错，请你手动点单。";
        } catch (UserNotLoginException e) {
            log.warn("清空购物车失败，用户上下文缺失, memoryId={}, error={}", memoryId, e.getMessage());
            return "code=USER_CONTEXT_MISSING; message=" + e.getMessage() + "。";
        } catch (Exception e) {
            log.error("清空购物车失败: {}", e.getMessage(), e);
            return "系统繁忙，请稍后再试～";
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
        Dish queryDish = Dish.builder()
                .name(dishName)
                .status(StatusConstant.ENABLE)
                .build();
        List<Dish> dishes = dishMapper.selectByName(queryDish);

        if (dishes != null && !dishes.isEmpty()) {
            Dish dish = pickExactDish(dishName, dishes);
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

    private Dish pickExactDish(String name, List<Dish> dishes) {
        for (Dish dish : dishes) {
            if (dish.getName() != null && dish.getName().equals(name)) {
                return dish;
            }
        }
        return dishes.get(0);
    }

    private Setmeal pickExactSetmeal(String name, List<Setmeal> setmeals) {
        for (Setmeal setmeal : setmeals) {
            if (setmeal.getName() != null && setmeal.getName().equals(name)) {
                return setmeal;
            }
        }
        return setmeals.get(0);
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

    private Dish findEnabledDishByName(String dishName) {
        Dish queryDish = Dish.builder()
                .name(dishName)
                .status(StatusConstant.ENABLE)
                .build();
        List<Dish> dishes = dishMapper.selectByName(queryDish);
        if (dishes == null || dishes.isEmpty()) {
            return null;
        }
        return pickExactDish(dishName, dishes);
    }

    private List<String> parseFlavorOptions(List<DishFlavor> flavorList) {
        if (flavorList == null || flavorList.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> options = new LinkedHashSet<>();
        Pattern valuePattern = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");

        for (DishFlavor flavor : flavorList) {
            if (flavor == null || flavor.getValue() == null) {
                continue;
            }
            String raw = flavor.getValue();
            Matcher matcher = valuePattern.matcher(raw);
            boolean matched = false;
            while (matcher.find()) {
                matched = true;
                addFlavorTokens(options, matcher.group(1));
            }
            if (!matched) {
                addFlavorTokens(options, raw);
            }
        }

        return new ArrayList<>(options);
    }

    private void addFlavorTokens(Set<String> options, String text) {
        if (text == null) {
            return;
        }
        String cleaned = text
                .replace("[", "")
                .replace("]", "")
                .replace("{", "")
                .replace("}", "")
                .replace("\"", "");

        for (String token : cleaned.split("[,，/、|]")) {
            String t = token == null ? "" : token.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.contains(":")) {
                continue;
            }
            options.add(t);
        }
    }

    private boolean isFlavorValid(String inputFlavor, List<String> validOptions) {
        if (inputFlavor == null || validOptions == null || validOptions.isEmpty()) {
            return false;
        }
        String input = normalizeText(inputFlavor);
        for (String option : validOptions) {
            if (input.equals(normalizeText(option))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim();
    }

    private String normalizeOptionalFlavor(String dishFlavor) {
        if (dishFlavor == null) {
            return null;
        }
        String trimmed = dishFlavor.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long requireUserId(String memoryId) {
        Long userId = agentUserContextRegistry.resolve(memoryId);
        if (userId == null) {
            throw new UserNotLoginException("用户上下文缺失，无法操作购物车");
        }
        return userId;
    }
}
