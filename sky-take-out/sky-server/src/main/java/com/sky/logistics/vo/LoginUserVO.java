package com.sky.logistics.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@ApiModel("登录用户信息")
public class LoginUserVO {

    @ApiModelProperty(value = "用户 ID", example = "USR-002")
    private String id;

    @ApiModelProperty(value = "用户名", example = "dispatcher")
    private String username;

    @ApiModelProperty(value = "姓名", example = "王调度")
    private String name;

    @ApiModelProperty(value = "角色", example = "DISPATCHER")
    private String role;

    @ApiModelProperty(value = "手机号", example = "13800000002")
    private String phone;

    @ApiModelProperty(value = "权限标识", example = "[\"VEHICLE_READ\", \"COMMAND_SEND\", \"ALERT_READ\"]")
    private List<String> permissions;
}
