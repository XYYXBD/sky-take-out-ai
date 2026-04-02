# AI 对话模块说明（购物车 / 下单意图 / RAG）

## 1. 文档目标

本文档面向新接手同学，目标是让你快速看懂以下新增 AI 能力：

- 用户对话入口与鉴权
- 意图路由（QA / CART / BOTH）
- 购物车工具链路（含口味判定与加购状态机）
- 下单相关意图处理（当前以购物车操作为主）
- RAG 咨询链路
- memory 隔离与用户上下文注入
- 错误处理、日志排查、扩展建议

---

## 2. 范围与边界

### 2.1 已实现范围

- `/user/chat` 对话接口（流式返回）
- 订单相关 Agent（`OrderAgent`）
- 咨询相关 Agent（`RagAgent`）
- 意图路由 Agent（`IntentRouterAiService`）
- 购物车工具（查询菜品、查口味、单次加购、看购物车、清空购物车）
- Redis 持久化会话 memory
- memoryId -> userId 稳定映射（解决异步线程中 ThreadLocal 不可用）

### 2.2 当前边界

- 名称叫“OrderAgent”，但当前工具集核心是购物车操作，不包含完整“提交订单/支付”交易链路
- `BOTH` 意图当前统一走订单链（购物车链），未并行触发 QA 链

---

## 3. 核心组件与职责

### 3.1 Controller 层

- `sky-server/src/main/java/com/sky/controller/user/ChatController.java`
- 负责：
  - 接收 `/user/chat?message=...`
  - 从 `BaseContext` 获取 `userId`
  - 未登录时抛 `UserNotLoginException`
  - 调用统一编排服务 `UnifiedAiChatService`

### 3.2 统一编排层

- `sky-server/src/main/java/com/sky/service/impl/UnifiedAiChatServiceImpl.java`
- 负责：
  - 生成并隔离 memory：
    - 订单链：`chat:order:user:{userId}`
    - 咨询链：`chat:qa:user:{userId}`
  - 将 memory 与 user 绑定到注册器
  - 根据关键词捷径 + 路由模型，分流到 `OrderAgent` 或 `RagAgent`

### 3.3 Agent 接口

- `sky-server/src/main/java/com/sky/aiService/OrderAgent.java`
  - 绑定 `shoppingCartTool`
  - 用于购物车/下单意图相关处理
- `sky-server/src/main/java/com/sky/aiService/RagAgent.java`
  - 绑定 `contentRetriever`
  - 用于咨询问答
- `sky-server/src/main/java/com/sky/aiService/IntentRouterAiService.java`
  - 只负责分类：`QA` / `CART` / `BOTH`

### 3.4 Tool 层（购物车）

- `sky-server/src/main/java/com/sky/tools/ShoppingCartTool.java`
- 对外暴露的核心工具：
  - 工具A：`queryDishByName`
  - 工具B：`queryDishFlavors`
  - 工具C：`addOneToCart`
  - 工具D：`getCartItems`
  - 工具E：`clearCartItems`

### 3.5 会话与上下文基础设施

- memory 存储：`sky-server/src/main/java/com/sky/repository/RedisChatMemoryStore.java`
- memory provider：`sky-server/src/main/java/com/sky/config/ChatConfiguration.java`
- user 上下注入：`sky-server/src/main/java/com/sky/context/AgentUserContextRegistry.java`

---

## 4. 端到端流程

### 4.1 流程图（文本版）

1. 客户端调用 `/user/chat?message=...`
2. `JwtTokenUserInterceptor` 解析 JWT，写入 `BaseContext`
3. `ChatController` 获取 `userId`，校验登录态
4. `UnifiedAiChatServiceImpl`：
   - 生成 `orderMemoryId` 和 `qaMemoryId`
   - 绑定 memoryId -> userId
   - 关键词命中则直达 `OrderAgent`
   - 否则调用 `IntentRouterAiService.route(...)`
5. 路由结果：
   - `CART` / `BOTH` -> `OrderAgent.handleOrderQuery(...)`
   - `QA` -> `RagAgent.chat(...)`
6. Agent 在推理过程中按需调用工具（例如购物车工具）
7. 工具返回结构化结果，Agent 组织自然语言回复并流式输出

---

## 5. 路由策略细节

### 5.1 关键词捷径（优先）

在 `UnifiedAiChatServiceImpl.isCartIntent(...)` 内，若消息包含如下关键词，会直接走 `OrderAgent`：

- 加入购物车
- 加到购物车
- 来一份 / 来两份
- 下单
- 再来一个
- 帮我点 / 我要点

### 5.2 模型路由（兜底）

- system prompt: `sky-server/src/main/resources/static/system/intentRouterAiService.txt`
- 输出必须映射到 `RouteDecision`
- 若路由为空或无意图，默认回退 QA 链

---

## 6. 购物车 / 下单意图链路（OrderAgent）

### 6.1 OrderAgent 目标

- 当前主要处理“点菜到购物车”相关动作
- 不是支付级别订单交易引擎

### 6.2 工具约束提示词

- `sky-server/src/main/resources/static/system/orderAgent.txt`
- 核心原则：逐个菜处理、逐次加购、口味必须合法、完成后展示购物车

### 6.3 工具 C：`addOneToCart` 关键设计

方法签名关键点：

- `@ToolMemoryId String memoryId`
- `dishFlavor` 参数通过 `@P(required = false)` 标记为非必填

核心逻辑：

1. 从 `memoryId` 解析 `userId`（不依赖 ThreadLocal）
2. 口味归一化：空字符串或全空白 => `null`
3. 判定菜品是否需要口味
4. 状态分支：
   - `NO_FLAVOR_NEEDED`：无需口味，直接加购
   - `FLAVOR_REQUIRED`：需要口味但未传
   - `FLAVOR_INVALID`：口味不在可选集合
   - `ADD_SUCCESS`：成功加入购物车

