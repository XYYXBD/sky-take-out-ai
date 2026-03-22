package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.UserNotLoginException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ShoppinCartServiceImp implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加到购物车
     * @param shoppingCartDTO
     */
    @Override
    @Transactional
    public void add(ShoppingCartDTO shoppingCartDTO) {
        addForUser(shoppingCartDTO, requireCurrentUserId());
    }

    @Override
    @Transactional
    public void addForUser(ShoppingCartDTO shoppingCartDTO, Long userId) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        shoppingCart.setUserId(requireUserId(userId));
        //判断商品是否存在
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);
        if(shoppingCarts != null && shoppingCarts.size() >0){
            //存在则数量加一
            ShoppingCart existingCart = shoppingCarts.get(0);
//            log.debug("购物车已存在该商品:{}",existingCart);
            existingCart.setNumber(existingCart.getNumber() + 1);
            shoppingCartMapper.updateNumber(existingCart);
            return;
        }else {
            //不存在则添加一条记录
            //判断是菜品还是套餐
            Long dishId = shoppingCartDTO.getDishId();
            Long setmealId = shoppingCartDTO.getSetmealId();
            if(dishId != null){
                //是菜品
                Dish dish = dishMapper.selectById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
            }else{
                //是套餐
                Setmeal setmeal = setmealMapper.selectById(setmealId);
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());

            }
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 查看购物车列表
     * @return
     */
    @Override
    public List<ShoppingCart> list() {
        return listForUser(requireCurrentUserId());
    }

    @Override
    public List<ShoppingCart> listForUser(Long userId) {
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(requireUserId(userId))
                .build();
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);
        return shoppingCarts;
    }

    /**
     * 清空购物车
     */
    @Override
    public void clean() {
        cleanForUser(requireCurrentUserId());
    }

    @Override
    public void cleanForUser(Long userId) {
        shoppingCartMapper.cleanByUserId(requireUserId(userId));
    }

    /**
     * 从购物车中减少商品
     */
    @Override
    @Transactional
    public void sub(ShoppingCartDTO shoppingCartDTO) {
        subForUser(shoppingCartDTO, requireCurrentUserId());
    }

    @Override
    @Transactional
    public void subForUser(ShoppingCartDTO shoppingCartDTO, Long userId) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        shoppingCart.setUserId(requireUserId(userId));
        //判断商品是否存在
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);
        if(shoppingCarts != null && shoppingCarts.size() >0){
            //存在则数量减一
            ShoppingCart existingCart = shoppingCarts.get(0);
            Integer currentNumber = existingCart.getNumber();
            if(currentNumber > 1){
                existingCart.setNumber(currentNumber - 1);
                shoppingCartMapper.updateNumber(existingCart);
            }else {
                shoppingCartMapper.deleteByUserIdAndDishIdAndSetmealId(existingCart);
            }
        }else {
            throw new RuntimeException("购物车中无此商品，无法减少");
        }
    }

    private Long requireCurrentUserId() {
        return requireUserId(BaseContext.getCurrentId());
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new UserNotLoginException("用户上下文缺失，请重新登录后再试");
        }
        return userId;
    }
}
