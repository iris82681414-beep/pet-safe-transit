# 变更日志 — 高德地图 + LLM 智能问答 + 接口补全

> **日期**: 2026-07-05  
> **分支**: main  
> **关联**: Amap Web服务 Key, DeepSeek LLM, SiliconFlow Embedding, 前后端功能补全

---

## 一、配置变更

### 1.1 高德地图 Amap (`application-dev.yml`)

新增 `amap` 配置块，用于后端调用高德 Web 服务 REST API：

```yaml
amap:
  web-key: ${AMAP_WEB_KEY:}   # 高德 Web服务 Key
  base-url: ${AMAP_BASE_URL:https://restapi.amap.com}
  connect-timeout-ms: ${AMAP_CONNECT_TIMEOUT_MS:3000}
  read-timeout-ms: ${AMAP_READ_TIMEOUT_MS:5000}
  cache-minutes: ${AMAP_CACHE_MINUTES:30}
```

- Key `5c5f3cfbb3dc7f155d20f3f414d5c1df` 已验证通过：地理编码、逆地理编码、输入提示、驾车路线规划均正常
- 货车路线规划 (`/v4/direction/truck`) 需要额外开通高德企业级权限，当前自动降级为驾车路线兜底
- 前端 `.env.local` 中 `VITE_AMAP_KEY` 和 `VITE_AMAP_SECURITY_CODE` 也已同步更新

### 1.2 LLM 大模型 (`application-dev.yml`)

```yaml
ai:
  llm:
    endpoint: ${AI_LLM_ENDPOINT:https://api.deepseek.com}
    api-key: ${DEEPSEEK_API_KEY:}
    model: ${AI_LLM_MODEL:deepseek-chat}
  embedding:
    endpoint: ${AI_EMBEDDING_ENDPOINT:https://api.siliconflow.cn}
    api-key: ${SILICONFLOW_API_KEY:}
    model: ${AI_EMBEDDING_MODEL:BAAI/bge-m3}
```

- **DeepSeek**: 智能问答 (`/api/v1/assistant/chat` 及流式 `/chat/stream`) 已测试通过
- **SiliconFlow Embedding (BGE-M3)**: Key 已配置，用于知识库文档向量化

### 1.3 基础设施条件化 (`application-dev.yml`)

```yaml
spring.kafka.listener.auto-startup: ${KAFKA_ENABLED:true}   # 无 Kafka 时优雅降级
mqtt.enabled: ${MQTT_ENABLED:true}                           # 无 MQTT 时使用占位 Handler
```

---

## 二、后端新增功能 (40+ 文件, ~4573 行)

### 2.1 高德地图集成

| 文件 | 说明 |
|------|------|
| `config/AmapProperties.java` | 高德配置属性绑定类 (webKey, baseUrl, 超时, 缓存) |
| `service/AmapService.java` | Amap 服务接口 |
| `service/impl/AmapServiceImpl.java` (570行) | Amap 核心实现：输入提示、地理/逆地理编码、驾车/货车路线规划 |
| `controller/AmapController.java` | REST 接口 `GET /input-tips`, `POST /geocode`, `POST /regeo` |

**已测试接口：**

| 端点 | 方法 | 测试结果 |
|------|------|----------|
| `/api/v1/amap/input-tips?keywords=武汉火车站&city=武汉` | GET | ✅ 返回真实 Amap 数据 |
| `/api/v1/amap/geocode` | POST | ✅ `"source":"AMAP"`, 返回经纬度 |
| `/api/v1/amap/regeo` | POST | ✅ `"source":"AMAP"`, 返回格式化地址 |

**降级策略**：当 Key 未配置或 API 调用失败时，自动返回本地兜底结果 (`LOCAL_FALLBACK`)，不影响业务流程。

### 2.2 路线规划

| 文件 | 说明 |
|------|------|
| `service/RoutePlanService.java` | 路线服务接口 |
| `service/impl/RoutePlanServiceImpl.java` (163行) | 实现：驾车规划、货车规划、路线重规划、偏航检测、改道建议 |
| `controller/RouteController.java` | REST 接口 |

