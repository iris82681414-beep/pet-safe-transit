package com.sky.logistics.service.impl;

import com.sky.logistics.common.PageResponse;
import com.sky.logistics.dto.UserCreateDTO;
import com.sky.logistics.dto.UserQueryDTO;
import com.sky.logistics.dto.UserUpdateDTO;
import com.sky.logistics.entity.LogisticsUser;
import com.sky.logistics.mapper.LogisticsUserMapper;
import com.sky.logistics.service.UserService;
import com.sky.logistics.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private static final Set<String> ROLES = new HashSet<>(
            Arrays.asList("SHIPPER", "DISPATCHER", "WAREHOUSE", "ADMIN", "DRIVER")
    );

    private final LogisticsUserMapper userMapper;

    public UserServiceImpl(LogisticsUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public PageResponse<UserVO> page(UserQueryDTO queryDTO) {
        int page = normalizePage(queryDTO == null ? null : queryDTO.getPage());
        int size = normalizeSize(queryDTO == null ? null : queryDTO.getSize());
        int offset = (page - 1) * size;
        String role = normalizeRoleOrNull(queryDTO == null ? null : queryDTO.getRole());
        String keyword = trimToNull(queryDTO == null ? null : queryDTO.getKeyword());

        Long total = userMapper.count(role, keyword);
        if (total == null || total == 0) {
            return new PageResponse<>(Collections.<UserVO>emptyList(), page, size, 0L, 0);
        }

        List<LogisticsUser> users = userMapper.findPage(role, keyword, offset, size);
        List<UserVO> content = users == null
                ? Collections.<UserVO>emptyList()
                : users.stream().map(this::toVO).collect(Collectors.toList());
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }

    @Override
    public UserVO detail(String id) {
        return toVO(findRequiredById(id));
    }

    @Override
    @Transactional
    public UserVO create(UserCreateDTO createDTO) {
        if (createDTO == null) {
            throw new IllegalArgumentException("用户信息不能为空");
        }

        String username = trimToNull(createDTO.getUsername());
        String password = trimToNull(createDTO.getPassword());
        String name = trimToNull(createDTO.getName());
        String role = trimToNull(createDTO.getRole());

        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        if (!StringUtils.hasText(role)) {
            throw new IllegalArgumentException("角色不能为空");
        }

        role = role.toUpperCase();
        if (!ROLES.contains(role)) {
            throw new IllegalArgumentException("角色不正确");
        }
        if (userMapper.findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        LogisticsUser user = new LogisticsUser();
        user.setId(newUserId());
        user.setUsername(username);
        user.setPasswordHash(md5(password));
        user.setName(name);
        user.setRole(role);
        user.setPhone(trimToNull(createDTO.getPhone()));

        userMapper.insert(user);
        return toVO(userMapper.findById(user.getId()));
    }

    @Override
    @Transactional
    public UserVO update(String id, UserUpdateDTO updateDTO) {
        if (updateDTO == null) {
            throw new IllegalArgumentException("用户信息不能为空");
        }

        LogisticsUser user = findRequiredById(id);
        String name = trimToNull(updateDTO.getName());
        String role = normalizeRoleOrNull(updateDTO.getRole());
        String password = trimToNull(updateDTO.getPassword());

        if (name != null) {
            user.setName(name);
        }
        if (role != null) {
            user.setRole(role);
        }
        user.setPhone(updateDTO.getPhone() == null ? user.getPhone() : trimToNull(updateDTO.getPhone()));
        if (password != null) {
            user.setPasswordHash(md5(password));
        }

        userMapper.update(user);
        return toVO(userMapper.findById(user.getId()));
    }

    @Override
    @Transactional
    public void delete(String id) {
        LogisticsUser user = findRequiredById(id);
        userMapper.deleteById(user.getId());
    }

    private UserVO toVO(LogisticsUser user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .phone(user.getPhone())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private String newUserId() {
        return "USR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String md5(String rawPassword) {
        return DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LogisticsUser findRequiredById(String id) {
        String safeId = trimToNull(id);
        if (!StringUtils.hasText(safeId)) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        LogisticsUser user = userMapper.findById(safeId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private String normalizeRoleOrNull(String role) {
        String safeRole = trimToNull(role);
        if (safeRole == null) {
            return null;
        }
        safeRole = safeRole.toUpperCase();
        if (!ROLES.contains(safeRole)) {
            throw new IllegalArgumentException("角色不正确");
        }
        return safeRole;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
