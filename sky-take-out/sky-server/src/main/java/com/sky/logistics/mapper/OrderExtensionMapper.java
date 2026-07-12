package com.sky.logistics.mapper;

import com.sky.logistics.entity.AddressChangeLog;
import com.sky.logistics.entity.AddressChangeRequest;
import com.sky.logistics.entity.DriverRating;
import com.sky.logistics.entity.DriverRatingTag;
import com.sky.logistics.entity.UnloadAddressRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderExtensionMapper {

    int insertAddressChange(AddressChangeRequest request);

    int insertAddressChangeLog(AddressChangeLog log);

    AddressChangeRequest findAddressChangeByRequestId(@Param("requestId") String requestId);

    List<AddressChangeRequest> findAddressChangesByOrderId(@Param("orderId") String orderId);

    List<AddressChangeLog> findAddressChangeLogs(@Param("requestId") String requestId);

    int updateAddressChangeStatus(@Param("requestId") String requestId,
                                  @Param("status") String status,
                                  @Param("remark") String remark);

    int insertDriverRating(DriverRating rating);

    int insertDriverRatingTag(DriverRatingTag tag);

    DriverRating findDriverRatingByOrderId(@Param("orderId") String orderId);

    List<DriverRatingTag> findDriverRatingTags(@Param("ratingId") String ratingId);

    Map<String, Object> ratingSummary(@Param("driverId") String driverId);

    List<Map<String, Object>> ratingTagStats(@Param("driverId") String driverId);

    List<DriverRating> findRatingsByDriverId(@Param("driverId") String driverId,
                                             @Param("offset") Integer offset,
                                             @Param("limit") Integer limit);

    Long countRatingsByDriverId(@Param("driverId") String driverId);

    int insertUnloadAddressRecord(UnloadAddressRecord record);

    List<UnloadAddressRecord> findUnloadAddressRecords(@Param("orderId") String orderId,
                                                       @Param("recordType") String recordType);

    List<Map<String, Object>> findAlertEventsByOrderId(@Param("orderId") String orderId);
}
