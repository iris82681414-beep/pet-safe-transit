package com.sky.logistics.common;

import lombok.extern.slf4j.Slf4j;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.sky.logistics")
@Slf4j
public class LogisticsExceptionHandler {

    @ExceptionHandler(LogisticsAuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Map<String, Object>> handleAuth(LogisticsAuthException exception) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reason", exception.getMessage());
        return ApiResponse.error(40101, exception.getMessage(), data);
    }

    @ExceptionHandler(LogisticsForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Map<String, Object>> handleForbidden(LogisticsForbiddenException exception) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reason", exception.getMessage());
        return ApiResponse.error(40301, exception.getMessage(), data);
    }

    @ExceptionHandler({IllegalArgumentException.class, JwtException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, Object>> handleBadRequest(Exception exception) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reason", exception.getMessage());
        return ApiResponse.error(40001, exception.getMessage(), data);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Map<String, Object>> handle(Exception exception) {
        log.error("logistics server error", exception);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reason", exception.getMessage());
        return ApiResponse.error(50001, "Server error", data);
    }
}
