package com.sky.logistics.service;

import com.sky.logistics.vo.LogisticsUserContextVO;

public interface LogisticsSecurityService {

    LogisticsUserContextVO currentOrDefault(String authorization, String defaultUserId, String defaultRole);

    void rejectRole(String authorization, String role);

    void requireAnyRole(String authorization, String... roles);
}
