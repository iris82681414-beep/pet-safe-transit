package com.sky.logistics.service;

import com.sky.logistics.dto.CargoStatusUpdateDTO;
import com.sky.logistics.vo.CargoVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShipperOrderService {

    private final ShipperAccessService accessService;
    private final CargoService cargoService;
    private final JdbcTemplate jdbcTemplate;

    public ShipperOrderService(ShipperAccessService accessService, CargoService cargoService,
                               @Qualifier("businessJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.accessService = accessService;
        this.cargoService = cargoService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public CargoVO confirmReceipt(String cargoId, String authorization) {
        accessService.requireShipperOrAdmin(authorization);
        accessService.requireOwnedIfShipper(cargoId, authorization);
        CargoVO cargo = cargoService.detail(cargoId);
        if ("CANCELLED".equals(cargo.getStatus()) || "CREATED".equals(cargo.getStatus())) {
            throw new IllegalArgumentException("当前订单状态不能确认签收");
        }
        if ("DELIVERED".equals(cargo.getStatus())) {
            return cargo;
        }
        CargoStatusUpdateDTO update = new CargoStatusUpdateDTO();
        update.setStatus("DELIVERED");
        update.setRemark("货主已确认收到宠物");
        update.setOperatorId(accessService.current(authorization).getUserId());
        return cargoService.updateStatus(cargoId, update);
    }

    public Map<String, Object> environment(String cargoId, String authorization) {
        accessService.requireShipperOrAdmin(authorization);
        accessService.requireOwnedIfShipper(cargoId, authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT temperature, humidity, air_quality AS \"airQuality\", vibration, recorded_at AS \"recordedAt\" " +
                        "FROM cargo_environment_readings WHERE cargo_id = ? ORDER BY recorded_at DESC LIMIT 1",
                cargoId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cargoId", cargoId);
        if (rows.isEmpty()) {
            result.put("status", "NO_DATA");
            result.put("temperature", null);
            result.put("humidity", null);
            result.put("airQuality", null);
            result.put("vibration", null);
            result.put("recordedAt", null);
        } else {
            result.putAll(rows.get(0));
            result.put("status", "NORMAL");
        }
        return result;
    }

    public List<Map<String, Object>> notifications(String cargoId, String authorization) {
        accessService.requireShipperOrAdmin(authorization);
        accessService.requireOwnedIfShipper(cargoId, authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT alert_id AS \"alertId\", alert_type AS \"alertType\", severity, status, title, " +
                        "COALESCE(summary, description) AS summary, triggered_at AS \"triggeredAt\" " +
                        "FROM alerts WHERE cargo_id = ? ORDER BY triggered_at DESC LIMIT 20",
                cargoId);
        return rows == null ? new ArrayList<>() : rows;
    }
}
