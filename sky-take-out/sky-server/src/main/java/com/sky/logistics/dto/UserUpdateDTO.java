package com.sky.logistics.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("修改用户请求")
public class UserUpdateDTO {

    @ApiModelProperty(value = "姓名", example = "王调度")
    private String name;

    @ApiModelProperty(value = "角色", example = "DISPATCHER")
    private String role;

    @ApiModelProperty(value = "手机号", example = "13800000002")
    private String phone;

    @ApiModelProperty(value = "新密码，不传则不修改", example = "123456")
    private String password;
}
