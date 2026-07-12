package com.sky.logistics.mapper;

import com.sky.logistics.entity.LogisticsUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LogisticsUserMapper {

    List<LogisticsUser> findPage(@Param("role") String role,
                                 @Param("keyword") String keyword,
                                 @Param("offset") Integer offset,
                                 @Param("limit") Integer limit);

    Long count(@Param("role") String role,
               @Param("keyword") String keyword);

    LogisticsUser findByUsername(@Param("username") String username);

    LogisticsUser findById(@Param("id") String id);

    void insert(LogisticsUser user);

    int update(LogisticsUser user);

    int deleteById(@Param("id") String id);
}
