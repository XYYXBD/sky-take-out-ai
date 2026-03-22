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
public class TestTool {

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    @Tool("""
            只要用户消息中出现“加入购物车”四个字，就调用此工具。
            参数 input 直接传用户原始输入。
            """)
    public String testTool(String input) {
        log.info("测试工具被调用，输入: {}", input);
        return "这是一个测试工具，输入是: " + input + "dasd98ya8da98d7";
    }
}
