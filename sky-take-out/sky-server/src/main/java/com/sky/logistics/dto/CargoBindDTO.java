package com.sky.logistics.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("绑定货物与车辆请求")
public class CargoBindDTO {

    @ApiModelProperty(value = "货物 ID", example = "SH-HZ-20260629-0291")
    private String cargoId;

    @ApiModelProperty(value = "车辆主键 ID", example = "1")
    private Long vehicleId;
}
