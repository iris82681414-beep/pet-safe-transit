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

    private String petName;
    private String petBreed;
    private String petAge;
    private String petGender;

    @ApiModelProperty(value = "重量 kg", example = "2500.00")
    private BigDecimal weight;

    @ApiModelProperty("起点")
    private CargoLocationDTO origin;

    @ApiModelProperty("终点")
    private CargoLocationDTO destination;

    private String contactName;
    private String contactPhone;
    private String receiverName;
    private String receiverPhone;
    private String requestNote;
}