### 6.4 用户上下文缺失处理

- 若 `memoryId` 无法解析到用户，返回：
  - `code=USER_CONTEXT_MISSING`
- 避免落到数据库层的空 userId 异常

---

## 7. RAG 咨询链路（RagAgent）

### 7.1 RagAgent 行为

- 通过 `contentRetriever` 做检索增强生成
- memory 使用 QA 独立命名空间
- system prompt: `sky-server/src/main/resources/static/system/ragAgent.txt`

### 7.2 数据来源

- `ChatConfiguration.store()` 启动时读取 `static/content` 文档并写入向量库
- 检索参数（当前）：
  - `minScore=0.5`
  - `maxResults=3`

### 7.3 说明

- `RetrievalTool` 已实现，但当前 `RagAgent` 通过 `contentRetriever` 工作，不依赖 tool 调用

---

## 8. Memory 与用户上下文机制

### 8.1 为什么要做

AI 工具调用可能发生在异步执行链，`BaseContext`（ThreadLocal）在异步线程中不可稳定获取。

### 8.2 当前机制

- 编排层先建立 memoryId：
  - `chat:order:user:{userId}`
  - `chat:qa:user:{userId}`
- `AgentUserContextRegistry.bind(memoryId, userId)` 持久绑定（进程内）
- Tool 收到 `@ToolMemoryId` 后，反查 userId 进行业务操作

### 8.3 会话存储

- `RedisChatMemoryStore` 按 memoryId 存储历史消息
- TTL 为 1 天
- `MessageWindowChatMemory.maxMessages = 20`

---

## 9. 错误码与异常策略

### 9.1 控制器层

- 未登录：`UserNotLoginException("未登录或token无效")`

### 9.2 工具层（典型）

- `INVALID_PARAM`
- `DISH_NOT_FOUND`
- `FLAVOR_REQUIRED`
- `FLAVOR_INVALID`
- `NO_FLAVOR_NEEDED`
- `ADD_SUCCESS`
- `USER_CONTEXT_MISSING`
- `SYSTEM_ERROR`

### 9.3 目标

- 优先返回可读业务错误
- 避免用户问题演化为底层 SQL/空指针异常

---

## 10. 日志与排查手册

### 10.1 核心日志点

- 编排入口：`userId`, `orderMemoryId`, `qaMemoryId`, `message`
- 路由结果：`decision`
- 加购入口：`userId`, `memoryId`, `dishName`, `dishFlavor`
- 加购成功：`dishId`/`setmealId`
- 上下文缺失：`memoryId` + 错误原因

### 10.2 常见排查路径

1. 用户报“无法操作购物车”
   - 看是否出现 `USER_CONTEXT_MISSING`
   - 检查是否执行了 `bind(memoryId, userId)`
2. 用户报“口味明明填了还失败”
   - 检查传入口味是否被归一化为空
   - 检查 `parseFlavorOptions` 提取结果
3. 购物车和咨询串历史
   - 检查 memory 是否使用了 `chat:order:user:*` 与 `chat:qa:user:*` 分离前缀

---

## 11. 配置与依赖

### 11.1 关键配置

- `sky-server/src/main/resources/application.yml`
  - `langchain4j.ollama.chat-model`
  - `langchain4j.ollama.streaming-chat-model`
  - `langchain4j.ollama.embedding-model`
  - Redis 连接配置

### 11.2 关键依赖（`sky-server/pom.xml`）

- `langchain4j-ollama-spring-boot-starter`
- `langchain4j-spring-boot-starter`
- `langchain4j-reactor`
- `langchain4j-easy-rag`
- `langchain4j-community-redis-spring-boot-starter`

---

## 12. 关键代码索引

- 对话入口：`sky-server/src/main/java/com/sky/controller/user/ChatController.java`
- 统一编排：`sky-server/src/main/java/com/sky/service/impl/UnifiedAiChatServiceImpl.java`
- 路由 Agent：`sky-server/src/main/java/com/sky/aiService/IntentRouterAiService.java`
- 订单 Agent：`sky-server/src/main/java/com/sky/aiService/OrderAgent.java`
- RAG Agent：`sky-server/src/main/java/com/sky/aiService/RagAgent.java`
- 购物车工具：`sky-server/src/main/java/com/sky/tools/ShoppingCartTool.java`
- 用户上下文注册器：`sky-server/src/main/java/com/sky/context/AgentUserContextRegistry.java`
- ChatMemory 配置：`sky-server/src/main/java/com/sky/config/ChatConfiguration.java`
- Redis 会话存储：`sky-server/src/main/java/com/sky/repository/RedisChatMemoryStore.java`
- 购物车服务实现：`sky-server/src/main/java/com/sky/service/impl/ShoppinCartServiceImp.java`
- 路由提示词：`sky-server/src/main/resources/static/system/intentRouterAiService.txt`
- 订单提示词：`sky-server/src/main/resources/static/system/orderAgent.txt`
- 咨询提示词：`sky-server/src/main/resources/static/system/ragAgent.txt`

---

## 13. 后续建议（可选）

1. `BOTH` 意图可升级为双链并发（订单链 + QA 链），再做结果融合。
2. `OrderAgent` 的 system message 可以改为 `fromResource = "static/system/orderAgent.txt"`，与文档化提示词保持一致。
3. 为 `addOneToCart` 增加单元测试：覆盖四种状态码与 `USER_CONTEXT_MISSING`。
4. 为 `AgentUserContextRegistry` 增加 TTL 或清理机制，避免长时间进程映射膨胀。


