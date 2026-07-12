package com.sky.logistics.mapper;

import com.sky.logistics.entity.Alert;
import com.sky.logistics.entity.AlertLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AlertMapper {

    List<Alert> findPage(@Param("severity") String severity,
                         @Param("type") String type,
                         @Param("status") String status,
                         @Param("vehiclePlate") String vehiclePlate,
                         @Param("offset") Integer offset,
                         @Param("limit") Integer limit);

    Long count(@Param("severity") String severity,
               @Param("type") String type,
               @Param("status") String status,
               @Param("vehiclePlate") String vehiclePlate);

    Alert findByAlertId(@Param("alertId") String alertId);

    int insert(Alert alert);

    int updateStatus(@Param("alertId") String alertId,
                     @Param("status") String status,
                     @Param("resolution") String resolution,
                     @Param("remark") String remark,
                     @Param("resolvedAt") java.time.OffsetDateTime resolvedAt,
                     @Param("acknowledgedAt") java.time.OffsetDateTime acknowledgedAt);

    int insertLog(AlertLog log);

    List<AlertLog> findLogsByAlertId(@Param("alertId") String alertId);

    int countByPlateTypeStatus(@Param("vehiclePlate") String vehiclePlate,
                               @Param("alertType") String alertType);

    List<Map<String, Object>> statsByStatus();

    List<Map<String, Object>> statsByType();
}
