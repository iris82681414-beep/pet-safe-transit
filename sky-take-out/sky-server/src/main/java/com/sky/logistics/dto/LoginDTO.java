package com.sky.logistics.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("登录请求")
public class LoginDTO {

    @ApiModelProperty(value = "用户名", example = "dispatcher", required = true)
    private String username;

    @ApiModelProperty(value = "密码，当前演示账号统一为 123456，数据库保存 MD5", example = "123456", required = true)
    private String password;
}
