package com.sky.logistics.service;

import com.sky.logistics.dto.FaceLoginDTO;
import com.sky.logistics.dto.FaceRegisterDTO;
import com.sky.logistics.dto.LoginDTO;
import com.sky.logistics.vo.FaceBindingVO;
import com.sky.logistics.vo.FaceStatusVO;
import com.sky.logistics.vo.LoginUserVO;
import com.sky.logistics.vo.LoginVO;

public interface AuthService {

    LoginVO login(LoginDTO loginDTO);

    LoginVO faceLogin(FaceLoginDTO faceLoginDTO);

    LoginVO refresh(String refreshToken);

    LoginUserVO currentUser(String authorization);

    FaceStatusVO faceStatus(String userId);

    FaceBindingVO registerFace(String userId, FaceRegisterDTO request);

    FaceBindingVO updateFace(String userId, FaceRegisterDTO request);

    FaceBindingVO deleteFace(String userId);
}
