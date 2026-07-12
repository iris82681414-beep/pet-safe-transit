package com.sky.logistics.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.logistics.common.PageResponse;
import com.sky.logistics.dto.AddressChangeApproveDTO;
import com.sky.logistics.dto.AddressChangeCreateDTO;
import com.sky.logistics.dto.AddressChangeImpactDTO;
import com.sky.logistics.dto.AddressChangeRejectDTO;
import com.sky.logistics.dto.AddressDTO;
import com.sky.logistics.dto.DriverConfirmDTO;
import com.sky.logistics.dto.DriverRatingCreateDTO;
import com.sky.logistics.dto.DriverRatingDimensionsDTO;
import com.sky.logistics.dto.GeoPointDTO;
import com.sky.logistics.dto.RoutePlanRequestDTO;
import com.sky.logistics.dto.StatusVerifyDTO;
import com.sky.logistics.dto.UnloadAddressAbnormalDTO;
import com.sky.logistics.dto.UnloadAddressConfirmDTO;
import com.sky.logistics.entity.AddressChangeLog;
import com.sky.logistics.entity.AddressChangeRequest;
import com.sky.logistics.entity.CargoRecord;
import com.sky.logistics.entity.DriverRating;
import com.sky.logistics.entity.DriverRatingTag;
import com.sky.logistics.entity.UnloadAddressRecord;
import com.sky.logistics.mapper.LogisticsCargoMapper;
import com.sky.logistics.mapper.OrderExtensionMapper;
import com.sky.logistics.service.AmapService;
import com.sky.logistics.service.CommandService;
import com.sky.logistics.service.LogisticsSecurityService;
import com.sky.logistics.service.OrderExtensionService;
import com.sky.logistics.service.RoutePlanService;
import com.sky.logistics.service.TrackingService;
import com.sky.logistics.vo.AddressChangeImpactVO;
import com.sky.logistics.vo.AmapInputTipVO;
import com.sky.logistics.vo.LogisticsUserContextVO;
import com.sky.logistics.vo.RoutePlanVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class OrderExtensionServiceImpl implements OrderExtensionService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final LogisticsCargoMapper cargoMapper;
    private final OrderExtensionMapper orderExtensionMapper;
    private final RoutePlanService routePlanService;
    private final AmapService amapService;
    private final TrackingService trackingService;
    private final CommandService commandService;
    private final LogisticsSecurityService securityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderExtensionServiceImpl(LogisticsCargoMapper cargoMapper,
                                     OrderExtensionMapper orderExtensionMapper,
                                     RoutePlanService routePlanService,
                                     AmapService amapService,
                                     TrackingService trackingService,
                                     CommandService commandService,
                                     LogisticsSecurityService securityService) {
        this.cargoMapper = cargoMapper;
        this.orderExtensionMapper = orderExtensionMapper;
        this.routePlanService = routePlanService;
        this.amapService = amapService;
        this.trackingService = trackingService;
        this.commandService = commandService;
        this.securityService = securityService;
    }

    @Override
    public AddressChangeImpactVO calculateAddressChangeImpact(String orderId, AddressChangeImpactDTO request) {
        CargoRecord cargo = requireCargo(orderId);
        AddressDTO newAddress = request == null ? null : request.getNewAddress();
        validateAddress(newAddress);
        ensureCargoCoordinates(cargo);

        RoutePlanVO oldRoute = plan(cargo.getOriginLng(), cargo.getOriginLat(), cargo.getDestinationLng(), cargo.getDestinationLat(), cargo.getVehiclePlate());
        RoutePlanVO newRoute = plan(cargo.getOriginLng(), cargo.getOriginLat(), newAddress.getLng(), newAddress.getLat(), cargo.getVehiclePlate());

        double oldKm = oldRoute.getDistanceKm() == null ? 0.0 : oldRoute.getDistanceKm();
        double newKm = newRoute.getDistanceKm() == null ? 0.0 : newRoute.getDistanceKm();
        double extraKm = Math.max(0.0, newKm - oldKm);

        long oldMinutes = oldRoute.getDurationMinutes() == null ? 0L : oldRoute.getDurationMinutes();
        long newMinutes = newRoute.getDurationMinutes() == null ? 0L : newRoute.getDurationMinutes();
        int delayMinutes = (int) Math.max(0L, newMinutes - oldMinutes);

        GeoPointDTO target = GeoPointDTO.builder().lng(newAddress.getLng()).lat(newAddress.getLat()).build();
        double distanceFromRoute = minDistanceToPolylineKm(target, oldRoute.getPolyline());
        boolean outOfArea = extraKm > 50 || distanceFromRoute > 30;
        boolean canChange = !("DELIVERED".equals(cargo.getStatus()) || "CANCELLED".equals(cargo.getStatus())) && !outOfArea;
        String level = impactLevel(extraKm, delayMinutes, outOfArea);

        return AddressChangeImpactVO.builder()
                .orderId(cargo.getCargoId())
                .currentOrderStatus(cargo.getStatus())
                .canChange(canChange)
                .impactLevel(level)
                .oldRouteDistanceKm(round(oldKm))
                .newRouteDistanceKm(round(newKm))
                .oldDistanceMeters(oldRoute.getDistanceMeters())
                .newDistanceMeters(newRoute.getDistanceMeters())
                .extraDistanceKm(round(extraKm))
                .estimatedDelayMinutes(delayMinutes)
                .extraCost(money(extraKm * 3))
                .isNearCurrentRoute(distanceFromRoute <= 5)
                .distanceFromCurrentRouteKm(round(distanceFromRoute))
                .isOutOfServiceArea(outOfArea)
                .needDispatcherReview("MEDIUM".equals(level) || "HIGH".equals(level) || "ABNORMAL".equals(level))
                .needDriverConfirm(extraKm > 1 || delayMinutes > 5)
                .reason(reason(level, extraKm, delayMinutes, distanceFromRoute, canChange))
                .build();
    }

    @Override
    @Transactional
    public Map<String, Object> createAddressChangeRequest(String orderId, AddressChangeCreateDTO request, String authorization) {
        if (request == null) {
            throw new IllegalArgumentException("改址申请不能为空");
        }
        CargoRecord cargo = requireCargo(orderId);
        AddressChangeImpactDTO impactRequest = new AddressChangeImpactDTO();
        impactRequest.setNewAddress(request.getNewAddress());
        AddressChangeImpactVO impact = calculateAddressChangeImpact(orderId, impactRequest);
        if (!Boolean.TRUE.equals(impact.getCanChange())) {
            throw new IllegalArgumentException(impact.getReason());
        }

        LogisticsUserContextVO user = securityService.currentOrDefault(authorization, "USR-001", "SHIPPER");
        String requestId = nextId("ADDR");
        AddressChangeRequest entity = new AddressChangeRequest();
        entity.setRequestId(requestId);
        entity.setOrderId(cargo.getCargoId());
        entity.setCustomerId(valueOrDefault(user.getUserId(), "USR-001"));
        entity.setOldAddress(cargo.getDestinationName());
        entity.setOldLng(cargo.getDestinationLng());
        entity.setOldLat(cargo.getDestinationLat());
        entity.setNewAddress(formatAddress(request.getNewAddress()));
        entity.setNewLng(request.getNewAddress().getLng());
        entity.setNewLat(request.getNewAddress().getLat());
        entity.setContactName(trimToNull(request.getContactName()));
        entity.setContactPhone(trimToNull(request.getContactPhone()));
        entity.setReason(trimToNull(request.getReason()));
        entity.setStatus(Boolean.TRUE.equals(impact.getNeedDispatcherReview()) ? "PENDING_REVIEW" : "AUTO_APPROVED");
        entity.setImpactLevel(impact.getImpactLevel());
        entity.setExtraDistanceKm(money(impact.getExtraDistanceKm()));
        entity.setEstimatedDelayMinutes(impact.getEstimatedDelayMinutes());
        entity.setExtraCost(impact.getExtraCost());
        entity.setNeedDispatcherReview(impact.getNeedDispatcherReview());
        entity.setNeedDriverConfirm(impact.getNeedDriverConfirm());
        orderExtensionMapper.insertAddressChange(entity);
        insertAddressLog(requestId, user, "CREATE_REQUEST", "客户提交改址申请");
        insertAddressLog(requestId, user, "CALCULATE_IMPACT", impact.getReason());

        Map<String, Object> result = addressChangeSummary(entity);
        result.put("message", Boolean.TRUE.equals(impact.getNeedDispatcherReview()) ? "改址会影响路线，需调度员审核" : "改址影响较小，已自动通过");
        return result;
    }

    @Override
    public Map<String, Object> addressChangeHistory(String orderId) {
        requireCargo(orderId);
        List<AddressChangeRequest> requests = orderExtensionMapper.findAddressChangesByOrderId(orderId);
        List<Map<String, Object>> content = new ArrayList<>();
        for (AddressChangeRequest request : requests) {
            Map<String, Object> item = addressChangeSummary(request);
            item.put("logs", addressLogs(request.getRequestId()));
            content.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("requests", content);
        return result;
    }

    @Override
    public Map<String, Object> addressChangeDetail(String requestId) {
        AddressChangeRequest request = requireAddressChange(requestId);
        Map<String, Object> detail = addressChangeSummary(request);
        detail.put("logs", addressLogs(requestId));
        return detail;
    }

    @Override
    @Transactional
    public Map<String, Object> approveAddressChange(String requestId, AddressChangeApproveDTO request, String authorization) {
        securityService.requireAnyRole(authorization, "DISPATCHER", "ADMIN");
        AddressChangeRequest change = requireAddressChange(requestId);
        requireAddressChangeStatus(change, "PENDING_REVIEW", "只有待审核的改址申请可以通过");
        CargoRecord cargo = requireCargo(change.getOrderId());
        LogisticsUserContextVO user = securityService.currentOrDefault(authorization, "USR-002", "DISPATCHER");

        boolean recalculateRoute = request == null || request.getRecalculateRoute() == null || request.getRecalculateRoute();
        boolean notifyDriver = request == null || request.getNotifyDriver() == null || request.getNotifyDriver();
        RoutePlanVO route = null;
        if (recalculateRoute) {
            route = plan(cargo.getOriginLng(), cargo.getOriginLat(), change.getNewLng(), change.getNewLat(), cargo.getVehiclePlate());
        }
        cargoMapper.updateDestination(change.getOrderId(), change.getNewAddress(), change.getNewLat(), change.getNewLng());
        orderExtensionMapper.updateAddressChangeStatus(requestId, "APPROVED", request == null ? null : request.getRemark());
        insertAddressLog(requestId, user, "DISPATCHER_APPROVE", trimToNull(request == null ? null : request.getRemark()));
        if (route != null) {
            insertAddressLog(requestId, user, "ROUTE_RECALCULATED", "改址通过后已重新规划路线");
        }

        Map<String, Object> command = null;
        if (notifyDriver && StringUtils.hasText(cargo.getVehiclePlate())) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", requestId);
            payload.put("orderId", change.getOrderId());
            payload.put("newAddress", change.getNewAddress());
            payload.put("lng", change.getNewLng());
            payload.put("lat", change.getNewLat());
            payload.put("route", route);
            command = commandService.createCommand(cargo.getVehiclePlate(), "REROUTE", "HIGH", payload, user.getUserId());
            insertAddressLog(requestId, user, "NOTIFY_DRIVER", "新路线已下发司机");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("status", "APPROVED");
        result.put("routeUpdated", route != null);
        result.put("route", route);
        result.put("commandId", command == null ? null : command.get("commandId"));
        result.put("message", "改址已通过，新路线已下发司机");
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> rejectAddressChange(String requestId, AddressChangeRejectDTO request, String authorization) {
        securityService.requireAnyRole(authorization, "DISPATCHER", "ADMIN");
        AddressChangeRequest change = requireAddressChange(requestId);
        requireAddressChangeStatus(change, "PENDING_REVIEW", "只有待审核的改址申请可以拒绝");
        LogisticsUserContextVO user = securityService.currentOrDefault(authorization, "USR-002", "DISPATCHER");
        String reason = trimToNull(request == null ? null : request.getReason());
        orderExtensionMapper.updateAddressChangeStatus(requestId, "REJECTED", reason);
        insertAddressLog(requestId, user, "DISPATCHER_REJECT", reason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("status", "REJECTED");
        result.put("reason", reason);
        result.put("suggestion", request == null ? null : request.getSuggestion());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> driverConfirmAddressChange(String requestId, DriverConfirmDTO request, String authorization) {
        AddressChangeRequest change = requireAddressChange(requestId);
        if (!"APPROVED".equals(change.getStatus()) && !"AUTO_APPROVED".equals(change.getStatus())) {
            throw new IllegalArgumentException("只有已通过的改址申请需要司机确认");
        }
        LogisticsUserContextVO user = securityService.currentOrDefault(authorization, "USR-005", "DRIVER");
        boolean confirmed = request != null && Boolean.TRUE.equals(request.getConfirmed());
        String status = confirmed ? "DRIVER_CONFIRMED" : "PENDING_REVIEW";
        orderExtensionMapper.updateAddressChangeStatus(requestId, status, request == null ? null : request.getRemark());
        insertAddressLog(requestId, user, confirmed ? "DRIVER_CONFIRM" : "DRIVER_REJECT_CONFIRM", request == null ? null : request.getRemark());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("status", status);
        result.put("confirmed", confirmed);
        return result;
    }

    @Override
    public Map<String, Object> delayPrediction(String orderId) {
        CargoRecord cargo = requireCargo(orderId);
        Map<String, Object> eta = trackingService.getEta(orderId);
        long remainingMinutes = longValue(eta.get("remainingMinutes"));
        int delayMinutes = Math.max(0, (int) remainingMinutes - 180);

        List<String> reasons = new ArrayList<>();
        if (delayMinutes > 0) {
            reasons.add("当前 ETA 超过 3 小时演示阈值");
        }
        if ("STOPPED".equals(eta.get("trend"))) {
            reasons.add("车辆速度较低或处于停车状态");
        }
        List<AddressChangeRequest> changes = orderExtensionMapper.findAddressChangesByOrderId(orderId);
        if (!changes.isEmpty()) {
            reasons.add("存在改址申请，可能影响原计划");
        }
        if (reasons.isEmpty()) {
            reasons.add("暂无明显延误风险");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("plannedArriveAt", cargo.getCreatedAt() == null ? null : cargo.getCreatedAt().plusHours(6).toString());
        result.put("estimatedArriveAt", eta.get("eta"));
        result.put("delayStatus", delayMinutes > 15 ? "POSSIBLE_DELAY" : "ON_TIME");
        result.put("delayMinutes", delayMinutes);
        result.put("reasons", reasons);
        return result;
    }

    @Override
    public Map<String, Object> riskScore(String orderId) {
        requireCargo(orderId);
        List<Map<String, Object>> alerts = orderExtensionMapper.findAlertEventsByOrderId(orderId);
        Map<String, Object> delay = delayPrediction(orderId);
        int score = 0;
        List<Map<String, Object>> factors = new ArrayList<>();

        if (!alerts.isEmpty()) {
            int impact = Math.min(40, alerts.size() * 20);
            score += impact;
            factors.add(factor("异常告警", alerts.size(), impact));
        }
        int delayMinutes = intValue(delay.get("delayMinutes"));
        if (delayMinutes > 15) {
            int impact = Math.min(35, delayMinutes);
            score += impact;
            factors.add(factor("预计延误", "可能延误 " + delayMinutes + " 分钟", impact));
        }
        List<AddressChangeRequest> changes = orderExtensionMapper.findAddressChangesByOrderId(orderId);
        if (!changes.isEmpty()) {
            score += 15;
            factors.add(factor("改址影响", changes.get(0).getImpactLevel(), 15));
        }
        score = Math.min(100, score);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("score", score);
        result.put("level", score >= 70 ? "HIGH" : score >= 40 ? "MEDIUM" : "LOW");
        result.put("factors", factors);
        result.put("suggestion", score >= 40 ? "建议调度员优先关注该订单，并确认司机当前路线" : "当前风险较低，保持常规监控");
        return result;
    }

    @Override
    public Map<String, Object> verifyStatus(String orderId, StatusVerifyDTO request) {
        CargoRecord cargo = requireCargo(orderId);
        if (request == null || request.getReportLocation() == null) {
            throw new IllegalArgumentException("上报位置不能为空");
        }
        double distance = haversineKm(request.getReportLocation(),
                GeoPointDTO.builder().lng(cargo.getDestinationLng()).lat(cargo.getDestinationLat()).build());
        boolean delivered = "DELIVERED".equalsIgnoreCase(request.getReportedStatus()) || "SIGNED".equalsIgnoreCase(request.getReportedStatus());
        boolean credible = !delivered || distance <= 1.5;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("credible", credible);
        result.put("confidence", credible ? 0.92 : 0.42);
        result.put("level", credible ? "HIGH" : "LOW");
        result.put("distanceToDestinationKm", round(distance));
        result.put("reason", credible ? "状态与位置基本一致" : "司机上报已送达，但当前位置距离目的地较远");
        result.put("suggestion", credible ? "可进入后续流程" : "建议调度员联系司机确认");
        return result;
    }

    @Override
    public Map<String, Object> exceptionSummary(String orderId) {
        requireCargo(orderId);
        List<Map<String, Object>> events = orderExtensionMapper.findAlertEventsByOrderId(orderId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("hasException", !events.isEmpty());
        result.put("level", events.isEmpty() ? "NORMAL" : "WARNING");
        result.put("summary", events.isEmpty() ? "当前订单暂无异常" : "订单存在运输异常，调度员正在处理或已处理");
        result.put("events", events);
        return result;
    }

    @Override
    public Map<String, Object> unloadAddressSuggestions(String orderId) {
        CargoRecord cargo = requireCargo(orderId);
        List<Map<String, Object>> suggestions = new ArrayList<>();

        for (UnloadAddressRecord record : orderExtensionMapper.findUnloadAddressRecords(orderId, "CONFIRM")) {
            Map<String, Object> item = suggestion("HISTORY", record.getAddress(), record.getLng(), record.getLat(), 0.93, "历史确认卸货点");
            suggestions.add(item);
        }
        try {
            List<AmapInputTipVO> tips = amapService.inputTips(cargo.getDestinationName(), null);
            for (AmapInputTipVO tip : tips) {
                if (tip.getLng() != null && tip.getLat() != null) {
                    suggestions.add(suggestion("AMAP_POI", tip.getName(), tip.getLng(), tip.getLat(), 0.76, "高德输入提示匹配结果"));
                }
            }
        } catch (Exception e) {
            log.debug("获取高德卸货点建议失败: {}", e.getMessage());
        }
        if (suggestions.isEmpty()) {
            suggestions.add(suggestion("CURRENT", cargo.getDestinationName(), cargo.getDestinationLng(), cargo.getDestinationLat(), 0.82, "当前订单目的地"));
        }

        Map<String, Object> currentAddress = new LinkedHashMap<>();
        currentAddress.put("detail", cargo.getDestinationName());
        currentAddress.put("lng", cargo.getDestinationLng());
        currentAddress.put("lat", cargo.getDestinationLat());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("currentAddress", currentAddress);
        result.put("confidence", suggestions.get(0).get("confidence"));
        result.put("suggestions", suggestions);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> confirmUnloadAddress(String orderId, UnloadAddressConfirmDTO request, String authorization) {
        requireCargo(orderId);
        if (request == null || !StringUtils.hasText(request.getAddress()) || request.getLng() == null || request.getLat() == null) {
            throw new IllegalArgumentException("确认卸货地址、经纬度不能为空");
        }
        LogisticsUserContextVO user = securityService.currentOrDefault(authorization, "USR-002", "DISPATCHER");
        UnloadAddressRecord record = unloadRecord(orderId, "CONFIRM", request.getAddress(), request.getLng(), request.getLat(), null,
                null, null, request.getRemark(), user);
        orderExtensionMapper.insertUnloadAddressRecord(record);
        cargoMapper.updateDestination(orderId, request.getAddress(), request.getLat(), request.getLng());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("recordId", record.getRecordId());
        result.put("status", "CONFIRMED");
        result.put("address", request.getAddress());
        result.put("lng", request.getLng());
        result.put("lat", request.getLat());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> reportUnloadAddressAbnormal(String orderId, UnloadAddressAbnormalDTO request, String authorization) {
        requireCargo(orderId);
        if (request == null || !StringUtils.hasText(request.getType())) {
            throw new IllegalArgumentException("异常类型不能为空");
        }
        LogisticsUserContextVO user = securityService.currentOrDefault(authorization, "USR-005", "DRIVER");
        String photos = null;
        try {
            photos = objectMapper.writeValueAsString(request.getPhotos() == null ? Collections.emptyList() : request.getPhotos());
        } catch (Exception ignored) {
        }
        UnloadAddressRecord record = unloadRecord(orderId, "ABNORMAL", null, request.getLng(), request.getLat(), request.getType(),
                request.getDescription(), photos, null, user);
        orderExtensionMapper.insertUnloadAddressRecord(record);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("recordId", record.getRecordId());
        result.put("status", "REPORTED");
        result.put("type", request.getType());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> submitDriverRating(String orderId, DriverRatingCreateDTO request, String authorization) {
        CargoRecord cargo = requireCargo(orderId);
        if (!"DELIVERED".equals(cargo.getStatus()) && !"SIGNED".equals(cargo.getStatus())) {
            throw new IllegalArgumentException("订单未签收，不能评价司机");
        }
        if (request == null || request.getScore() == null || request.getScore() < 1 || request.getScore() > 5) {
            throw new IllegalArgumentException("评分必须在 1 到 5 之间");
        }
        if (orderExtensionMapper.findDriverRatingByOrderId(orderId) != null) {
            throw new IllegalArgumentException("该订单已经评价过司机");
        }
        List<String> tags = request.getTags() == null ? Collections.<String>emptyList() : request.getTags();
        if (tags.size() > 5) {
            throw new IllegalArgumentException("评价标签最多 5 个");
        }

        LogisticsUserContextVO user = securityService.currentOrDefault(authorization, "USR-001", "SHIPPER");
        DriverRating rating = new DriverRating();
        rating.setRatingId(nextId("RATE"));
        rating.setOrderId(orderId);
        rating.setCustomerId(valueOrDefault(user.getUserId(), "USR-001"));
        rating.setDriverId(valueOrDefault(trimToNull(request.getDriverId()), cargo.getVehicleId() == null ? cargo.getVehiclePlate() : "DRV-" + cargo.getVehicleId()));
        rating.setPlate(cargo.getVehiclePlate());
        rating.setScore(request.getScore());
        DriverRatingDimensionsDTO dimensions = request.getDimensions();
        rating.setPunctuality(dimensions == null ? request.getScore() : dimensions.getPunctuality());
        rating.setServiceAttitude(dimensions == null ? request.getScore() : dimensions.getServiceAttitude());
        rating.setCargoIntegrity(dimensions == null ? request.getScore() : dimensions.getCargoIntegrity());
        rating.setCommunication(dimensions == null ? request.getScore() : dimensions.getCommunication());
        rating.setComment(trimToNull(request.getComment()));
        orderExtensionMapper.insertDriverRating(rating);
        for (String tag : tags) {
            String safeTag = trimToNull(tag);
            if (StringUtils.hasText(safeTag)) {
                DriverRatingTag entity = new DriverRatingTag();
                entity.setRatingId(rating.getRatingId());
                entity.setTagName(safeTag);
                orderExtensionMapper.insertDriverRatingTag(entity);
            }
        }
        return ratingMap(rating, tags);
    }

    @Override
    public Map<String, Object> getDriverRating(String orderId) {
        requireCargo(orderId);
        DriverRating rating = orderExtensionMapper.findDriverRatingByOrderId(orderId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rated", rating != null);
        result.put("rating", rating == null ? null : ratingMap(rating, tagNames(rating.getRatingId())));
        return result;
    }

    @Override
    public Map<String, Object> driverRatingSummary(String driverId) {
        if (!StringUtils.hasText(driverId)) {
            throw new IllegalArgumentException("司机 ID 不能为空");
        }
        Map<String, Object> summary = orderExtensionMapper.ratingSummary(driverId);
        if (summary == null) {
            summary = new LinkedHashMap<>();
            summary.put("driverId", driverId);
            summary.put("averageScore", 0);
            summary.put("ratingCount", 0);
        }
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("punctuality", summary.get("punctuality"));
        dimensions.put("serviceAttitude", summary.get("serviceAttitude"));
        dimensions.put("cargoIntegrity", summary.get("cargoIntegrity"));
        dimensions.put("communication", summary.get("communication"));
        summary.put("dimensions", dimensions);
        summary.remove("punctuality");
        summary.remove("serviceAttitude");
        summary.remove("cargoIntegrity");
        summary.remove("communication");
        summary.put("tags", orderExtensionMapper.ratingTagStats(driverId));
        return summary;
    }

    @Override
    public PageResponse<Map<String, Object>> driverRatings(String driverId, Integer page, Integer size) {
        int p = normalizePage(page);
        int s = normalizeSize(size);
        int offset = (p - 1) * s;
        Long total = orderExtensionMapper.countRatingsByDriverId(driverId);
        if (total == null || total == 0) {
            return new PageResponse<>(Collections.<Map<String, Object>>emptyList(), p, s, 0L, 0);
        }
        List<Map<String, Object>> content = new ArrayList<>();
        for (DriverRating rating : orderExtensionMapper.findRatingsByDriverId(driverId, offset, s)) {
            content.add(ratingMap(rating, tagNames(rating.getRatingId())));
        }
        return new PageResponse<>(content, p, s, total, (int) Math.ceil((double) total / s));
    }

    private RoutePlanVO plan(Double originLng, Double originLat, Double destLng, Double destLat, String plate) {
        RoutePlanRequestDTO routeRequest = new RoutePlanRequestDTO();
        routeRequest.setOrigin(GeoPointDTO.builder().lng(originLng).lat(originLat).build());
        routeRequest.setDestination(GeoPointDTO.builder().lng(destLng).lat(destLat).build());
        routeRequest.setPlate(plate);
        routeRequest.setStrategy("FASTEST");
        return routePlanService.plan(routeRequest);
    }

    private CargoRecord requireCargo(String orderId) {
        String safeOrderId = trimToNull(orderId);
        if (!StringUtils.hasText(safeOrderId)) {
            throw new IllegalArgumentException("订单 ID 不能为空");
        }
        CargoRecord cargo = cargoMapper.findByCargoId(safeOrderId);
        if (cargo == null) {
            throw new IllegalArgumentException("订单不存在: " + safeOrderId);
        }
        return cargo;
    }

    private AddressChangeRequest requireAddressChange(String requestId) {
        String safeRequestId = trimToNull(requestId);
        if (!StringUtils.hasText(safeRequestId)) {
            throw new IllegalArgumentException("改址申请 ID 不能为空");
        }
        AddressChangeRequest request = orderExtensionMapper.findAddressChangeByRequestId(safeRequestId);
        if (request == null) {
            throw new IllegalArgumentException("改址申请不存在: " + safeRequestId);
        }
        return request;
    }

    private void requireAddressChangeStatus(AddressChangeRequest request, String expectedStatus, String message) {
        if (request == null || !expectedStatus.equals(request.getStatus())) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateAddress(AddressDTO address) {
        if (address == null || !StringUtils.hasText(address.getDetail()) || address.getLng() == null || address.getLat() == null) {
            throw new IllegalArgumentException("新地址详情、经纬度不能为空");
        }
    }

    private void ensureCargoCoordinates(CargoRecord cargo) {
        if (cargo.getOriginLng() == null || cargo.getOriginLat() == null
                || cargo.getDestinationLng() == null || cargo.getDestinationLat() == null) {
            throw new IllegalArgumentException("订单缺少起点或终点经纬度，无法规划路线");
        }
    }

    private String impactLevel(double extraKm, int delayMinutes, boolean outOfArea) {
        if (outOfArea) return "HIGH";
        if (extraKm <= 1 && delayMinutes <= 5) return "MICRO";
        if (extraKm <= 5 && delayMinutes <= 15) return "LOW";
        if (extraKm <= 20 || delayMinutes <= 45) return "MEDIUM";
        return "HIGH";
    }

    private String reason(String level, double extraKm, int delayMinutes, double distanceFromRoute, boolean canChange) {
        if (!canChange) {
            return "新地址超出当前配送范围或订单状态不允许改址";
        }
        return "新地址距离原路线 " + round(distanceFromRoute) + "km，预计增加 "
                + round(extraKm) + "km / " + delayMinutes + " 分钟，影响等级 " + level;
    }

    private String formatAddress(AddressDTO address) {
        StringBuilder builder = new StringBuilder();
        append(builder, address.getProvince());
        append(builder, address.getCity());
        append(builder, address.getDistrict());
        append(builder, address.getDetail());
        return builder.toString();
    }

    private void append(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(value.trim());
        }
    }

    private void insertAddressLog(String requestId, LogisticsUserContextVO user, String action, String remark) {
        AddressChangeLog log = new AddressChangeLog();
        log.setRequestId(requestId);
        log.setOperatorId(user == null ? null : user.getUserId());
        log.setOperatorRole(user == null ? null : user.getRole());
        log.setAction(action);
        log.setRemark(remark);
        orderExtensionMapper.insertAddressChangeLog(log);
    }

    private Map<String, Object> addressChangeSummary(AddressChangeRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("requestId", request.getRequestId());
        map.put("orderId", request.getOrderId());
        map.put("oldAddress", request.getOldAddress());
        map.put("newAddress", request.getNewAddress());
        map.put("status", request.getStatus());
        map.put("impactLevel", request.getImpactLevel());
        map.put("extraDistanceKm", request.getExtraDistanceKm());
        map.put("estimatedDelayMinutes", request.getEstimatedDelayMinutes());
        map.put("extraCost", request.getExtraCost());
        map.put("needDispatcherReview", request.getNeedDispatcherReview());
        map.put("needDriverConfirm", request.getNeedDriverConfirm());
        map.put("createdAt", request.getCreatedAt() == null ? null : request.getCreatedAt().toString());
        return map;
    }

    private List<Map<String, Object>> addressLogs(String requestId) {
        List<Map<String, Object>> logs = new ArrayList<>();
        for (AddressChangeLog log : orderExtensionMapper.findAddressChangeLogs(requestId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("action", log.getAction());
            item.put("operatorRole", log.getOperatorRole());
            item.put("remark", log.getRemark());
            item.put("createdAt", log.getCreatedAt() == null ? null : log.getCreatedAt().toString());
            logs.add(item);
        }
        return logs;
    }

    private UnloadAddressRecord unloadRecord(String orderId, String recordType, String address, Double lng, Double lat,
                                             String abnormalType, String description, String photos, String remark,
                                             LogisticsUserContextVO user) {
        UnloadAddressRecord record = new UnloadAddressRecord();
        record.setRecordId(nextId("ULD"));
        record.setOrderId(orderId);
        record.setRecordType(recordType);
        record.setAddress(address);
        record.setLng(lng);
        record.setLat(lat);
        record.setAbnormalType(abnormalType);
        record.setDescription(description);
        record.setPhotos(photos);
        record.setRemark(remark);
        record.setOperatorId(user == null ? null : user.getUserId());
        record.setOperatorRole(user == null ? null : user.getRole());
        return record;
    }

    private Map<String, Object> ratingMap(DriverRating rating, List<String> tags) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ratingId", rating.getRatingId());
        map.put("orderId", rating.getOrderId());
        map.put("driverId", rating.getDriverId());
        map.put("plate", rating.getPlate());
        map.put("score", rating.getScore());
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("punctuality", rating.getPunctuality());
        dimensions.put("serviceAttitude", rating.getServiceAttitude());
        dimensions.put("cargoIntegrity", rating.getCargoIntegrity());
        dimensions.put("communication", rating.getCommunication());
        map.put("dimensions", dimensions);
        map.put("tags", tags);
        map.put("comment", rating.getComment());
        map.put("createdAt", rating.getCreatedAt() == null ? null : rating.getCreatedAt().toString());
        return map;
    }

    private List<String> tagNames(String ratingId) {
        List<String> tags = new ArrayList<>();
        for (DriverRatingTag tag : orderExtensionMapper.findDriverRatingTags(ratingId)) {
            tags.add(tag.getTagName());
        }
        return tags;
    }

    private Map<String, Object> factor(String name, Object value, int impact) {
        Map<String, Object> factor = new LinkedHashMap<>();
        factor.put("name", name);
        factor.put("value", value);
        factor.put("impact", impact);
        return factor;
    }

    private Map<String, Object> suggestion(String source, String address, Double lng, Double lat, double confidence, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("source", source);
        item.put("address", address);
        item.put("lng", lng);
        item.put("lat", lat);
        item.put("confidence", confidence);
        item.put("reason", reason);
        return item;
    }

    private String nextId(String prefix) {
        String date = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(Instant.now());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return prefix + "-" + date + "-" + suffix;
    }

    private double minDistanceToPolylineKm(GeoPointDTO point, List<GeoPointDTO> polyline) {
        if (polyline == null || polyline.size() < 2) {
            return 0.0;
        }
        double min = Double.MAX_VALUE;
        for (int i = 1; i < polyline.size(); i++) {
            min = Math.min(min, distanceToSegmentKm(point, polyline.get(i - 1), polyline.get(i)));
        }
        return min == Double.MAX_VALUE ? 0.0 : min;
    }

    private double distanceToSegmentKm(GeoPointDTO point, GeoPointDTO start, GeoPointDTO end) {
        double latScale = 111.32;
        double lngScale = 111.32 * Math.cos(Math.toRadians(point.getLat()));
        double px = point.getLng() * lngScale;
        double py = point.getLat() * latScale;
        double ax = start.getLng() * lngScale;
        double ay = start.getLat() * latScale;
        double bx = end.getLng() * lngScale;
        double by = end.getLat() * latScale;
        double dx = bx - ax;
        double dy = by - ay;
        if (dx == 0 && dy == 0) {
            return Math.sqrt(Math.pow(px - ax, 2) + Math.pow(py - ay, 2));
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * dx;
        double cy = ay + t * dy;
        return Math.sqrt(Math.pow(px - cx, 2) + Math.pow(py - cy, 2));
    }

    private double haversineKm(GeoPointDTO a, GeoPointDTO b) {
        final double r = 6371.0;
        double dLat = Math.toRadians(b.getLat() - a.getLat());
        double dLng = Math.toRadians(b.getLng() - a.getLng());
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.getLat())) * Math.cos(Math.toRadians(b.getLat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal money(Double value) {
        return BigDecimal.valueOf(value == null ? 0.0 : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private long longValue(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
