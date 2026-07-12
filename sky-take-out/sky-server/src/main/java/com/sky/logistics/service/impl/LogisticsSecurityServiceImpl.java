package com.sky.logistics.service.impl;

import com.sky.constant.JwtClaimsConstant;
import com.sky.logistics.common.LogisticsForbiddenException;
import com.sky.logistics.service.LogisticsSecurityService;
import com.sky.logistics.vo.LogisticsUserContextVO;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Service
public class LogisticsSecurityServiceImpl implements LogisticsSecurityService {

    private final JwtProperties jwtProperties;

    public LogisticsSecurityServiceImpl(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public LogisticsUserContextVO currentOrDefault(String authorization, String defaultUserId, String defaultRole) {
        Claims claims = parseClaims(authorization);
        if (claims == null) {
            return LogisticsUserContextVO.builder()
                    .userId(defaultUserId)
                    .role(defaultRole)
                    .name(defaultRole)
                    .build();
        }
        return LogisticsUserContextVO.builder()
                .userId(stringValue(claims.get(JwtClaimsConstant.USER_ID)))
                .username(stringValue(claims.get(JwtClaimsConstant.USERNAME)))
                .name(stringValue(claims.get(JwtClaimsConstant.NAME)))
                .role(stringValue(claims.get(JwtClaimsConstant.ROLE)))
                .build();
    }

    @Override
    public void rejectRole(String authorization, String role) {
        Claims claims = parseClaims(authorization);
        if (claims != null && role.equals(stringValue(claims.get(JwtClaimsConstant.ROLE)))) {
            throw new LogisticsForbiddenException("当前身份无权访问该接口");
        }
    }

    @Override
    public void requireAnyRole(String authorization, String... roles) {
        Claims claims = parseClaims(authorization);
        if (claims == null) {
            return;
        }
        String currentRole = stringValue(claims.get(JwtClaimsConstant.ROLE));
        if (!Arrays.asList(roles).contains(currentRole)) {
            throw new LogisticsForbiddenException("当前身份无权执行该操作");
        }
    }

    private Claims parseClaims(String authorization) {
        String token = extractBearerToken(authorization);
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length());
        }
        return authorization;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
