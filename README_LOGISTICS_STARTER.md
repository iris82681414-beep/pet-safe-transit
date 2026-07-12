# 智慧物流 Starter 改造说明

本次只把原项目改造成可继续实践的基础骨架，没有实现真实业务链路。

## 已搭好的部分

- 前端源码：`project-rjwm-admin-vue-ts`
  - 登录页改为智慧物流主题。
  - 主菜单改为：货物追踪、车辆调度、告警中心、车辆货物、设备在线、智能问答。
  - 新增物流页面壳：`src/views/logistics/`。
  - 新增接口封装：`src/api/logistics.ts`。
  - Axios 兼容 `Authorization: Bearer <token>`。

- 后端源码：`sky-take-out`
  - 新增统一响应：`com.sky.logistics.common.ApiResponse`，成功码按文档为 `0`。
  - 新增 `/api/v1` starter 接口骨架。
  - 新增少量内存演示数据，方便前端先联调接口格式。
  - 新增 `/api/v1/ws` WebSocket 空壳。
  - Swagger 增加“智慧物流接口”分组。

## 故意没有做完的部分

- PostgreSQL / TimescaleDB 真实持久化。
- MQTT -> Kafka -> Redis -> WebSocket 主链路。
- 告警算法、调度指令 MQTT 下发、command ack 状态流转。
- MinIO / pgvector / RAG 真实问答。
- 前端地图、表单、真实列表操作和权限细节。

这些部分按 `智慧物流_真实版前后端文档包` 里的排期继续做。

## 启动入口

后端：

```bash
cd sky-take-out
mvn spring-boot:run -pl sky-server
```

前端：

```bash
cd project-rjwm-admin-vue-ts
npm install
npm run serve
```

演示账号：

```txt
shipper / 123456
dispatcher / 123456
warehouse / 123456
admin / 123456
driver / 123456
```

接口前缀：

```txt
http://localhost:8080/api/v1
ws://localhost:8080/api/v1/ws?token=<token>
```
