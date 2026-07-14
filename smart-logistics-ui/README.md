# 伴生云途 · 宠物托运工作人员前端

## 首次克隆与 Git LFS

本项目的 ActiveTheory 场景视频使用 Git LFS 管理。首次克隆前请先安装 [Git LFS](https://git-lfs.com/)，并执行：

```bash
git lfs install
git clone https://github.com/Mela-xwt/smart-logistics-ui.git
cd smart-logistics-ui
git lfs pull
npm install
npm run dev
```

如果没有执行 `git lfs install` 和 `git lfs pull`，仓库中的 MP4 文件可能只是 LFS 指针文本，导航页视频将无法正常播放。
基于 Vue 3 + Vite + TypeScript + Element Plus + Leaflet + ECharts + Axios + Pinia + Vue Router + WebSocket 的伴生云途宠物托运工作人员工作台。

## 功能覆盖

- 货物追踪：实时位置、ETA、路线轨迹、运输时间线
- 车辆调度：车辆搜索筛选、全队分布、调度指令下发
- 告警中心：告警列表、级别标识、处理状态展示
- 仓储管理：车辆、货物、设备在线状态的数据入口已封装
- 智能问答：问答接口入口与业务面板
- 实时能力：WebSocket 接收位置、告警、指令回执；MQTT.js 预留设备主题订阅

## 环境变量

复制 `.env.example` 为 `.env.local`，按后端联调环境填写：

```bash
VITE_DATA_MODE=api
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_WS_URL=ws://localhost:8080/api/v1/ws
VITE_MQTT_URL=ws://localhost:8083/mqtt
```

没有后端时，页面使用演示数据；地图仍由 Leaflet 正常渲染。

## 运行

```bash
npm install
npm run dev
```

当前前端可在没有后端服务时独立运行，登录后默认使用本地演示数据。

- 演示账号：`dispatcher`
- 演示密码：`123456`
- 前端源码：`src/`
- 生产构建：`npm run build`
- 类型检查：`npm run typecheck`

演示环境支持在用户菜单中切换货主、仓库管理员、调度员、司机和系统管理员角色。已覆盖轨迹回放、车辆编辑删除、货物车辆解绑、司机状态上报、确认收货、实时通知、调度指令送达与执行回执等前端流程。

Mock 数据会保存在浏览器 `localStorage`，刷新页面或切换入口后仍会保留新增车辆、货物、绑定关系、告警状态和指令回执。需要重新演示时，可在右上角用户菜单选择“重置演示数据”。

源码按职责拆分为：

```txt
src/
├── data/       # 本地演示数据
├── services/   # Axios 与业务接口封装
├── stores/     # Pinia 业务状态
├── types/      # TypeScript 类型
├── views/      # 业务页面
├── App.vue     # 登录与工作台外壳
└── main.ts     # 应用入口
```

后端服务可用后，在 `.env.local` 中把 `VITE_DATA_MODE` 改为 `api` 并配置服务地址。页面会直接切换到真实接口、WebSocket 和断线轮询，无需再次修改业务页面。

## 接口协作

接口封装集中在 `src/services/api.ts`，路径与接口文档保持一致：

- `/auth/login`、`/auth/refresh`、`/auth/logout`
- `/cargo/{cargoId}`、`/cargo/{cargoId}/position`、`/cargo/{cargoId}/timeline`、`/cargo/{cargoId}/trajectory`、`/cargo/{cargoId}/eta`
- `/vehicles`、`/vehicles/{plate}`、`/vehicles/{plate}/command`
- `/alerts`、`/alerts/{alertId}`、`/alerts/stats`
- `/devices/status`
- `/assistant/chat`、`/assistant/suggestions`

统一响应在 `src/services/http.ts` 解包，非 `code: 0` 会直接提示错误。

## 接口层

接口相关代码集中在 `src/services/`：

- `api.ts`：认证、车辆、货物、告警、设备、问答、知识库和健康检查
- `http.ts`：Axios 实例、统一响应、业务错误、401 重试和 Token 自动刷新
- `token.ts`：accessToken、refreshToken、过期时间和用户信息
- `websocket.ts`：自动重连、恢复订阅、事件去重和 Mock 事件
- `mqtt.ts`：设备联调主题订阅
- `types.ts`：与接口文档对应的请求、响应和实时事件类型
- `config.ts`：Mock/API 模式及服务地址

后端联调时将 `VITE_DATA_MODE` 改为 `api`。未配置后端时使用 `mock`，页面不会发出真实接口请求。

真实模式下，路由守卫使用后端用户角色；位置、告警和指令回执通过 WebSocket 更新，连接不可用时每 15 秒轮询车辆与告警接口。
