package com.sky.logistics.common;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@ApiModel("统一响应")
public class ApiResponse<T> {

    @ApiModelProperty(value = "业务状态码，0 表示成功", example = "0")
    private Integer code;

    @ApiModelProperty(value = "响应消息", example = "success")
    private String message;

    @ApiModelProperty("响应数据")
    private T data;

    @ApiModelProperty(value = "响应时间", example = "2026-06-29T06:15:00Z")
    private String timestamp;

    @ApiModelProperty(value = "请求追踪 ID")
    private String requestId;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = base();
        response.setCode(0);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(Integer code, String message, T data) {
        ApiResponse<T> response = base();
        response.setCode(code);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    private static <T> ApiResponse<T> base() {
        ApiResponse<T> response = new ApiResponse<>();
        response.setTimestamp(Instant.now().toString());
        response.setRequestId(UUID.randomUUID().toString());
        return response;
    }
}
