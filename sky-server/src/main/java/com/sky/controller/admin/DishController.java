package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/dish")
@Api(tags = "菜品相关接口")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;

    /**
     * 新增菜品
     * @param dishDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增菜品")
    @CacheEvict( cacheNames = "dishCache",  key = "#dishDTO.categoryId")
    public Result save(@RequestBody DishDTO dishDTO){
        dishService.saveWithFlavor(dishDTO);
        log.info("新增菜品：{}", dishDTO);
        return Result.success();
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO){
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        log.info("菜品分页查询：{}", dishPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 删除菜品
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("删除菜品")
    @CacheEvict( cacheNames = "dishCache",  allEntries = true)
    public Result delete(@RequestParam List<Long> ids){
        dishService.deleteBatch(ids);
        log.info("已删除菜品{}", ids);
        return Result.success();
    }

    /**
     * 根据分类id查询菜品列表
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据分类id查询菜品列表")
    public  Result<DishVO> selectById(@PathVariable Long id){
        DishVO dishVO = dishService.selectById(id);
        log.info("根据分类id查询菜品列表：{}", id);
        return Result.success(dishVO);
    }

    /**
     * 修改菜品
     * @param dishDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改菜品")
    @CacheEvict( cacheNames = "dishCache",  key = "#dishDTO.categoryId")
    public Result update(@RequestBody DishDTO dishDTO){
        dishService.updateWithFlavor(dishDTO);
        log.info("修改菜品：{}", dishDTO);
        return Result.success();
    }

    /**
     * 启用禁用菜品
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("启用禁用菜品")
    @CacheEvict( cacheNames = "dishCache",  allEntries = true)
    public Result startOrStop(@PathVariable Integer status, @RequestParam Long id){
        dishService.changeStatus(status, id);
        log.info("修改菜品状态为{}，菜品ID列表：{}", status, id);
        return Result.success();
    }


    /**
     * 根据条件查询菜品列表
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据条件查询菜品列表")
    public Result<List<Dish>> selectByCategoryId(@RequestParam Long categoryId){
        List<Dish> dishes = dishService.selectByCategoryId(categoryId);
        log.info("根据条件查询菜品列表：{}", categoryId);
        return Result.success(dishes);
    }
}
