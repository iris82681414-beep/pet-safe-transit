# 后端 2：高德路线规划、ETA、风险评分与轨迹纠偏

> 你的定位：路线与算法负责人。  
> 你负责把高德 API 真正接进系统，所有“距离、时间、路线、偏航、轨迹纠偏、延误预测”都归你。

---

## 1. 你负责什么

```txt
1. 封装 AmapService
2. 高德地理编码、逆地理编码
3. 高德驾车路线规划
4. 高德货车路线规划
5. 改址影响计算
6. ETA 重新计算
7. 延误预测
8. 运输风险评分
9. 偏航检测与纠偏建议
10. 轨迹纠偏
```

---

## 2. 你不负责什么

```txt
1. 不负责客户提交改址申请的入库
2. 不负责调度审核按钮和状态流转
3. 不负责前端地图画线
4. 不负责文件上传
5. 不负责用户菜单权限
```

---

## 3. 高德服务封装

### 3.1 配置项

`application.yml`：

```yaml
amap:
  web-key: ${AMAP_WEB_KEY}
  base-url: https://restapi.amap.com
  connect-timeout-ms: 3000
  read-timeout-ms: 5000
  cache-minutes: 30
```

环境变量：

```bash
export AMAP_WEB_KEY="你的高德Web服务Key"
```

---

### 3.2 AmapService 接口

```java
public interface AmapService {
    GeoPoint geocode(String address, String city);
    AddressInfo regeo(double lng, double lat);
    List<InputTipDTO> inputTips(String keywords, String city);
    RoutePlanDTO planDrivingRoute(RoutePlanRequest request);
    RoutePlanDTO planTruckRoute(TruckRoutePlanRequest request);
    List<GeoPoint> correctTrajectory(List<GpsPointDTO> points);
    List<PoiDTO> searchPoi(String keywords, String city, Double lng, Double lat);
}
```

---

## 4. 你要提供的内部接口

### 4.1 地址输入提示代理

```http
GET /api/amap/input-tips?keywords=未来科技城&city=杭州
```

Response：

```json
{
  "code": 0,
  "data": [
    {
      "name": "未来科技城海创园",
      "district": "浙江省杭州市余杭区",
      "address": "文一西路998号",
      "lng": 120.0165,
      "lat": 30.2798
    }
  ]
}
```

说明：前端可以直接用高德 JS API 输入提示，也可以调这个后端代理。为了统一验收，建议保留该接口。

---

### 4.2 地理编码

```http
POST /api/amap/geocode
```

Request：

```json
{
  "address": "杭州市余杭区未来科技城海创园",
  "city": "杭州"
}
```

Response：

```json
{
  "code": 0,
  "data": {
    "formattedAddress": "浙江省杭州市余杭区未来科技城海创园",
    "province": "浙江省",
    "city": "杭州市",
    "district": "余杭区",
    "lng": 120.0165,
    "lat": 30.2798,
    "level": "兴趣点"
  }
}
```

---

### 4.3 逆地理编码

```http
POST /api/amap/regeo
```

Request：

```json
{
  "lng": 120.5738,
  "lat": 30.4219
}
```

Response：

```json
{
  "code": 0,
  "data": {
    "formattedAddress": "浙江省嘉兴市海宁市G320国道附近",
    "province": "浙江省",
    "city": "嘉兴市",
    "district": "海宁市",
    "road": "G320国道",
    "poiName": "海宁服务区附近"
  }
}
```

---

### 4.4 普通路线规划

```http
POST /api/routes/plan
```

Request：

```json
{
  "origin": { "lng": 121.4737, "lat": 31.2304 },
  "destination": { "lng": 120.1551, "lat": 30.2741 },
  "strategy": "FASTEST",
  "plate": "沪A·C0291",
  "waypoints": [
    { "lng": 120.7555, "lat": 30.7522 }
  ]
}
```

Response：

```json
{
  "code": 0,
  "data": {
    "distanceKm": 190.4,
    "durationMinutes": 172,
    "tollCost": 86,
    "trafficLights": 18,
    "polyline": [
      { "lng": 121.4737, "lat": 31.2304 },
      { "lng": 120.7555, "lat": 30.7522 },
      { "lng": 120.1551, "lat": 30.2741 }
    ],
    "steps": [
      {
        "instruction": "沿G60沪昆高速行驶",
        "distanceKm": 80.2,
        "durationMinutes": 66
      }
    ]
  }
}
```

