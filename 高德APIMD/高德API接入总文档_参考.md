# 高德 API 接入分工文档

> 项目：智慧物流平台  
> 当前部署地址：`http://123.249.38.178`  
> 目标：把规划路线、地址解析、地址修改影响计算、卸货点确认、偏航纠偏等能调用高德 API 的地方直接接入高德，减少自己写复杂算法。

---

## 1. 总结结论

可以直接接高德。

你们现在最值得接入的能力有 6 类：

| 能力 | 用高德哪个服务 | 用在项目哪里 | 建议调用位置 |
|---|---|---|---|
| 地图展示 | 高德 JS API 2.0 | 货物追踪页、车辆调度页、地址选点弹窗 | 前端 |
| 地址输入提示 | 输入提示 API / JS API 输入提示插件 | 客户修改收货地址、调度确认卸货地址 | 前端展示 + 后端二次校验 |
| 地址转经纬度 | 地理编码 API | 把客户输入的新地址转成坐标 | 后端 |
| 经纬度转地址 | 逆地理编码 API | GPS 点显示具体地址、司机当前位置说明 | 后端 |
| 驾车路线规划 | 路径规划 2.0 驾车路线规划 | 普通路线规划、改址影响、ETA、路线重算 | 后端 |
| 货车路线规划 | 货车路径规划 API | 有车辆长宽高重、限行约束时使用 | 后端 |
| 轨迹纠偏 | 轨迹纠偏 API | GPS 漂移修正、轨迹回放更贴路 | 后端 |
| POI 搜索 | 搜索 POI / 搜索 POI 2.0 | 卸货点建议、地图选点、园区/仓库搜索 | 后端或前端 |

### 关键原则

1. **前端只负责地图展示、画线、选点和交互。**
2. **路线规划、地理编码、货车规划、轨迹纠偏这些 Web 服务 API 尽量由后端调用。**
3. **高德 Web 服务 Key 不要写死在前端代码里。**
4. **后端统一封装 `AmapService`，前端不要直接调用一堆高德 Web API。**
5. **所有高德返回结果都转成你们自己的 DTO，避免前端依赖高德原始字段。**

---

## 2. 要接入的项目功能

### 2.1 客户修改收货地址

客户在订单详情页点击“申请修改地址”。

流程：

```txt
客户输入新地址
→ 前端调用输入提示，选择地址
→ 后端地理编码确认坐标
→ 后端调用高德路线规划
→ 计算新旧地址距离、车辆到新地址距离、预计延误、是否超区、额外费用
→ 返回改址影响
→ 客户提交申请
→ 调度员审核
→ 审核通过后重新规划路线并下发给司机
```

内部接口：

```http
GET  /api/amap/input-tips?keywords=xxx&city=杭州
POST /api/amap/geocode
POST /api/orders/{orderId}/address-change-impact
POST /api/orders/{orderId}/address-change-requests
GET  /api/orders/{orderId}/address-change-history
```

---

### 2.2 调度员确认卸货地址

用于解决客户填写地址模糊、卸货点不准、园区多个门口的问题。

流程：

```txt
订单目的地地址
→ 后端调用 POI 搜索 / 地理编码
→ 结合历史卸货点
→ 给调度员推荐 2~5 个候选卸货点
→ 调度员确认最终卸货点
→ 系统重新规划路线
→ 推送给司机
```

内部接口：

```http
GET  /api/orders/{orderId}/unload-address/suggestions
POST /api/orders/{orderId}/unload-address/confirm
POST /api/orders/{orderId}/unload-address/abnormal
```

---

### 2.3 路线重算与 ETA 延误预测

用于运输途中改址、偏航、调度重新规划路线。

流程：

```txt
车辆当前位置 + 目的地
→ 调用高德驾车路线规划 / 货车路径规划
→ 获取距离 distance、时长 duration、路线 polyline
→ 计算预计到达时间 ETA
→ 判断是否延误
→ 前端画出新路线
```

内部接口：

