package com.sky.logistics.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
@ApiModel("用户信息")
public class UserVO {

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

    @ApiModelProperty(value = "创建时间")
    private OffsetDateTime createdAt;

    @ApiModelProperty(value = "更新时间")
    private OffsetDateTime updatedAt;
}
