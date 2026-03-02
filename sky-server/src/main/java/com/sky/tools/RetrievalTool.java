package com.sky.tools;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜品检索工具
 * 提供菜品信息查询和推荐功能
 */
@Component
@Slf4j
public class RetrievalTool {

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 从知识库检索菜品信息并结合数据库菜单进行推荐
     *
     * 工作流程：
     * 1. 从数据库查询所有在售的菜品和套餐名称
     * 2. 从知识库检索相关的菜品描述、口味、食材等信息
     * 3. 将菜单列表和知识库信息组合返回给大模型
     * 4. 大模型根据用户偏好从菜单中筛选推荐
     *
     * @param question 用户的查询或偏好描述，如"推荐几个川菜"、"有什么不辣的菜"
     * @return 包含菜单列表和知识库信息的综合结果
     */
    @Tool("当用户询问菜单、菜品推荐、食材、口味、餐厅信息等问题时调用此工具。返回可用菜单和相关知识，供你进行推荐分析")
    public String retrieveFromKnowledgeBase(String question) {
        try {
            log.info("AI助手检索菜品信息: {}", question);

            StringBuilder result = new StringBuilder();

            // 1. 查询所有在售的菜品
            Dish dishQuery = Dish.builder()
                    .status(StatusConstant.ENABLE)
                    .build();
            List<Dish> dishes = dishMapper.selectByCategoryId(dishQuery);

            // 2. 查询所有在售的套餐
            Setmeal setmealQuery = Setmeal.builder()
                    .status(StatusConstant.ENABLE)
                    .build();
            List<Setmeal> setmeals = setmealMapper.list(setmealQuery);

            // 3. 构建菜单列表
            result.append("【当前可用菜单】\n\n");

            if (dishes != null && !dishes.isEmpty()) {
                result.append("菜品：\n");
                for (Dish dish : dishes) {
                    result.append("- ").append(dish.getName())
                          .append(" ¥").append(dish.getPrice());
                    if (dish.getDescription() != null && !dish.getDescription().isEmpty()) {
                        result.append(" (").append(dish.getDescription()).append(")");
                    }
                    result.append("\n");
                }
                result.append("\n");
            }

            if (setmeals != null && !setmeals.isEmpty()) {
                result.append("套餐：\n");
                for (Setmeal setmeal : setmeals) {
                    result.append("- ").append(setmeal.getName())
                          .append(" ¥").append(setmeal.getPrice());
                    if (setmeal.getDescription() != null && !setmeal.getDescription().isEmpty()) {
                        result.append(" (").append(setmeal.getDescription()).append(")");
                    }
                    result.append("\n");
                }
                result.append("\n");
            }

            // 4. 从知识库检索相关信息（菜品详情、口味、做法等）
            ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .minScore(0.6)  // 降低阈值以获取更多相关信息
                    .maxResults(5)   // 增加结果数量
                    .build();

            List<Content> contents = retriever.retrieve(Query.from(question));

            if (!contents.isEmpty()) {
                result.append("【相关菜品知识】\n\n");
                String knowledgeInfo = contents.stream()
                        .map(c -> c.textSegment().text())
                        .collect(Collectors.joining("\n---\n"));
                result.append(knowledgeInfo);
                result.append("\n\n");
            }

            // 5. 添加推荐指引
            result.append("【推荐指引】\n");
            result.append("请根据以上菜单和知识库信息，结合用户的偏好（").append(question).append("）进行推荐。\n");
            result.append("推荐时请：\n");
            result.append("1. 只推荐菜单中存在的菜品或套餐\n");
            result.append("2. 每次推荐2-3道菜，给出推荐理由\n");
            result.append("3. 推荐后询问用户是否需要加入购物车\n");
            result.append("4. 如果用户确认，使用 addDishesToCart 工具批量添加，参数格式：\"菜品1,菜品2,菜品3\"");

            log.info("检索完成，找到{}个菜品，{}个套餐",
                    dishes != null ? dishes.size() : 0,
                    setmeals != null ? setmeals.size() : 0);

            return result.toString();

        } catch (Exception e) {
            log.error("检索菜品信息失败: question={}, error={}", question, e.getMessage(), e);
            return "抱歉，查询菜品信息时出现问题，请稍后再试～";
        }
    }

    /**
     * 获取完整菜单列表（不带知识库信息）
     * 用于用户直接询问"有什么菜"、"菜单"等场景
     *
     * @return 格式化的菜单列表
     */
    @Tool("当用户询问完整菜单、有什么菜、菜单列表时调用此工具，返回所有在售菜品和套餐")
    public String getFullMenu() {
        try {
            log.info("AI助手查询完整菜单");

            StringBuilder result = new StringBuilder();
            result.append("【完整菜单】🍜\n\n");

            // 查询所有在售的菜品
            Dish dishQuery = Dish.builder()
                    .status(StatusConstant.ENABLE)
                    .build();
            List<Dish> dishes = dishMapper.selectByCategoryId(dishQuery);

            // 查询所有在售的套餐
            Setmeal setmealQuery = Setmeal.builder()
                    .status(StatusConstant.ENABLE)
                    .build();
            List<Setmeal> setmeals = setmealMapper.list(setmealQuery);

            if (dishes != null && !dishes.isEmpty()) {
                result.append("【菜品】\n");
                for (int i = 0; i < dishes.size(); i++) {
                    Dish dish = dishes.get(i);
                    result.append(i + 1).append(". ")
                          .append(dish.getName())
                          .append(" - ¥").append(String.format("%.2f", dish.getPrice()));
                    if (dish.getDescription() != null && !dish.getDescription().isEmpty()) {
                        result.append("\n   ").append(dish.getDescription());
                    }
                    result.append("\n");
                }
                result.append("\n");
            }

            if (setmeals != null && !setmeals.isEmpty()) {
                result.append("【套餐】\n");
                for (int i = 0; i < setmeals.size(); i++) {
                    Setmeal setmeal = setmeals.get(i);
                    result.append(i + 1).append(". ")
                          .append(setmeal.getName())
                          .append(" - ¥").append(String.format("%.2f", setmeal.getPrice()));
                    if (setmeal.getDescription() != null && !setmeal.getDescription().isEmpty()) {
                        result.append("\n   ").append(setmeal.getDescription());
                    }
                    result.append("\n");
                }
            }

            if ((dishes == null || dishes.isEmpty()) && (setmeals == null || setmeals.isEmpty())) {
                result.append("抱歉，当前暂无可用菜品～");
            }

            log.info("菜单查询完成");
            return result.toString();

        } catch (Exception e) {
            log.error("查询菜单失败: error={}", e.getMessage(), e);
            return "抱歉，查询菜单时出现问题，请稍后再试～";
        }
    }
}