```http
POST /api/routes/plan
POST /api/routes/replan
GET  /api/cargo/{cargoId}/eta
GET  /api/orders/{orderId}/delay-prediction
```

---

### 2.4 偏航检测与一键纠偏

高德可以帮你们算路，但“是否偏航”建议你们自己在后端根据 GPS 点和规划路线做判断。

流程：

```txt
后端保存规划路线 polyline
→ GPS 实时上报
→ 判断车辆当前位置到规划路线的最近距离
→ 超过阈值，例如 3km 且持续 10 分钟
→ 生成偏航告警
→ 调用高德路线规划，计算从当前位置回到目的地的新路线
→ 调度员一键下发 REROUTE 指令
```

内部接口：

```http
POST /api/routes/deviation/check
POST /api/routes/reroute-suggestion
POST /api/vehicles/{plate}/command
```

---

### 2.5 轨迹纠偏

用于 GPS 漂移修正和轨迹回放。

流程：

```txt
GPS 原始点
→ 调用高德轨迹纠偏 API
→ 返回贴合道路后的轨迹点
→ 前端同时展示原始轨迹和纠偏轨迹
```

内部接口：

```http
POST /api/trajectory/correct
GET  /api/cargo/{cargoId}/trajectory/raw
GET  /api/cargo/{cargoId}/trajectory/corrected
```

---

## 3. 高德 API 封装建议

### 3.1 后端环境变量

后端 `.env` 或 `application.yml`：

```yaml
amap:
  web-key: ${AMAP_WEB_KEY}
  js-key: ${AMAP_JS_KEY}
  security-js-code: ${AMAP_SECURITY_JS_CODE}
  base-url: https://restapi.amap.com
  connect-timeout-ms: 3000
  read-timeout-ms: 5000
```

服务器环境变量：

```bash
export AMAP_WEB_KEY="你的Web服务Key"
export AMAP_JS_KEY="你的JS API Key"
export AMAP_SECURITY_JS_CODE="你的安全密钥"
```

### 3.2 后端 AmapService 方法

建议统一建一个服务类：

```java
public interface AmapService {
    GeoPoint geocode(String address, String city);
    AddressInfo regeo(double lng, double lat);
    List<InputTip> inputTips(String keywords, String city);
    RoutePlanResult planDrivingRoute(RoutePlanRequest request);
    RoutePlanResult planTruckRoute(TruckRoutePlanRequest request);
    List<GeoPoint> correctTrajectory(List<GpsPoint> points);
    List<PoiResult> searchPoi(String keywords, String city, double lng, double lat);
}
```

### 3.3 坐标格式统一

高德经纬度顺序是：

```txt
经度,纬度
lng,lat
```

你们系统内部 DTO 也统一用：

```json
{
  "lng": 120.1551,
  "lat": 30.2741
}
```

不要一会儿 `lat,lng`，一会儿 `lng,lat`。

---

## 4. 新增后端接口定义

### 4.1 地址输入提示

```http
GET /api/amap/input-tips?keywords=未来科技城&city=杭州
```

Response：

```json
{
  "code": 0,
  "data": {
    "tips": [
      {
        "name": "未来科技城海创园",
        "district": "浙江省杭州市余杭区",
        "address": "文一西路",
        "lng": 120.0165,
        "lat": 30.2798,
        "adcode": "330110"
      }
    ]
  }
}
```

---

### 4.2 地址解析

```http
POST /api/amap/geocode
```

Request：

```json
{
  "address": "浙江省杭州市余杭区未来科技城海创园3号楼东门",
  "city": "杭州"
}
```

Response：

```json
{
  "code": 0,
  "data": {
    "formattedAddress": "浙江省杭州市余杭区未来科技城海创园3号楼东门",
    "lng": 120.0165,
    "lat": 30.2798,
    "level": "门牌号",
    "adcode": "330110"
  }
}
```

---

### 4.3 路线规划

```http
POST /api/routes/plan
```

Request：

