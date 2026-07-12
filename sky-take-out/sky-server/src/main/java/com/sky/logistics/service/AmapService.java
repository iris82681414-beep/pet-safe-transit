package com.sky.logistics.service;

import com.sky.logistics.dto.GeoPointDTO;
import com.sky.logistics.dto.GpsPointDTO;
import com.sky.logistics.dto.RoutePlanRequestDTO;
import com.sky.logistics.vo.AmapAddressVO;
import com.sky.logistics.vo.AmapInputTipVO;
import com.sky.logistics.vo.RoutePlanVO;

import java.util.List;

public interface AmapService {

    List<AmapInputTipVO> inputTips(String keywords, String city);

    AmapAddressVO geocode(String address, String city);

    AmapAddressVO regeo(Double lng, Double lat);

    RoutePlanVO planDrivingRoute(RoutePlanRequestDTO request);

    RoutePlanVO planTruckRoute(RoutePlanRequestDTO request);

    List<GeoPointDTO> correctTrajectory(List<GpsPointDTO> points);
}
