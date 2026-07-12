package com.sky.logistics.mapper;

import com.sky.logistics.entity.Vehicle;
import com.sky.logistics.vo.VehicleActiveCargoVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LogisticsVehicleMapper {

    List<Vehicle> findPage(@Param("status") String status,
                           @Param("keyword") String keyword,
                           @Param("offset") Integer offset,
                           @Param("limit") Integer limit);

    Long count(@Param("status") String status,
               @Param("keyword") String keyword);

    Vehicle findById(@Param("id") Long id);

    Vehicle findByPlate(@Param("plate") String plate);

    Vehicle findByVinTopic(@Param("vinTopic") String vinTopic);

    Vehicle findByImei(@Param("imei") String imei);

    Vehicle findByPlateExcludeId(@Param("plate") String plate,
                                 @Param("id") Long id);

    int insert(Vehicle vehicle);

    Vehicle findByDeviceImei(@Param("deviceImei") String deviceImei);

    Vehicle findByDeviceImeiExcludeId(@Param("deviceImei") String deviceImei,
                                      @Param("id") Long id);

    Long countBindingById(@Param("id") Long id);

    List<VehicleActiveCargoVO> findActiveCargosByPlate(@Param("plate") String plate);

    int update(Vehicle vehicle);

    int deleteById(@Param("id") Long id);

    List<Vehicle> findAll();

    int updateDeviceStatus(@Param("id") Long id,
                           @Param("deviceStatus") String deviceStatus);
}