**接口列表：**

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/routes/plan` | POST | 驾车路线规划 |
| `/api/v1/routes/truck-plan` | POST | 货车路线规划 (含车辆参数) |
| `/api/v1/routes/replan` | POST | 路线重新规划 |
| `/api/v1/routes/deviation/check` | POST | 偏航检测 |
| `/api/v1/routes/reroute-suggestion` | POST | 改道建议 |
| `/api/v1/routes/trajectory/correct` | POST | 轨迹纠偏 |

**已测试**：`/routes/plan` 返回真实高德路网数据 (17.18km, 39min, 12个红绿灯, polyline轨迹点)。

### 2.3 订单扩展 & 地址变更

| 文件 | 说明 |
|------|------|
| `controller/OrderExtensionController.java` (116行) | 订单扩展接口 |
| `controller/AddressChangeRequestController.java` (60行) | 地址变更审批流 |
| `service/OrderExtensionService.java` | 接口 |
| `service/impl/OrderExtensionServiceImpl.java` (851行) | 核心逻辑实现 |
| `mapper/OrderExtensionMapper.java` / `.xml` | 数据访问 |
| `entity/AddressChangeRequest.java` | 地址变更请求实体 |
| `entity/AddressChangeLog.java` | 变更日志实体 |
| `entity/DriverRating.java` / `DriverRatingTag.java` | 司机评分实体 |
| `entity/UnloadAddressRecord.java` | 卸货地址记录 |

**接口列表：**

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/orders/{orderId}/address-change-impact` | POST | 评估地址变更影响 |
| `/api/v1/orders/{orderId}/address-change-requests` | POST | 创建地址变更请求 |
| `/api/v1/orders/{orderId}/address-change-history` | GET | 变更历史 |
| `/api/v1/orders/{orderId}/delay-prediction` | GET | 延迟预测 |
| `/api/v1/orders/{orderId}/risk-score` | GET | 风险评分 |
| `/api/v1/orders/{orderId}/status/verify` | POST | 状态验证 |
| `/api/v1/orders/{orderId}/exception-summary` | GET | 异常汇总 |
| `/api/v1/orders/{orderId}/unload-address/suggestions` | GET | 卸货地址建议 |
| `/api/v1/orders/{orderId}/unload-address/confirm` | POST | 确认卸货地址 |
| `/api/v1/orders/{orderId}/unload-address/abnormal` | POST | 异常卸货上报 |
| `/api/v1/orders/{orderId}/driver-rating` | GET/POST | 司机评分 |
| `/api/v1/address-change-requests/{requestId}` | GET | 变更请求详情 |
| `/api/v1/address-change-requests/{requestId}/approve` | POST | 批准变更 |
| `/api/v1/address-change-requests/{requestId}/reject` | POST | 驳回变更 |
| `/api/v1/address-change-requests/{requestId}/driver-confirm` | POST | 司机确认 |

### 2.4 驾驶员管理

| 文件 | 说明 |
|------|------|
| `controller/DriverController.java` | 驾驶员 REST 接口 |
| `entity/DriverRating.java` | 评分实体 |

