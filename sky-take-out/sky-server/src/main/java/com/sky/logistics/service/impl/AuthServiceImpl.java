package com.sky.logistics.service.impl;

import com.sky.constant.JwtClaimsConstant;
import com.sky.logistics.common.LogisticsAuthException;
import com.sky.logistics.dto.FaceLoginDTO;
import com.sky.logistics.dto.FaceRegisterDTO;
import com.sky.logistics.dto.LoginDTO;
import com.sky.logistics.entity.FaceBinding;
import com.sky.logistics.entity.LogisticsUser;
import com.sky.logistics.mapper.FaceAuthMapper;
import com.sky.logistics.mapper.LogisticsUserMapper;
import com.sky.logistics.service.AuthService;
import com.sky.logistics.service.BaiduFaceService;
import com.sky.logistics.service.FaceImageStorageService;
import com.sky.logistics.vo.FaceBindingVO;
import com.sky.logistics.vo.FaceStatusVO;
import com.sky.logistics.vo.LoginUserVO;
import com.sky.logistics.vo.LoginVO;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final LogisticsUserMapper userMapper;
    private final JwtProperties jwtProperties;
    private final BaiduFaceService baiduFaceService;
    private final FaceAuthMapper faceAuthMapper;
    private final FaceImageStorageService faceImageStorageService;

    public AuthServiceImpl(LogisticsUserMapper userMapper,
                           JwtProperties jwtProperties,
                           BaiduFaceService baiduFaceService,
                           FaceAuthMapper faceAuthMapper,
                           FaceImageStorageService faceImageStorageService) {
        this.userMapper = userMapper;
        this.jwtProperties = jwtProperties;
        this.baiduFaceService = baiduFaceService;
        this.faceAuthMapper = faceAuthMapper;
        this.faceImageStorageService = faceImageStorageService;
    }

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        if (loginDTO == null || !StringUtils.hasText(loginDTO.getUsername()) || !StringUtils.hasText(loginDTO.getPassword())) {
            throw new LogisticsAuthException("用户名或密码不能为空");
        }

        LogisticsUser user = userMapper.findByUsername(loginDTO.getUsername());

        if (user == null) {
            throw new LogisticsAuthException("用户名或密码错误");
        }

        String passwordHash = md5(loginDTO.getPassword());

        if (!passwordHash.equalsIgnoreCase(user.getPasswordHash())) {
            throw new LogisticsAuthException("用户名或密码错误");
        }

        return buildLoginVO(user);
    }

    @Override
    public LoginVO faceLogin(FaceLoginDTO faceLoginDTO) {
        if (faceLoginDTO == null || !StringUtils.hasText(faceLoginDTO.getImageBase64())) {
            throw new LogisticsAuthException("未识别到有效人脸，请正对摄像头重试");
        }

        BaiduFaceService.FaceMatchResult result = baiduFaceService.search(faceLoginDTO.getImageBase64());
        LogisticsUser user = StringUtils.hasText(result.getUserId()) ? userMapper.findById(result.getUserId()) : null;
        if (user == null && StringUtils.hasText(result.getUserId())) {
            user = userMapper.findByUsername(result.getUserId());
        }
        if (user == null && StringUtils.hasText(result.getUserId())) {
            FaceBinding binding = faceAuthMapper.findBindingByBaiduUserId(baiduFaceService.defaultGroupId(), result.getUserId());
            if (binding != null) {
                user = userMapper.findById(binding.getUserId());
            }
        }
        boolean success = result.isPassed() && user != null;
        insertFaceLog(user == null ? result.getUserId() : user.getId(), result.getConfidence(), success, result.getReason(), faceLoginDTO.getDeviceId());

        if (!success) {
            if (user == null && StringUtils.hasText(result.getUserId())) {
                throw new LogisticsAuthException("百度匹配到的人脸未绑定系统用户：" + result.getUserId());
            }
            throw new LogisticsAuthException(faceFailureMessage(result));
        }

        LoginVO loginVO = buildLoginVO(user);
        loginVO.setFace(toMap(
                "baiduUserId", result.getUserId(),
                "confidence", result.getConfidence(),
                "threshold", result.getThreshold(),
                "passed", true
        ));
        return loginVO;
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new LogisticsAuthException("刷新令牌不能为空");
        }

        Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), refreshToken);
        String userId = String.valueOf(claims.get(JwtClaimsConstant.USER_ID));
        LogisticsUser user = userMapper.findById(userId);
        if (user == null) {
            throw new LogisticsAuthException("用户不存在");
        }

        return buildLoginVO(user);
    }

    @Override
    public LoginUserVO currentUser(String authorization) {
        String token = extractBearerToken(authorization);
        if (!StringUtils.hasText(token)) {
            throw new LogisticsAuthException("未登录");
        }

        Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
        String userId = String.valueOf(claims.get(JwtClaimsConstant.USER_ID));
        LogisticsUser user = userMapper.findById(userId);

        if (user == null) {
            throw new LogisticsAuthException("用户不存在");
        }

        return buildUserVO(user);
    }

    @Override
    public FaceStatusVO faceStatus(String userId) {
        LogisticsUser user = requireUser(userId);
        FaceBinding binding = faceAuthMapper.findBinding(user.getId());
        return FaceStatusVO.builder()
                .userId(user.getId())
                .bound(binding != null)
                .groupId(binding == null ? null : binding.getGroupId())
                .baiduUserId(binding == null ? null : binding.getBaiduUserId())
                .faceImageUrl(binding == null ? null : binding.getFaceImageUrl())
                .lastUpdatedAt(binding == null ? null : binding.getLastUpdatedAt())
                .build();
    }

    @Override
    public FaceBindingVO registerFace(String userId, FaceRegisterDTO request) {
        return saveFace(userId, request, false);
    }

    @Override
    public FaceBindingVO updateFace(String userId, FaceRegisterDTO request) {
        return saveFace(userId, request, true);
    }

    @Override
    public FaceBindingVO deleteFace(String userId) {
        LogisticsUser user = requireUser(userId);
        FaceBinding binding = faceAuthMapper.findBinding(user.getId());
        String groupId = binding == null ? baiduFaceService.defaultGroupId() : binding.getGroupId();
        String baiduUserId = binding == null ? baiduFaceUserId(user) : binding.getBaiduUserId();
        baiduFaceService.delete(baiduUserId, groupId);
        faceAuthMapper.deleteBinding(user.getId());
        if (binding != null) {
            faceImageStorageService.deleteByUrl(binding.getFaceImageUrl());
        }
        return FaceBindingVO.builder()
                .userId(user.getId())
                .baiduUserId(baiduUserId)
                .groupId(groupId)
                .deleted(true)
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private FaceBindingVO saveFace(String userId, FaceRegisterDTO request, boolean update) {
        LogisticsUser user = requireUser(userId);
        if (request == null || !StringUtils.hasText(request.getImageBase64())) {
            throw new IllegalArgumentException("imageBase64 不能为空");
        }
        String groupId = StringUtils.hasText(request.getGroupId()) ? request.getGroupId() : baiduFaceService.defaultGroupId();
        String baiduUserId = baiduFaceUserId(user);
        FaceBinding oldBinding = faceAuthMapper.findBinding(user.getId());
        FaceImageStorageService.StoredFaceImage storedImage = faceImageStorageService.save(user.getId(), request.getImageBase64());
        try {
            if (update) {
                baiduFaceService.update(baiduUserId, request.getImageBase64(), groupId);
            } else {
                baiduFaceService.register(baiduUserId, request.getImageBase64(), groupId);
            }
        } catch (RuntimeException e) {
            faceImageStorageService.deleteByUrl(storedImage.getUrl());
            throw e;
        }
        faceAuthMapper.deleteBinding(user.getId());
        faceAuthMapper.insertBinding(
                "UFB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                user.getId(),
                groupId,
                baiduUserId,
                storedImage.getUrl(),
                storedImage.getObjectKey()
        );
        if (oldBinding != null) {
            faceImageStorageService.deleteByUrl(oldBinding.getFaceImageUrl());
        }
        OffsetDateTime now = OffsetDateTime.now();
        return FaceBindingVO.builder()
                .userId(user.getId())
                .baiduUserId(baiduUserId)
                .groupId(groupId)
                .faceImageUrl(storedImage.getUrl())
                .registeredAt(update ? null : now)
                .updatedAt(update ? now : null)
                .deleted(false)
                .build();
    }

    private String baiduFaceUserId(LogisticsUser user) {
        String source = StringUtils.hasText(user.getUsername()) ? user.getUsername() : user.getId();
        String sanitized = source.replaceAll("[^A-Za-z0-9_]", "_");
        if (!StringUtils.hasText(sanitized)) {
            sanitized = "user_" + Math.abs(user.getId().hashCode());
        }
        return sanitized.length() > 128 ? sanitized.substring(0, 128) : sanitized;
    }

    private String faceFailureMessage(BaiduFaceService.FaceMatchResult result) {
        String reason = result == null ? "" : String.valueOf(result.getReason());
        if ("No permission to access data".equalsIgnoreCase(reason)) {
            return "百度人脸接口无数据访问权限，请确认该 API Key 已开通人脸识别/人脸库权限";
        }
        if ("NO_FACE_MATCHED".equalsIgnoreCase(reason) || reason.toLowerCase().contains("match user is not found")) {
            return "未匹配到已注册人脸，请先绑定人脸后再登录";
        }
        if (reason.toLowerCase().contains("pic not has face")) {
            return "图片中未检测到人脸，请正对摄像头重试";
        }
        if (reason.toLowerCase().contains("quality") || reason.toLowerCase().contains("fuzzy")) {
            return "人脸图片质量不足，请保持光线充足并正对摄像头";
        }
        if (reason.toLowerCase().contains("liveness")) {
            return "活体检测未通过，请本人正对摄像头重试";
        }
        if (result != null && result.getConfidence() != null && result.getThreshold() != null) {
            return "人脸匹配度不足，当前 " + result.getConfidence() + "，要求 " + result.getThreshold();
        }
        return StringUtils.hasText(reason) ? reason : "人脸识别失败，请稍后重试";
    }

    private LogisticsUser requireUser(String userId) {
        LogisticsUser user = userMapper.findById(userId);
        if (user == null) user = userMapper.findByUsername(userId);
        if (user == null) throw new LogisticsAuthException("用户不存在");
        return user;
    }

    private void insertFaceLog(String userId, BigDecimal confidence, boolean success, String reason, String deviceId) {
        try {
            faceAuthMapper.insertLog(
                    "FLL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    userId,
                    confidence,
                    success,
                    reason,
                    null,
                    deviceId
            );
        } catch (Exception ignored) {
            // 日志表未初始化时不阻断登录主链路。
        }
    }

    private LoginVO buildLoginVO(LogisticsUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        claims.put(JwtClaimsConstant.USERNAME, user.getUsername());
        claims.put(JwtClaimsConstant.NAME, user.getName());
        claims.put(JwtClaimsConstant.PHONE, user.getPhone());
        claims.put(JwtClaimsConstant.ROLE, user.getRole());

        String accessToken = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims
        );

        String refreshToken = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl() * 24,
                claims
        );

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn((int) (jwtProperties.getAdminTtl() / 1000))
                .user(buildUserVO(user))
                .build();
    }

    private LoginUserVO buildUserVO(LogisticsUser user) {
        return LoginUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .phone(user.getPhone())
                .permissions(permissionsOf(user.getRole()))
                .build();
    }

    private List<String> permissionsOf(String role) {
        if ("DISPATCHER".equals(role)) {
            return Arrays.asList("VEHICLE_READ", "COMMAND_SEND", "ALERT_READ");
        }
        if ("WAREHOUSE".equals(role)) {
            return Arrays.asList("VEHICLE_WRITE", "CARGO_WRITE", "BINDING_WRITE");
        }
        if ("SHIPPER".equals(role)) {
            return Arrays.asList("CARGO_READ", "POSITION_READ", "ASSISTANT_CHAT");
        }
        if ("ADMIN".equals(role)) {
            return Arrays.asList("SYSTEM_ADMIN", "VEHICLE_WRITE", "CARGO_WRITE", "ALERT_WRITE");
        }
        if ("DRIVER".equals(role)) {
            return Arrays.asList("TASK_READ", "STATUS_REPORT");
        }
        return Collections.emptyList();
    }

    private Map<String, Object> toMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return map;
    }

    private String md5(String rawPassword) {
        return DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
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
}
