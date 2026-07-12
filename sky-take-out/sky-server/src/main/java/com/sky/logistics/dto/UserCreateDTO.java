package com.sky.logistics.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("新增用户请求")
public class UserCreateDTO {

    @ApiModelProperty(value = "用户名", example = "new_dispatcher")
    private String username;

    @ApiModelProperty(value = "明文密码，后端保存 MD5", example = "123456")
    private String password;

    @ApiModelProperty(value = "姓名", example = "新调度员")
    private String name;

    @ApiModelProperty(value = "角色", example = "DISPATCHER")
    private String role;

    @ApiModelProperty(value = "手机号", example = "13800000006")
    private String phone;
}
