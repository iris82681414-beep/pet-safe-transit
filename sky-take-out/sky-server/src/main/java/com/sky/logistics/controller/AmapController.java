package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.dto.AmapGeocodeDTO;
import com.sky.logistics.dto.AmapRegeoDTO;
import com.sky.logistics.service.AmapService;
import com.sky.logistics.vo.AmapAddressVO;
import com.sky.logistics.vo.AmapInputTipVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/amap", "/api/amap"})
@Api(tags = "智慧物流-高德基础接口")
public class AmapController {

    private final AmapService amapService;

    public AmapController(AmapService amapService) {
        this.amapService = amapService;
    }

    @GetMapping("/input-tips")
    @ApiOperation("地址输入提示")
    public ApiResponse<List<AmapInputTipVO>> inputTips(@RequestParam String keywords,
                                                       @RequestParam(required = false) String city) {
        return ApiResponse.success(amapService.inputTips(keywords, city));
    }

    @PostMapping("/geocode")
    @ApiOperation("地址转经纬度")
    public ApiResponse<AmapAddressVO> geocode(@RequestBody AmapGeocodeDTO request) {
        return ApiResponse.success(amapService.geocode(request == null ? null : request.getAddress(),
                request == null ? null : request.getCity()));
    }

    @PostMapping("/regeo")
    @ApiOperation("经纬度转地址")
    public ApiResponse<AmapAddressVO> regeo(@RequestBody AmapRegeoDTO request) {
        return ApiResponse.success(amapService.regeo(request == null ? null : request.getLng(),
                request == null ? null : request.getLat()));
    }
}
