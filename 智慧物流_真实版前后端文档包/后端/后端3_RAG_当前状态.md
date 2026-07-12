
# 后端3 RAG 当前状态 · 已做/未做清单

## 已完成

### 知识库文档管理
- [x] `POST /knowledge/documents` — 上传 .md（测试） 到 MinIO + 写 DB
- [x] `GET /knowledge/documents` — 文档列表（分页、搜索、分类筛选）
- [x] `DELETE /knowledge/documents/{id}` — 删 MinIO 文件 + DB 记录 + 关联 chunks
- [x] `POST /knowledge/documents/{id}/index` — 下载 MinIO → 按 `##`+段落切片 → embedding → 写入 pgvector

### 智能问答 RAG
- [x] `POST /assistant/chat` — 完整链路已跑通
  - [x] 预检索：正则提取车牌/cargoId → 查车辆位置、告警
  - [x] 查询改写：LLM 把口语转为检索关键词
  - [x] 混合检索：pgvector 余弦 + ILIKE 关键词 + RRF 融合
  - [x] 生成：Prompt 拼装（角色+时间+业务数据+知识库）→ DeepSeek
  - [x] 通用问题兜底：无具体车牌时注入平台总览
  - [x] 输出：禁止 markdown，纯文本
- [x] `GET /assistant/suggestions` — 按角色返回建议问题（JWT 解析→DISPATCHER/SHIPPER）
- [x] `GET /assistant/sessions/{id}/messages` — Redis 会话历史

### 设备接口
- [x] `GET /devices/status` — 设备在线状态列表（Redis 心跳）
- [x] `GET /devices/{imei}` — 设备详情
- [x] `GET /devices/{imei}/heartbeats` — 查 TimescaleDB device_heartbeats
- [x] `GET /devices/{imei}/cargo-events` — 查 alerts 表 CARGO_DOOR 告警
- [x] Kafka 消费者：vehicle-heartbeats → TimescaleDB device_heartbeats

### 基础设施
- [x] MinIO 存储 + pgvector 向量检索
- [x] Docker Compose 端口 127.0.0.1 绑定（安全）
- [x] API Key 环境变量（不写代码里）
- [x] ivfflat 索引优化（lists=100）
- [x] CORS 跨域配置
- [x] JwtProperties 注入修复（AssistantController）

### 对抗性审查修复
- [x] flushChunk 长段落截断 → 按句号切分
- [x] RRF 用 HashSet 丢排序 → LinkedHashMap 保序
- [x] RestTemplate 无超时 → 加 5s/30s/60s
- [x] @Transactional 包裹外部 API → 拆分事务
- [x] CRLF 换行不处理 → 统一 normalize
- [x] 文件上传无校验 → 10MB + .md 白名单
- [x] MinIO bucket 失败静默 → 抛异常阻止启动
- [x] 心跳 JSON ClassCastException → JsonNode 安全读取
- [x] 会话 ID 未返回 → chat 响应加 sessionId
- [x] 分页总数用 content.size → 修复为 DB count
- [x] indexDocument 并发重复 → 加 INDEXING 状态互斥
- [x] 事务失败回滚丢 FAILED 状态 → 加 try-catch 兜底
- [x] 异常静默吞 → buildBusinessContext 加 warn 日志

---

##  未做

### 文档权限
不实现。当前所有文档全局共享。后续若加 PRIVATE/ROLE/PUBLIC 三级可见范围，需改 5 个文件。

### PDF 支持
不实现。当前仅支持 .md。

### 用户自行上传文档
不实现。上传接口无角色限制，开发者/管理员用。

### 中文关键词分词
不实现。当前用简单去停用词 + ILIKE。加 jieba 分词可提升检索精度。

### 批量 embedding
不实现。当前逐条调用 embedding API。接口支持批量但未接入。

### 知识库切片暴露接口
未加 `GET /knowledge/documents/{id}/chunks`。前端有展示切片的需求时再加。

### Session ID 碰撞
8 位 hex 有 32 位熵。演示够用，不升级。

### 硬编码常量可配置化
chunk_size、top_k、timeout 等都是硬编码。不改。

---

## 前端联调已修问题
- [x] ElementPlus 自动导入插件不兼容 Node 18 → 改手动导入
- [x] `answeredAt` 缺失 → 前端 Invalid Date → 后端加了
- [x] AI 用 markdown → Prompt 禁止
- [x] 通用问题无上下文 → 加 buildOverviewContext
- [x] CORS 跨域 → WebMvcConfiguration 加 addCorsMappings
