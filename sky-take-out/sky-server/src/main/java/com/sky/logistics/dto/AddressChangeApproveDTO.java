package com.sky.logistics.dto;

import lombok.Data;

@Data
public class AddressChangeApproveDTO {
    private String remark;
    private Boolean notifyDriver;
    private Boolean recalculateRoute;
}