```json
{
  "mode": "TRUCK",
  "origin": {
    "lng": 121.4737,
    "lat": 31.2304
  },
  "destination": {
    "lng": 120.1551,
    "lat": 30.2741
  },
  "waypoints": [
    {
      "lng": 120.7555,
      "lat": 30.7522
    }
  ],
  "strategy": "RECOMMEND",
  "vehicle": {
    "plate": "沪A-C0291",
    "width": 2.5,
    "height": 3.2,
    "weight": 10,
    "load": 5,
    "axis": 2
  }
}
```

Response：

```json
{
  "code": 0,
  "data": {
    "routeId": "ROUTE-20260705-001",
    "distanceMeters": 190500,
    "durationSeconds": 10800,
    "tolls": 86,
    "trafficLights": 23,
    "restriction": "NO_RESTRICTION",
    "polyline": [
      { "lng": 121.4737, "lat": 31.2304 },
      { "lng": 120.7555, "lat": 30.7522 },
      { "lng": 120.1551, "lat": 30.2741 }
    ],
    "steps": [
      {
        "instruction": "沿G60沪昆高速向西行驶",
        "roadName": "G60沪昆高速",
        "distanceMeters": 85000,
        "durationSeconds": 4200
      }
    ]
  }
}
```

---

### 4.4 改址影响计算

```http
POST /api/orders/{orderId}/address-change-impact
```

Request：

```json
{
  "newAddress": {
    "province": "浙江省",
    "city": "杭州市",
    "district": "余杭区",
    "detail": "未来科技城海创园3号楼东门",
    "lng": 120.0165,
    "lat": 30.2798
  }
}
```

Response：

```json
{
  "code": 0,
  "data": {
    "orderId": "SH-HZ-20260629-0291",
    "canChange": true,
    "impactLevel": "MEDIUM",
    "oldDistanceMeters": 190500,
    "newDistanceMeters": 202900,
    "extraDistanceKm": 12.4,
    "estimatedDelayMinutes": 28,
    "extraCost": 35.00,
    "isNearCurrentRoute": true,
    "isOutOfServiceArea": false,
    "needDispatcherReview": true,
    "needDriverConfirm": true,
    "reason": "新地址距离原路线2.1km，仍在配送范围内，但预计增加28分钟"
  }
}
```

---

### 4.5 卸货地址建议

```http
GET /api/orders/{orderId}/unload-address/suggestions
```

Response：

```json
{
  "code": 0,
  "data": {
    "orderId": "SH-HZ-20260629-0291",
    "currentAddress": {
      "detail": "杭州余杭物流中心",
      "lng": 120.1551,
      "lat": 30.2741
    },
    "suggestions": [
      {
        "source": "HISTORY",
        "address": "杭州余杭物流中心2号卸货口",
        "lng": 120.1547,
        "lat": 30.2748,
        "confidence": 0.93,
        "reason": "历史订单中8次使用该卸货点"
      },
      {
        "source": "AMAP_POI",
        "address": "杭州余杭物流中心东门",
        "lng": 120.1561,
        "lat": 30.2752,
        "confidence": 0.76,
        "reason": "高德POI搜索匹配结果"
      }
    ]
  }
}
```

---

## 5. 五人分工

## 后端 1：订单地址、改址申请、司机评分

### 负责范围

1. 收货地址修改申请表。
2. 收货地址修改记录表。
3. 客户订单权限校验。
4. 司机评分功能。
5. 地址修改状态流转。

### 要做的接口

```http
POST /api/orders/{orderId}/address-change-requests
GET  /api/orders/{orderId}/address-change-history
GET  /api/address-change-requests/{requestId}
POST /api/orders/{orderId}/driver-rating
GET  /api/orders/{orderId}/driver-rating
GET  /api/drivers/{driverId}/rating-summary
GET  /api/drivers/{driverId}/ratings
```

### 要建的表

```sql
address_change_requests
address_change_logs
driver_ratings
driver_rating_tags
```

### 你不负责

```txt
不直接调用高德路线规划。
不写地图页面。
不写调度审核页面。
```

### 今天必须完成