---

### 4.5 货车路线规划

```http
POST /api/routes/truck-plan
```

Request：

```json
{
  "origin": { "lng": 121.4737, "lat": 31.2304 },
  "destination": { "lng": 120.1551, "lat": 30.2741 },
  "truck": {
    "plate": "沪A·C0291",
    "size": "MEDIUM",
    "heightMeters": 3.2,
    "widthMeters": 2.4,
    "loadWeightTons": 2.5,
    "totalWeightTons": 7.5,
    "axis": 2
  }
}
```

Response：

```json
{
  "code": 0,
  "data": {
    "routeType": "TRUCK",
    "distanceKm": 196.8,
    "durationMinutes": 188,
    "restrictionWarnings": [
      "已避开限高路段",
      "已避开货车限行区域"
    ],
    "polyline": []
  }
}
```

---

### 4.6 改址影响计算

```http
POST /api/orders/{orderId}/address-change-impact
```

Request：

```json
{
  "newAddress": {
    "detail": "未来科技城海创园 3 号楼东门",
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
    "currentOrderStatus": "IN_TRANSIT",
    "canChange": true,
    "impactLevel": "MEDIUM",
    "oldRouteDistanceKm": 190.4,
    "newRouteDistanceKm": 202.8,
    "extraDistanceKm": 12.4,
    "estimatedDelayMinutes": 28,
    "extraCost": 35.00,
    "isNearCurrentRoute": true,
    "distanceFromCurrentRouteKm": 2.1,
    "isOutOfServiceArea": false,
    "needDispatcherReview": true,
    "needDriverConfirm": true,
    "reason": "新地址距离原路线 2.1km，未超出配送范围"
  }
}
```

影响等级规则：

```txt
微改址：extraDistanceKm <= 1 且 delay <= 5，自动通过
近距离改址：extraDistanceKm <= 5 且 delay <= 15，需要司机确认
中等影响：extraDistanceKm <= 20 或 delay <= 45，需要调度员审核
跨区改址：超出配送城市或 extraDistanceKm > 20，需要重新派单/转运
异常地址：高德无法解析、坐标不可信、费用变化大，需要人工确认
```

---

### 4.7 ETA 延误预测

```http
GET /api/orders/{orderId}/delay-prediction
```

Response：

```json
{
  "code": 0,
  "data": {
    "orderId": "SH-HZ-20260629-0291",
    "plannedArriveAt": "2026-07-01T18:00:00Z",
    "estimatedArriveAt": "2026-07-01T18:28:00Z",
    "delayStatus": "POSSIBLE_DELAY",
    "delayMinutes": 28,
    "reasons": [
      "改址导致路线增加 12.4km",
      "车辆过去 30 分钟平均速度偏低",
      "曾发生 1 次偏航告警"
    ]
  }
}
```

---

### 4.8 运输风险评分

```http
GET /api/orders/{orderId}/risk-score
```

Response：

```json
{
  "code": 0,
  "data": {
    "orderId": "SH-HZ-20260629-0291",
    "score": 72,
    "level": "MEDIUM",
    "factors": [
      { "name": "偏航次数", "value": 1, "impact": 25 },
      { "name": "异常停留时长", "value": "18分钟", "impact": 20 },
      { "name": "设备离线", "value": "无", "impact": 0 },
      { "name": "预计延误", "value": "可能延误 28 分钟", "impact": 27 }
    ],
    "suggestion": "建议调度员优先关注该订单，并确认司机当前路线"
  }
}
```

评分规则建议：

```txt
基础分 0
偏航一次 +25
异常停车超过15分钟 +20
设备离线 +30
预计延误超过15分钟 +20
改址影响中等 +15
改址影响高 +30
最终分数：0~100
0~39 LOW，40~69 MEDIUM，70~100 HIGH
```

---

### 4.9 货物状态可信度

```http
POST /api/orders/{orderId}/status/verify
```

Request：

```json
{
  "reportedStatus": "DELIVERED",
  "reportLocation": {
    "lng": 120.5738,
    "lat": 30.4219
  }
}
```

