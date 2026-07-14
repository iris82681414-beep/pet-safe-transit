package com.sky.logistics.mapper;

import com.sky.logistics.entity.Cargo;
import com.sky.logistics.entity.CargoRecord;
import com.sky.logistics.entity.CargoStatusLog;
import com.sky.logistics.entity.CargoVehicleBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LogisticsCargoMapper {

    List<CargoRecord> findPage(@Param("status") String status,
                               @Param("keyword") String keyword,
                               @Param("ownerId") String ownerId,
                               @Param("offset") Integer offset,
                               @Param("limit") Integer limit);

    Long count(@Param("status") String status,
               @Param("keyword") String keyword,
               @Param("ownerId") String ownerId);

    CargoRecord findByCargoId(@Param("cargoId") String cargoId);

    int countOwnedBy(@Param("cargoId") String cargoId, @Param("ownerId") String ownerId);

    int insert(Cargo cargo);

    int update(Cargo cargo);

    Long countActiveBindingByCargoId(@Param("cargoId") String cargoId);

    void insertBinding(CargoVehicleBinding binding);

    void updateCargoStatus(@Param("cargoId") String cargoId, @Param("status") String status);

    int updateDestination(@Param("cargoId") String cargoId,
                          @Param("destinationName") String destinationName,
                          @Param("destinationLat") Double destinationLat,
                          @Param("destinationLng") Double destinationLng);

    List<CargoRecord> findInTransitWithVehicle();

    CargoVehicleBinding findActiveBindingByCargoId(@Param("cargoId") String cargoId);

    int unbindById(@Param("id") String id);

    void insertStatusLog(CargoStatusLog cargoStatusLog);

    List<CargoStatusLog> findStatusLogsByCargoId(@Param("cargoId") String cargoId);

    List<String> findActiveCargoIdsByVehicleId(@Param("vehicleId") Long vehicleId);
}
