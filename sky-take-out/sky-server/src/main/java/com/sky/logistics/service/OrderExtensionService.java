package com.sky.logistics.service;

import com.sky.logistics.common.PageResponse;
import com.sky.logistics.dto.AddressChangeApproveDTO;
import com.sky.logistics.dto.AddressChangeCreateDTO;
import com.sky.logistics.dto.AddressChangeImpactDTO;
import com.sky.logistics.dto.AddressChangeRejectDTO;
import com.sky.logistics.dto.DriverConfirmDTO;
import com.sky.logistics.dto.DriverRatingCreateDTO;
import com.sky.logistics.dto.StatusVerifyDTO;
import com.sky.logistics.dto.UnloadAddressAbnormalDTO;
import com.sky.logistics.dto.UnloadAddressConfirmDTO;
import com.sky.logistics.vo.AddressChangeImpactVO;

import java.util.Map;

public interface OrderExtensionService {

    AddressChangeImpactVO calculateAddressChangeImpact(String orderId, AddressChangeImpactDTO request);

    Map<String, Object> createAddressChangeRequest(String orderId, AddressChangeCreateDTO request, String authorization);

    Map<String, Object> addressChangeHistory(String orderId);

    Map<String, Object> addressChangeDetail(String requestId);

    Map<String, Object> approveAddressChange(String requestId, AddressChangeApproveDTO request, String authorization);

    Map<String, Object> rejectAddressChange(String requestId, AddressChangeRejectDTO request, String authorization);

    Map<String, Object> driverConfirmAddressChange(String requestId, DriverConfirmDTO request, String authorization);

    Map<String, Object> delayPrediction(String orderId);

    Map<String, Object> riskScore(String orderId);

    Map<String, Object> verifyStatus(String orderId, StatusVerifyDTO request);

    Map<String, Object> exceptionSummary(String orderId);

    Map<String, Object> unloadAddressSuggestions(String orderId);

    Map<String, Object> confirmUnloadAddress(String orderId, UnloadAddressConfirmDTO request, String authorization);

    Map<String, Object> reportUnloadAddressAbnormal(String orderId, UnloadAddressAbnormalDTO request, String authorization);

    Map<String, Object> submitDriverRating(String orderId, DriverRatingCreateDTO request, String authorization);

    Map<String, Object> getDriverRating(String orderId);

    Map<String, Object> driverRatingSummary(String driverId);

    PageResponse<Map<String, Object>> driverRatings(String driverId, Integer page, Integer size);
}
