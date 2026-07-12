package com.sky.logistics.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AddressChangeImpactVO {
    private String orderId;
    private String currentOrderStatus;
    private Boolean canChange;
    private String impactLevel;
    private Double oldRouteDistanceKm;
    private Double newRouteDistanceKm;
    private Long oldDistanceMeters;
    private Long newDistanceMeters;
    private Double extraDistanceKm;
    private Integer estimatedDelayMinutes;
    private BigDecimal extraCost;
    private Boolean isNearCurrentRoute;
    private Double distanceFromCurrentRouteKm;
    private Boolean isOutOfServiceArea;
    private Boolean needDispatcherReview;
    private Boolean needDriverConfirm;
    private String reason;
}
