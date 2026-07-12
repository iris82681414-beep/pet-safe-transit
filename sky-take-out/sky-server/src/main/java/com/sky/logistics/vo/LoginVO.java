package com.sky.logistics.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("登录响应")
public class LoginVO {

    @ApiModelProperty("访问令牌，调用需要登录的接口时放到 Authorization: Bearer <accessToken>")
    private String accessToken;

    @ApiModelProperty("刷新令牌")
    private String refreshToken;

    @ApiModelProperty(value = "访问令牌过期秒数", example = "7200")
    private Integer expiresIn;

    @ApiModelProperty("当前登录用户公开信息")
    private LoginUserVO user;

    @ApiModelProperty("人脸识别结果，仅人脸登录时返回")
    private Object face;
}
