package com.sky.logistics.mapper;

import com.sky.logistics.entity.Command;
import com.sky.logistics.entity.CommandLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommandMapper {

    int insert(Command command);

    int insertLog(CommandLog log);

    Command findByCommandId(@Param("commandId") String commandId);

    List<Command> findByPlate(@Param("plate") String plate,
                              @Param("offset") Integer offset,
                              @Param("limit") Integer limit);

    Long countByPlate(@Param("plate") String plate);

    int updateStatus(@Param("commandId") String commandId,
                     @Param("status") String status);

    List<CommandLog> findLogsByCommandId(@Param("commandId") String commandId);
}
