package com.sky.logistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 返回的 Action VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentActionVO {
    /** 操作类型：NAVIGATE / HIGHLIGHT_MAP_TARGET / CALL_FUNCTION / CALL_API / NOOP */
    private String type;
    /** 目标类型：VEHICLE / PAGE / ORDER */
    private String targetType;
    /** 目标值 */
    private String targetValue;
    /** 前端路由名称 */
    private String routeName;
    /** 路由参数 */
    private Object params;
    /** 路由查询参数 */
    private Object query;
    /** 地图操作 */
    private String mapAction;
    /** 缩放级别 */
    private Integer zoom;
    /** API 路径（CALL_API 时） */
    private String apiPath;
    /** API 请求体（CALL_API 时） */
    private Object apiBody;
    /** 前端业务函数名称（CALL_FUNCTION 时） */
    private String functionName;
    /** 前端业务函数参数（CALL_FUNCTION 时） */
    private Object arguments;
    /** 车牌号 */
    private String plate;
    /** 是否需要前端二次确认 */
    private Boolean needConfirm;
    /** 前端确认弹窗文案 */
    private String confirmText;
}