**接口：**

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/drivers/{driverId}/rating-summary` | GET | ✅ 已测试，返回评分汇总 |
| `/api/v1/drivers/{driverId}/ratings` | GET | 评分历史列表 |

### 2.5 安全 & 权限控制

| 文件 | 说明 |
|------|------|
| `service/LogisticsSecurityService.java` | 安全服务接口 |
| `service/impl/LogisticsSecurityServiceImpl.java` | JWT 解析 + 角色校验 |
| `common/LogisticsForbiddenException.java` | 403 异常类 |
| `vo/LogisticsUserContextVO.java` | 用户上下文 VO |

- Alert 告警接口增加 `SHIPPER` 角色拦截（货主无权查看/操作告警）
- 新增 `@ResponseStatus` 注解，HTTP 状态码规范化

---

## 三、后端修复/改进 (10 文件, ~81 行)

### 3.1 优雅降级

| 文件 | 改动 |
|------|------|
| `MinioConfig.java` | MinIO 初始化失败不再抛 `RuntimeException` 阻断启动，改为 `log.warn` |
| `MqttConfig.java` | 新增 `@ConditionalOnProperty(mqtt.enabled)` 条件加载；`mqtt.enabled=false` 时注册占位 Handler |
| `GpsKafkaConsumer.java` | `@KafkaListener` 增加 `autoStartup="${spring.kafka.listener.auto-startup}"` |
| `HeartbeatKafkaConsumer.java` | 同上 |

### 3.2 异常处理增强

| 文件 | 改动 |
|------|------|
| `LogisticsExceptionHandler.java` | 新增 `LogisticsForbiddenException` → 403；所有 Handler 增加 `@ResponseStatus` |

### 3.3 数据层完善

| 文件 | 改动 |
|------|------|
| `LogisticsCargoMapper.java` | 新增 `updateDestination` 方法 |
| `LogisticsCargoMapper.xml` | 对应 SQL：更新货物目的地名称和经纬度 |

### 3.4 SQL 初始化

| 文件 | 说明 |
|------|------|
| `sql/amap_order_extension_init.sql` | 地址变更、司机评分、卸货地址等表结构 |

---

## 四、前端变更 (8 文件, +1249 / -11 行)

### 4.1 权限调整

**`src/App.vue`**: 告警中心菜单移除 `SHIPPER` 角色（货主不显示告警入口），与后端 `AlertController` 角色拦截一致。

### 4.2 组件库扩展

**`src/components.d.ts`**: 新增全局组件声明：
- `ElButtonGroup`, `ElCheckboxButton`, `ElCheckboxGroup` — 多选筛选
- `ElRate` — 星级评分

### 4.3 类型定义

**`src/services/types.ts`** (+337行): 新增完整的 TypeScript 类型定义：
- `GeoPoint`, `RoutePlanRequest`, `RoutePlanResponse`, `RouteStep`
- `AddressChangeRequest`, `AddressChangeImpact`, `DriverRating`
- `AmapAddress`, `AmapInputTip`, `DeviationCheck`, `UnloadAddress`
- `OrderException`, `DelayPrediction`, `RiskScore` 等

### 4.4 API 层

**`src/services/api.ts`** (+89行): 新增 API 调用方法：
- 高德地址服务：`geocode`, `regeo`, `inputTips`
- 路线规划：`planRoute`, `planTruckRoute`, `replanRoute`, `checkDeviation`
- 地址变更：`createAddressChange`, `getChangeHistory`, `approveChange`, `rejectChange`
- 司机评分：`getDriverRatingSummary`, `submitDriverRating`
- 卸货地址：`confirmUnloadAddress`, `reportAbnormalUnload`

### 4.5 视图增强

**`src/views/TrackingView.vue`** (+550行): 货物追踪视图大幅增强
- 高德地图实时轨迹展示
- 地址变更请求创建
- 卸货地址异常上报
- 司机评分组件
- 延迟预测和风险评分卡片

**`src/views/DispatchView.vue`** (+85行): 车辆调度视图增强
- 多选筛选器
- 路线规划入口

**`src/styles.css`** (+188行): 新增样式
- 追踪视图布局
- 评分组件样式
- 状态标签颜色方案
- 地图容器样式

**`src/router.ts`**: 路由微调

---

## 五、文档

| 文件 | 说明 |
|------|------|
| `高德APIMD/00_README_使用说明.md` | 高德 API 对接使用说明 |
| `高德APIMD/高德API接入总文档_参考.md` (21KB) | 高德 Web 服务 API 完整参考 |
| `高德APIMD/前端/` | 前端 JS API 对接文档 |
| `高德APIMD/后端/` | 后端 REST API 对接文档 |
| `高德APIMD/对接/` | 前后端对接说明 |

---

## 六、测试验证

| 测试项 | 状态 | 备注 |
|--------|:--:|------|
| 高德 geocode (地址→经纬度) | ✅ | `source: AMAP` |
| 高德 regeo (经纬度→地址) | ✅ | `source: AMAP` |
| 高德 input-tips (输入提示) | ✅ | 返回真实 POI 数据 |
| 高德 驾车路线规划 | ✅ | 真实路网, polyline 轨迹 |
| 高德 货车路线规划 | ⚠️ | Key 无权限, 降级为驾车兜底 |
| LLM 智能问答 (普通) | ✅ | DeepSeek 正常回复 |
| LLM 智能问答 (流式) | ✅ | SSE 流式正常 |
| LLM 建议接口 | ✅ | 正常返回 |
| 知识库文档列表 | ✅ | 正常 (空库) |
| 驾驶员评分 | ✅ | 正常 |
| 系统状态 | ⚠️ | 各组件监控标记 TODO |

---

## 七、未完成/待办

1. **高德货车路线规划**：需在高德控制台开通企业级「货车路线规划」API 权限
2. **System Status 监控**：各基础设施健康检查均为 `TODO`，待实现
3. **知识库文档**：MinIO bucket 需手动创建后上传文档进行 RAG 测试
4. **Kafka/EMQX**：Docker 基础设施未启动（用户不在 docker 组），Kafka Consumer 和 MQTT 相关功能未验证
