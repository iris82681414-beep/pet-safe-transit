# 高德 API 接入 · 前后端接口联调总表

> 用于每天联调时勾选。谁负责、谁调用、什么页面用，一张表看清楚。

---

## 1. 高德与地址类接口

| 接口 | 负责人 | 调用方 | 页面/用途 | 优先级 |
|---|---|---|---|---|
| `GET /api/amap/input-tips` | 后端2 | 前端2 | 改址地址输入提示 | P0 |
| `POST /api/amap/geocode` | 后端2 | 前端2/后端1 | 地址转坐标 | P0 |
| `POST /api/amap/regeo` | 后端2 | 前端1/后端3 | GPS 点转地址描述 | P1 |
| `POST /api/routes/plan` | 后端2 | 前端1/后端3 | 普通路线规划 | P0 |
| `POST /api/routes/truck-plan` | 后端2 | 前端1/后端3 | 货车路线规划 | P1 |
| `POST /api/trajectory/correct` | 后端2 | 前端1 | 轨迹纠偏 | P2 |

---

## 2. 改址流程接口

| 接口 | 负责人 | 调用方 | 页面/用途 | 优先级 |
|---|---|---|---|---|
| `POST /api/orders/{orderId}/address-change-impact` | 后端2 | 前端2 | 改址影响预估 | P0 |
| `POST /api/orders/{orderId}/address-change-requests` | 后端1 | 前端2 | 客户提交改址申请 | P0 |
| `GET /api/orders/{orderId}/address-change-history` | 后端1 | 前端2 | 改址进度时间线 | P0 |
| `POST /api/address-change-requests/{requestId}/approve` | 后端3 | 前端2 | 调度员通过改址 | P0 |
| `POST /api/address-change-requests/{requestId}/reject` | 后端3 | 前端2 | 调度员拒绝改址 | P0 |
| `POST /api/address-change-requests/{requestId}/driver-confirm` | 后端3 | 前端2 | 司机确认新地址 | P1 |

---

## 3. 调度与路线纠偏接口

| 接口 | 负责人 | 调用方 | 页面/用途 | 优先级 |
|---|---|---|---|---|
| `POST /api/routes/deviation/check` | 后端2 | 前端1/后端3 | 偏航检测 | P1 |
| `POST /api/routes/reroute-suggestion` | 后端2 | 前端1/后端3 | 纠偏建议 | P1 |
| `POST /api/vehicles/{plate}/command` | 后端3 | 前端1 | 一键下发新路线 | P0 |
| `GET /api/vehicles/{plate}/command/{commandId}` | 后端3 | 前端1 | 指令状态 | P0 |

---

## 4. 风险与 ETA 接口

| 接口 | 负责人 | 调用方 | 页面/用途 | 优先级 |
|---|---|---|---|---|
| `GET /api/orders/{orderId}/delay-prediction` | 后端2 | 前端1/前端2 | 延误预测 | P1 |
| `GET /api/orders/{orderId}/risk-score` | 后端2 | 前端1/前端2 | 运输风险评分 | P1 |
| `POST /api/orders/{orderId}/status/verify` | 后端2 | 前端2/后端3 | 状态可信度校验 | P1 |

---

## 5. 客户权限与异常摘要

| 接口 | 负责人 | 调用方 | 页面/用途 | 优先级 |
|---|---|---|---|---|
| `GET /api/orders/{orderId}/exception-summary` | 后端3 | 前端2 | 客户订单异常摘要 | P0 |
| `/api/alerts/**` 禁止 SHIPPER | 后端3 | 前端2 | 权限整改 | P0 |

---

## 6. 卸货点确认

| 接口 | 负责人 | 调用方 | 页面/用途 | 优先级 |
|---|---|---|---|---|
| `GET /api/orders/{orderId}/unload-address/suggestions` | 后端3 | 前端2 | 推荐卸货点 | P1 |
| `POST /api/orders/{orderId}/unload-address/confirm` | 后端3 | 前端2 | 确认卸货点 | P1 |
| `POST /api/orders/{orderId}/unload-address/abnormal` | 后端3 | 前端2 | 司机反馈异常 | P2 |

---

## 7. 司机评分

| 接口 | 负责人 | 调用方 | 页面/用途 | 优先级 |
|---|---|---|---|---|
| `POST /api/orders/{orderId}/driver-rating` | 后端1 | 前端2 | 客户评价司机 | P1 |
| `GET /api/orders/{orderId}/driver-rating` | 后端1 | 前端2 | 判断是否已评价 | P1 |
| `GET /api/drivers/{driverId}/rating-summary` | 后端1 | 前端2 | 司机评分汇总 | P1 |
| `GET /api/drivers/{driverId}/ratings` | 后端1 | 前端2 | 司机评价列表 | P2 |

---

## 8. 文件上传与问答

| 接口 | 负责人 | 调用方 | 页面/用途 | 优先级 |
|---|---|---|---|---|
| `POST /api/files/upload` | 后端3 | 前端2 | 头像/车辆图/异常图上传 | P1 |
| `POST /api/assistant/chat` | 后端3 | 前端2 | 大模型问答 | P2 |

---

## 9. 每日联调检查

```txt
[ ] 客户登录后没有告警中心
[ ] 客户订单详情能看到异常摘要
[ ] 改址地址输入提示能用
[ ] 选中新地址后能返回额外距离/延误/费用
[ ] 客户能提交改址申请
[ ] 调度员能审核通过/拒绝
[ ] 通过后前端地图能刷新新路线
[ ] 司机评分能提交
[ ] 司机评分汇总能展示
[ ] 高德 API Key 没有暴露在前端 Web 服务调用里
```