```txt
1. 建 address_change_requests 表。
2. 建 driver_ratings 表。
3. 完成提交改址申请接口。
4. 完成客户提交司机评分接口。
5. 改址申请中保存高德返回的 lng、lat、extraDistanceKm、estimatedDelayMinutes。
```

### 验收标准

```txt
客户能提交改址申请。
数据库能看到新地址坐标、修改原因、联系人、审核状态。
客户能在已签收订单给司机评分。
一个订单不能重复评分。
```

---

## 后端 2：高德 API 核心封装、路线规划、ETA、改址影响

### 负责范围

你是高德 API 主负责人。

1. 封装 AmapService。
2. 地理编码、逆地理编码。
3. 驾车路线规划。
4. 货车路径规划。
5. 改址影响计算。
6. ETA 延误预测。
7. 偏航距离计算。
8. 轨迹纠偏。

### 要做的接口

```http
GET  /api/amap/input-tips
POST /api/amap/geocode
POST /api/amap/regeo
POST /api/routes/plan
POST /api/routes/replan
POST /api/routes/deviation/check
POST /api/routes/reroute-suggestion
POST /api/orders/{orderId}/address-change-impact
GET  /api/orders/{orderId}/delay-prediction
POST /api/trajectory/correct
```

### 要写的 Service

```java
AmapService
RoutePlanService
AddressImpactService
DeviationCheckService
TrajectoryCorrectService
```

### 今天必须完成

```txt
1. 在后端配置 AMAP_WEB_KEY。
2. 写 AmapService 基础请求方法。
3. 跑通 geocode：地址 → 经纬度。
4. 跑通 route plan：起点终点 → distance、duration、polyline。
5. 完成 /api/orders/{orderId}/address-change-impact。
```

### 明天必须完成

```txt
1. 接货车路径规划。
2. 接轨迹纠偏。
3. 完成偏航检测接口。
4. 完成 ETA 延误预测接口。
```

### 验收标准

```txt
输入一个新收货地址，能返回经纬度。
输入车辆当前位置和目的地，能返回规划路线、距离、时长。
地址修改影响能算出额外距离、预计延误和影响等级。
前端地图能画出后端返回的 polyline。
```

---

## 后端 3：审核流程、调度联动、权限、部署配置

### 负责范围

1. 客户删除告警中心权限。
2. 调度员审核改址。
3. 司机确认新地址。
4. 改址通过后生成调度指令。
5. 卸货地址确认。
6. 高德 API 生产环境配置。
7. 文件上传到云端。
8. 大模型 API 接入。

### 要做的接口

```http
GET  /api/orders/{orderId}/exception-summary
POST /api/address-change-requests/{requestId}/approve
POST /api/address-change-requests/{requestId}/reject
POST /api/address-change-requests/{requestId}/driver-confirm
GET  /api/orders/{orderId}/unload-address/suggestions
POST /api/orders/{orderId}/unload-address/confirm
POST /api/orders/{orderId}/unload-address/abnormal
POST /api/files/upload
POST /api/assistant/chat
```

### 今天必须完成

```txt
1. SHIPPER 禁止访问 /alerts。
2. 客户端菜单删除告警中心入口。
3. 完成调度员审核改址接口。
4. 审核通过后调用后端2的路线重算结果，生成 REROUTE 调度指令。
5. 部署服务器加 AMAP_WEB_KEY 环境变量。
```

### 验收标准

```txt
客户看不到告警中心。
客户只能看到订单异常摘要。
调度员能审核改址申请。
审核通过后，司机端/调度指令能看到新地址和新路线。
```

---

## 前端 1：客户订单详情、地图、改址申请、路线展示

### 负责范围

1. 客户订单详情页。
2. 收货地址修改弹窗。
3. 高德 JS API 地图加载。
4. 地图选点。
5. 地址输入提示。
6. 改址影响预览。
7. 车辆路线 polyline 展示。
8. 地图加载小车动画补回。

### 要接的接口

