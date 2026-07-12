package com.sky.logistics.mapper;

import com.sky.logistics.entity.VoiceCommandLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VoiceCommandLogMapper {

    int insert(VoiceCommandLog log);

    int updateExecuted(@Param("id") String id,
                       @Param("executed") Boolean executed);

    List<VoiceCommandLog> findPage(@Param("userId") String userId,
                                   @Param("intent") String intent,
                                   @Param("offset") Integer offset,
                                   @Param("limit") Integer limit);

    Long count(@Param("userId") String userId,
               @Param("intent") String intent);

    VoiceCommandLog findById(@Param("id") String id);
}
