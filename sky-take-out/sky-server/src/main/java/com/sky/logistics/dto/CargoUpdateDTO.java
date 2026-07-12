package com.sky.logistics.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@ApiModel("修改货物请求")
public class CargoUpdateDTO {

    @ApiModelProperty(value = "货物类型", example = "电子产品")
    private String cargoType;

    @ApiModelProperty(value = "重量 kg", example = "2500.00")
    private BigDecimal weight;

    @ApiModelProperty("起点")
    private CargoLocationDTO origin;

    @ApiModelProperty("终点")
    private CargoLocationDTO destination;
}