```http
GET  /api/orders/{orderId}
GET  /api/amap/input-tips
POST /api/orders/{orderId}/address-change-impact
POST /api/orders/{orderId}/address-change-requests
GET  /api/orders/{orderId}/address-change-history
GET  /api/cargo/{cargoId}/position
GET  /api/cargo/{cargoId}/trajectory/corrected
```

### 页面改动

```txt
1. 订单详情页新增“申请修改收货地址”按钮。
2. 弹窗里有地址输入框、地图选点、新联系人、新电话、修改原因。
3. 选择地址后，地图自动定位并打点。
4. 点击“计算影响”，显示额外距离、预计延误、是否需审核、预计费用。
5. 提交后显示申请状态。
6. 货物追踪地图支持展示原路线和新路线。
7. 地图加载时显示小车 loading 动画。
```

### 今天必须完成

```txt
1. 接入高德 JS API Key。
2. 客户订单详情页加载地图。
3. 改址弹窗 UI。
4. 地址输入提示下拉框。
5. 调用 address-change-impact 显示影响结果。
```

### 验收标准

```txt
客户能在地图上选择新地址。
选择后能看到新地址 marker。
点击计算影响后，页面展示额外距离和预计延误。
能提交改址申请。
```

---

## 前端 2：调度审核、卸货地址确认、司机评分、权限收尾

### 负责范围

1. 调度员改址审核列表。
2. 改址详情页。
3. 调度员确认卸货地址。
4. 客户司机评分页面。
5. 司机评分汇总展示。
6. 删除客户告警中心菜单。
7. 登录页演示角色删除。
8. 运营总览页布局调整。

### 要接的接口

```http
GET  /api/address-change-requests
GET  /api/address-change-requests/{requestId}
POST /api/address-change-requests/{requestId}/approve
POST /api/address-change-requests/{requestId}/reject
POST /api/address-change-requests/{requestId}/driver-confirm
GET  /api/orders/{orderId}/unload-address/suggestions
POST /api/orders/{orderId}/unload-address/confirm
POST /api/orders/{orderId}/driver-rating
GET  /api/drivers/{driverId}/rating-summary
GET  /api/orders/{orderId}/exception-summary
```

### 页面改动

```txt
1. 调度端新增“改址审核”菜单。
2. 审核详情展示旧地址、新地址、地图、路线影响、预计延误、额外费用。
3. 按钮：通过、拒绝、联系司机确认。
4. 卸货地址确认页展示历史卸货点和高德 POI 建议。
5. 客户订单完成后展示“评价司机”入口。
6. 客户角色删除告警中心菜单。
7. 登录页去掉演示角色说明。
8. 运营总览两列火车图保留一列。
```

### 今天必须完成

```txt
1. 客户端删除告警中心菜单。
2. 登录页去掉演示角色说明。
3. 改址审核列表页面。
4. 改址审核详情页面。
5. 司机评分弹窗。
```

### 验收标准

```txt
调度员能看到客户提交的改址申请。
调度员能通过或拒绝申请。
客户不能进入告警中心。
客户能给已完成订单的司机评分。
```

---

## 6. 两天落地计划

## Day 1：先把高德打通，主流程能走

| 人员 | 今日任务 | 晚上验收 |
|---|---|---|
| 后端1 | 建改址申请、司机评分表；写提交申请接口 | 数据库能保存改址申请和司机评分 |
| 后端2 | 封装 AmapService；跑通 geocode 和 route plan | 输入地址能得到坐标，输入起终点能得到路线 |
| 后端3 | 权限整改；调度审核接口；服务器配置 Key | 客户看不到告警中心，调度员能审核 |
| 前端1 | 高德地图接入；改址弹窗；输入提示 | 客户能选新地址并计算影响 |
| 前端2 | 改址审核页面；司机评分弹窗；菜单权限 | 调度端能看到申请，客户能评分 |

## Day 2：做完整闭环和演示效果

