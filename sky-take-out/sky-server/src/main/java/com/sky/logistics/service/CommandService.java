package com.sky.logistics.service;

import java.util.Map;

public interface CommandService {

    Map<String, Object> createCommand(String plate, String commandType, String priority,
                                       Map<String, Object> payload, String createdBy);

    Map<String, Object> getCommandDetail(String plate, String commandId);

    Map<String, Object> listCommands(String plate, Integer page, Integer size);
}
