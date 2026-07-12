package com.sky.logistics.mapper;

import com.sky.logistics.entity.FaceBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface FaceAuthMapper {

    FaceBinding findBinding(@Param("userId") String userId);

    FaceBinding findBindingByBaiduUserId(@Param("groupId") String groupId,
                                         @Param("baiduUserId") String baiduUserId);

    void deleteBinding(@Param("userId") String userId);

    void insertBinding(@Param("id") String id,
                       @Param("userId") String userId,
                       @Param("groupId") String groupId,
                       @Param("baiduUserId") String baiduUserId,
                       @Param("faceImageUrl") String faceImageUrl,
                       @Param("faceImageObjectKey") String faceImageObjectKey);

    void insertLog(@Param("id") String id,
                   @Param("userId") String userId,
                   @Param("confidence") BigDecimal confidence,
                   @Param("success") Boolean success,
                   @Param("reason") String reason,
                   @Param("ip") String ip,
                   @Param("deviceId") String deviceId);
}
