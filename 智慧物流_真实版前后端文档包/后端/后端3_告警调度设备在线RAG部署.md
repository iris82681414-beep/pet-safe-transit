# 后端 3 · 告警、调度、设备在线、RAG、MinIO、部署

## 1. 你的定位

你负责系统闭环：发现异常、处理异常、下发指令、设备回执、AI 问答、部署。

---

## 2. 负责范围

### 必须负责

- 告警列表、详情、统计。
- 告警确认、关闭。
- 告警日志。
- 偏航、异常停车、异常开箱、设备离线规则。
- 设备在线状态。
- 调度指令保存。
- MQTT command 发布。
- MQTT command/ack 订阅。
- WebSocket 推送 `alert.triggered`、`command.ack`。
- MinIO 知识库文档。
- pgvector 文档向量。
- `/assistant/chat` 真实 RAG。
- Docker Compose、Nginx、README。

### 不负责

- 不负责车辆/货物基础 CRUD。
- 不负责 GPS 入库主链路。
- 不负责前端样式。

---

## 3. 你要实现的接口

### 3.1 告警

| 方法 | 路径 | 优先级 | 完成日 |
|---|---|---|---|
| GET | `/alerts` | P0 | Day 3 |
| GET | `/alerts/{alertId}` | P0 | Day 3 |
| GET | `/alerts/stats` | P1 | Day 4 |
| POST | `/alerts/{alertId}/acknowledge` | P0 | Day 4 |
| POST | `/alerts/{alertId}/resolve` | P0 | Day 4 |

### 3.2 调度

| 方法 | 路径 | 优先级 | 完成日 |
|---|---|---|---|
| POST | `/vehicles/{plate}/command` | P0 | Day 4 |
| GET | `/vehicles/{plate}/command/{commandId}` | P0 | Day 4 |
| GET | `/vehicles/{plate}/commands` | P1 | Day 5 |

### 3.3 设备

| 方法 | 路径 | 优先级 | 完成日 |
|---|---|---|---|
| GET | `/devices/status` | P0 | Day 4 |
| GET | `/devices/{imei}` | P1 | Day 5 |
| GET | `/devices/{imei}/heartbeats` | P1 | Day 5 |
| GET | `/devices/{imei}/cargo-events` | P1 | Day 5 |

### 3.4 智能问答与知识库

| 方法 | 路径 | 优先级 | 完成日 |
|---|---|---|---|
| POST | `/assistant/chat` | P1 | Day 5 |
| GET | `/assistant/suggestions` | P1 | Day 5 |
| GET | `/assistant/sessions/{sessionId}/messages` | P2 | Day 5 |
| POST | `/knowledge/documents` | P1 | Day 5 |
| POST | `/knowledge/documents/{documentId}/index` | P1 | Day 5 |
| GET | `/knowledge/documents` | P2 | Day 5 |
| DELETE | `/knowledge/documents/{documentId}` | P2 | Day 6 |

---

## 4. MQTT Topic

| Topic | 方向 | 你要做什么 |
|---|---|---|
| `vehicle/{vin}/command` | Cloud → Device | 发布调度指令 |
| `vehicle/{vin}/command/ack` | Device → Cloud | 接收回执，更新 command 状态 |
| `vehicle/{vin}/cargo/event` | Device → Cloud | 开箱事件，触发 CARGO_DOOR 告警 |

---

## 5. 告警规则

| 告警类型 | 规则 | 说明 |
|---|---|---|
| ROUTE_DEVIATION | 偏离预设路线 > 3km 且持续 > 10min | 6 天可用简化路线点判断 |
| ABNORMAL_STOP | 速度 = 0 持续 > 15min 且不在服务区/装卸点 | 先基于 GPS speed 判断 |
| CARGO_DOOR | DOOR_OPEN 且不在装卸点 | 来自 cargo/event |
| DEVICE_OFFLINE | lastHeartbeat 超过 90s | 来自 heartbeat |

去重：同一车辆同一告警类型，未关闭前不要重复生成。

---

## 6. RAG 最低真实实现

```txt
1. 上传知识库文件到 MinIO。
2. 解析 Markdown/PDF/Word，PDF/Word 来不及可先支持 Markdown。
3. 文本切片，每片 300-800 字。
4. 调用 embedding 模型生成向量。
5. 写入 pgvector knowledge_chunks。
6. /assistant/chat 对问题生成向量。
7. pgvector 相似度检索 Top-5。
8. 拼 Prompt 调用 DeepSeek/Qwen。
9. 返回 answer + sources。
```

注意：如果大模型 API 不稳定，至少保证检索 sources 能返回，answer 可以有兜底模板，但流程必须真实经过检索。

---

## 7. 6 天任务

### Day 1

- 建 alerts / alert_logs / commands / command_logs / knowledge_documents / knowledge_chunks。
- MinIO 启动。
- pgvector 扩展。
- docker-compose 初版。

### Day 2

- heartbeat 消费。
- 设备最新心跳写 Redis。
- 告警接口空壳。

### Day 3

- `/alerts`、`/alerts/{alertId}`。
- 基础告警生成：偏航、离线。
- WebSocket 推送 `alert.triggered`。

### Day 4

- 告警确认/关闭/统计。
- `/devices/status`。
- `/vehicles/{plate}/command`。
- MQTT command publish。
- command/ack 订阅。

### Day 5

- MinIO 文档上传。
- pgvector 索引。
- `/assistant/chat`。
- Docker Compose 补全。

### Day 6

- 不加新功能。
- 修部署和演示问题。
- 写 README 和 MQTTX 样例。

---

## 8. 完成标准

- 告警能自动生成、确认、关闭。
- 设备在线状态来自真实 heartbeat。
- 指令真实发布到 MQTT。
- 设备 ack 后 command 状态真实变化。
- 智能问答真实检索知识库。
- docker-compose 能一键启动核心服务。

---

## 9. 避坑

1. 告警一定要做去重。
2. command 状态不能靠前端假改，必须由后端收到 ack 后更新。
3. RAG 不要追求复杂 UI，后端流程真实更重要。
4. 部署不是最后一天才做，Day 1 就要有 compose。
5. Day 6 不要再动架构。