| 人员 | 今日任务 | 晚上验收 |
|---|---|---|
| 后端1 | 改址历史、评分汇总、权限校验 | 客户只能操作自己的订单 |
| 后端2 | 货车路线规划、偏航检测、轨迹纠偏 | 路线能按货车参数规划，轨迹能纠偏 |
| 后端3 | 审核通过后生成调度指令；卸货地址建议；部署 | 通过改址后，司机/调度能看到新路线 |
| 前端1 | 新旧路线对比、地图画线、小车 loading | 地图上能看到旧路线、新路线、车辆点 |
| 前端2 | 卸货地址确认、评分汇总、UI 收尾 | 调度可确认卸货点，司机评分可展示 |

---

## 7. 最终演示流程

```txt
1. 客户登录。
2. 打开订单详情。
3. 点击“申请修改收货地址”。
4. 输入新地址，高德输入提示出现候选地址。
5. 选择地址，地图自动打点。
6. 点击“计算影响”。
7. 系统显示：额外距离、预计延误、额外费用、是否需要调度审核。
8. 客户提交申请。
9. 调度员登录。
10. 打开“改址审核”。
11. 查看旧地址、新地址、新路线和影响等级。
12. 调度员审核通过。
13. 系统重新规划路线并生成 REROUTE 调度指令。
14. 司机确认新地址。
15. 客户订单完成后给司机评分。
16. 调度端查看司机评分汇总。
```

---

## 8. 最小验收清单

### 高德 API

```txt
[ ] 地址输入提示可用
[ ] 地址转经纬度可用
[ ] 经纬度转地址可用
[ ] 驾车路线规划可用
[ ] 货车路线规划可用
[ ] 返回路线 polyline 能在地图上画出来
[ ] 高德 Key 没有明文写在 GitHub 仓库里
```

### 改址流程

```txt
[ ] 客户能提交改址申请
[ ] 系统能计算改址影响
[ ] 调度员能审核通过/拒绝
[ ] 审核通过后能重新规划路线
[ ] 改址记录可查询
```

### 权限整改

```txt
[ ] 客户菜单没有告警中心
[ ] 客户访问 /alerts 会被拦截
[ ] 客户只能查看自己的订单异常摘要
[ ] 调度员和管理员仍然能看告警中心
```

### 司机评分

```txt
[ ] 订单完成后客户能评分
[ ] 一个订单只能评价一次
[ ] 能查看司机平均分
[ ] 能查看评分标签统计
```

---

## 9. 注意事项

1. 高德 Web 服务 API 的 Key 不要放前端。
2. 前端 JS API Key 可以放前端，但要配置安全密钥和域名限制。
3. 路线规划返回的坐标一般是高德坐标系，GPS 原始坐标如果是 WGS84，需要先做坐标转换。
4. 货车路径规划优先用于真实物流场景；普通驾车路线规划可作为降级方案。
5. 如果高德接口失败，后端要返回兜底结果，不要让前端页面崩。
6. 你们部署地址是 `123.249.38.178`，上线时要确认高德控制台是否配置了允许域名/IP。
7. 高德接口可能有调用额度限制，开发阶段不要每秒疯狂调用路线规划；路线结果要做 Redis 缓存。

---

## 10. 推荐缓存策略

| 数据 | 缓存 Key | 缓存时间 |
|---|---|---|
| 地址解析 | `amap:geocode:{city}:{address}` | 7 天 |
| 逆地理编码 | `amap:regeo:{lng}:{lat}` | 1 天 |
| 路线规划 | `amap:route:{origin}:{destination}:{strategy}` | 30 分钟 |
| 输入提示 | `amap:tips:{city}:{keywords}` | 10 分钟 |
| POI 搜索 | `amap:poi:{city}:{keywords}` | 1 天 |

---

## 11. 推荐 Git 分支

```txt
feature/amap-route-backend2
feature/address-change-backend1
feature/address-review-backend3
feature/amap-map-frontend1
feature/address-review-frontend2
```

提交格式：

```txt
backend2: 接入高德驾车路线规划
frontend1: 完成改址地图选点弹窗
backend3: 完成调度员改址审核接口
```

