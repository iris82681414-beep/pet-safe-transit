package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.common.PageResponse;
import com.sky.logistics.dto.FaceLoginDTO;
import com.sky.logistics.dto.FaceRegisterDTO;
import com.sky.logistics.dto.LoginDTO;
import com.sky.logistics.dto.RefreshTokenRequest;
import com.sky.logistics.dto.UserCreateDTO;
import com.sky.logistics.dto.UserQueryDTO;
import com.sky.logistics.dto.UserUpdateDTO;
import com.sky.logistics.service.AuthService;
import com.sky.logistics.service.UserService;
import com.sky.logistics.vo.FaceBindingVO;
import com.sky.logistics.vo.FaceStatusVO;
import com.sky.logistics.vo.LoginUserVO;
import com.sky.logistics.vo.LoginVO;
import com.sky.logistics.vo.UserVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Api(tags = "智慧物流-认证与用户")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/auth/login")
    @ApiOperation("登录")
    public ApiResponse<LoginVO> login(@RequestBody LoginDTO loginDTO) {
        return ApiResponse.success(authService.login(loginDTO));
    }

    @PostMapping("/auth/face-login")
    @ApiOperation("人脸登录")
    public ApiResponse<LoginVO> faceLogin(@RequestBody FaceLoginDTO faceLoginDTO) {
        return ApiResponse.success(authService.faceLogin(faceLoginDTO));
    }

    @PostMapping("/auth/refresh")
    @ApiOperation("刷新 Token")
    public ApiResponse<LoginVO> refresh(@RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request == null ? null : request.getRefreshToken()));
    }

    @PostMapping("/auth/logout")
    @ApiOperation("登出")
    public ApiResponse<Void> logout() {
        return ApiResponse.success();
    }

    @GetMapping("/users/me")
    @ApiOperation("获取当前用户")
    public ApiResponse<LoginUserVO> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(authService.currentUser(authorization));
    }

    @GetMapping("/users/{id}/face/status")
    @ApiOperation("查询人脸绑定状态")
    public ApiResponse<FaceStatusVO> faceStatus(@PathVariable String id) {
        return ApiResponse.success(authService.faceStatus(id));
    }

    @PostMapping("/users/{id}/face/register")
    @ApiOperation("注册人脸")
    public ApiResponse<FaceBindingVO> registerFace(@PathVariable String id, @RequestBody FaceRegisterDTO request) {
        return ApiResponse.success(authService.registerFace(id, request));
    }

    @PutMapping("/users/{id}/face")
    @ApiOperation("更新人脸")
    public ApiResponse<FaceBindingVO> updateFace(@PathVariable String id, @RequestBody FaceRegisterDTO request) {
        return ApiResponse.success(authService.updateFace(id, request));
    }

    @DeleteMapping("/users/{id}/face")
    @ApiOperation("删除人脸")
    public ApiResponse<FaceBindingVO> deleteFace(@PathVariable String id) {
        return ApiResponse.success(authService.deleteFace(id));
    }

    @GetMapping("/users")
    @ApiOperation("获取用户列表")
    public ApiResponse<PageResponse<UserVO>> users(@RequestParam(required = false) String role,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        UserQueryDTO queryDTO = new UserQueryDTO();
        queryDTO.setRole(role);
        queryDTO.setKeyword(keyword);
        queryDTO.setPage(page);
        queryDTO.setSize(size);
        return ApiResponse.success(userService.page(queryDTO));
    }

    @GetMapping("/users/{id}")
    @ApiOperation("获取用户详情")
    public ApiResponse<UserVO> userDetail(@PathVariable String id) {
        return ApiResponse.success(userService.detail(id));
    }

    @PostMapping("/users")
    @ApiOperation("新增用户")
    public ApiResponse<UserVO> createUser(@RequestBody UserCreateDTO request) {
        return ApiResponse.success(userService.create(request));
    }

    @PutMapping("/users/{id}")
    @ApiOperation("修改用户")
    public ApiResponse<UserVO> updateUser(@PathVariable String id, @RequestBody UserUpdateDTO request) {
        return ApiResponse.success(userService.update(id, request));
    }

    @DeleteMapping("/users/{id}")
    @ApiOperation("删除用户")
    public ApiResponse<Void> deleteUser(@PathVariable String id) {
        userService.delete(id);
        return ApiResponse.success();
    }
}
