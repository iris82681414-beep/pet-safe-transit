package com.sky.logistics.service;

import com.sky.logistics.common.PageResponse;
import com.sky.logistics.dto.UserCreateDTO;
import com.sky.logistics.dto.UserQueryDTO;
import com.sky.logistics.dto.UserUpdateDTO;
import com.sky.logistics.vo.UserVO;

public interface UserService {

    PageResponse<UserVO> page(UserQueryDTO queryDTO);

    UserVO detail(String id);

    UserVO create(UserCreateDTO createDTO);

    UserVO update(String id, UserUpdateDTO updateDTO);

    void delete(String id);
}
