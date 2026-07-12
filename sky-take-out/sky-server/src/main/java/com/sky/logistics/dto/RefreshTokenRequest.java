package com.sky.logistics.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("刷新 Token 请求")
public class RefreshTokenRequest {

    @ApiModelProperty(value = "登录接口返回的 refreshToken", example = "jwt-refresh-token", required = true)
    private String refreshToken;
}