Response：

```json
{
  "code": 0,
  "data": {
    "credible": false,
    "confidence": 0.42,
    "level": "LOW",
    "distanceToDestinationKm": 8.6,
    "reason": "司机上报已送达，但当前位置距离目的地 8.6km",
    "suggestion": "建议调度员联系司机确认"
  }
}
```

---

### 4.10 偏航检测

```http
POST /api/routes/deviation/check
```

Request：

```json
{
  "orderId": "SH-HZ-20260629-0291",
  "plate": "沪A·C0291",
  "currentPoint": { "lng": 120.5738, "lat": 30.4219 }
}
```

Response：

```json
{
  "code": 0,
  "data": {
    "deviated": true,
    "distanceFromRouteKm": 3.2,
    "durationMinutes": 17,
    "level": "WARNING",
    "suggestion": "建议调度员确认是否绕行，如非计划路线可一键下发纠偏路线"
  }
}
```

---

### 4.11 一键纠偏路线建议

```http
POST /api/routes/reroute-suggestion
```

Request：

```json
{
  "orderId": "SH-HZ-20260629-0291",
  "currentPoint": { "lng": 120.5738, "lat": 30.4219 }
}
```

Response：

```json
{
  "code": 0,
  "data": {
    "suggestion": "从当前位置重新规划到杭州余杭物流中心，预计增加 18 分钟",
    "distanceKm": 64.8,
    "durationMinutes": 62,
    "polyline": [],
    "commandPayload": {
      "type": "REROUTE",
      "route": "新规划路线",
      "reason": "偏航后重新规划路线"
    }
  }
}
```

---

### 4.12 轨迹纠偏

```http
POST /api/trajectory/correct
```

Request：

```json
{
  "points": [
    { "lng": 121.4737, "lat": 31.2304, "timestamp": "2026-07-01T08:00:00Z" },
    { "lng": 120.7555, "lat": 30.7522, "timestamp": "2026-07-01T09:10:00Z" }
  ]
}
```

Response：

```json
{
  "code": 0,
  "data": {
    "rawCount": 2,
    "correctedCount": 2,
    "correctedPoints": [
      { "lng": 121.4740, "lat": 31.2301 },
      { "lng": 120.7552, "lat": 30.7525 }
    ]
  }
}
```

---

## 5. 缓存要求

高德 API 有调用次数限制，必须加缓存。

```txt
1. geocode 结果缓存 24 小时
2. regeo 结果缓存 30 分钟
3. route plan 结果缓存 5~30 分钟，key = origin + destination + strategy
4. input tips 可以不缓存，也可以缓存 5 分钟
5. trajectory correct 不缓存或只缓存当前订单
```

Redis Key 示例：

```txt
amap:geocode:{city}:{addressMd5}
amap:regeo:{lng}:{lat}
amap:route:{origin}:{destination}:{strategy}
amap:truck-route:{origin}:{destination}:{truckHash}
```

---

## 6. 和其他人的对接

| 对接人 | 你给对方什么 | 你需要对方什么 |
|---|---|---|
| 后端 1 | 改址影响结果、风险评分 | 订单旧地址、新地址、订单状态 |
| 后端 3 | 纠偏路线、调度 payload、风险等级 | 审核结果、告警/调度数据 |
| 前端 1 | route polyline、ETA、纠偏轨迹 | 地图展示效果反馈 |
| 前端 2 | 地址解析结果、影响等级、风险评分 | 改址弹窗字段 |

---

## 7. 2 天完成计划

### Day 1

```txt
上午：封装 AmapService，跑通 geocode、regeo
下午：跑通 routes/plan、address-change-impact
晚上：联调前端地址选点和影响计算
```

### Day 2

```txt
上午：完成 delay-prediction、risk-score
下午：完成 deviation/check、reroute-suggestion
晚上：完成 trajectory/correct 或保留兜底算法
```

---

## 8. 完成标准

```txt
1. 地址能解析成真实经纬度
2. 新地址能算出额外距离和预计延误
3. 路线 polyline 能给前端画出来
4. 偏航后能生成纠偏路线建议
5. 风险评分能解释原因
6. 高德 API 失败时有兜底，不让页面崩
```
