package com.sky.logistics.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 伴生云途宠物托运 Agent 暴露给 LLM 的真实业务工具目录。 */
public final class AgentToolCatalog {

    private AgentToolCatalog() {}

    private static final Set<String> READ_ONLY = new HashSet<>(Arrays.asList(
            "navigate_page", "locate_vehicle", "query_vehicle", "query_cargo", "query_alerts"
    ));

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    public static List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        add(tools, "navigate_page", "打开系统页面。用户只说查看、打开、进入某模块时使用。",
                props("page", enums("目标页面", "Dashboard", "FleetOverview", "CargoTracking", "PersonnelManagement", "DriverTasks", "SmartAssistant", "WarehouseManagement", "RiskCenter", "NavigationPortal")), req("page"));
        add(tools, "locate_vehicle", "在地图上定位指定车辆。", props("plate", str("完整车牌号")), req("plate"));
        add(tools, "dispatch_vehicle", "向车辆下发改道、停车、返程或提醒调度指令。",
                props("plate", str("完整车牌号"), "commandType", enums("指令类型", "REROUTE", "STOP", "RETURN", "NOTIFY"), "content", str("给司机的具体指令内容")), req("plate", "commandType"));
        add(tools, "create_vehicle", "新增车辆。", props("plate", str("车牌号"), "driver", str("司机姓名"), "phone", str("手机号"), "vehicleType", str("车辆类型"), "capacity", num("载重吨数")), req("plate"));
        add(tools, "update_vehicle", "修改车辆司机、电话、状态、设备或载重。", props("plate", str("车牌号"), "driver", str("司机姓名"), "phone", str("手机号"), "status", enums("车辆状态", "IN_TRANSIT", "IDLE", "OFFLINE"), "deviceImei", str("设备IMEI"), "capacity", num("载重吨数")), req("plate"));
        add(tools, "delete_vehicle", "删除车辆并解除相关宠物托运任务安排。", props("plate", str("车牌号")), req("plate"));
        add(tools, "create_cargo", "新增宠物托运任务。", props("cargoId", str("托运任务编号"), "name", str("宠物称呼"), "category", str("宠物种类或品种"), "origin", str("接宠地点"), "destination", str("交接地点"), "weight", num("宠物体重")), req("cargoId"));
        add(tools, "bind_cargo_vehicle", "把指定宠物托运任务安排到指定车辆。", props("cargoId", str("托运任务编号"), "plate", str("车牌号")), req("cargoId", "plate"));
        add(tools, "unbind_cargo_vehicle", "解除宠物托运任务与车辆安排。", props("cargoId", str("托运任务编号")), req("cargoId"));
        add(tools, "update_cargo_status", "更新宠物旅程状态。", props("cargoId", str("托运任务编号"), "status", enums("宠物旅程状态", "CREATED", "LOADED", "IN_TRANSIT", "DELIVERED", "CANCELLED"), "remark", str("照护备注")), req("cargoId", "status"));
        add(tools, "confirm_receipt", "确认宠物已安全交接。", props("cargoId", str("托运任务编号")), req("cargoId"));
        add(tools, "submit_address_change", "提交宠物交接地址变更申请。", props("cargoId", str("托运任务编号"), "address", str("新交接地址"), "city", str("城市"), "lat", num("纬度"), "lng", num("经度"), "reason", str("改址原因")), req("cargoId", "address", "lat", "lng"));
        add(tools, "confirm_unload_address", "确认宠物交接地址。", props("cargoId", str("托运任务编号"), "address", str("宠物交接地址"), "lat", num("纬度"), "lng", num("经度"), "remark", str("备注")), req("cargoId", "address", "lat", "lng"));
        add(tools, "report_unload_abnormal", "上报宠物交接地址异常。", props("cargoId", str("托运任务编号"), "reason", str("异常原因"), "currentAddress", str("当前位置"), "lat", num("纬度"), "lng", num("经度")), req("cargoId", "reason"));
        add(tools, "rate_driver", "评价已完成宠物旅程的司机与照护服务。", props("cargoId", str("托运任务编号"), "score", num("1到5分"), "comment", str("评价内容")), req("cargoId", "score"));
        add(tools, "approve_address_change", "审核通过改址申请。", props("requestId", str("改址申请编号"), "remark", str("审核备注")), req("requestId"));
        add(tools, "reject_address_change", "驳回改址申请。", props("requestId", str("改址申请编号"), "reason", str("驳回原因"), "suggestion", str("修改建议")), req("requestId", "reason"));
        add(tools, "driver_confirm_address_change", "司机确认或拒绝改址申请。", props("requestId", str("改址申请编号"), "confirmed", bool("是否确认"), "remark", str("备注")), req("requestId", "confirmed"));
        add(tools, "acknowledge_alert", "确认指定告警；未给编号时确认最新待处理告警。", props("alertId", str("告警编号，可省略"), "severity", enums("告警级别", "CRITICAL", "WARNING", "INFO"), "remark", str("备注")), Collections.emptyList());
        add(tools, "resolve_alert", "关闭指定告警；未给编号时关闭最新可处理告警。", props("alertId", str("告警编号，可省略"), "resolution", str("处理结果")), Collections.emptyList());
        add(tools, "delete_resolved_alert", "删除并归档已关闭告警。", props("alertId", str("告警编号，可省略")), Collections.emptyList());
        add(tools, "execute_dispatch_command", "司机确认执行收到的调度指令。", props("commandId", str("指令编号；省略时执行最新收到的指令")), Collections.emptyList());
        add(tools, "add_driver", "新增司机档案。", props("name", str("司机姓名"), "phone", str("手机号")), req("name", "phone"));
        add(tools, "delete_driver", "删除司机档案并清空相关车辆司机信息。", props("name", str("司机姓名或手机号")), req("name"));
        add(tools, "mark_notifications_read", "将一条或全部通知设为已读。", props("notificationId", str("通知编号；省略表示全部")), Collections.emptyList());
        add(tools, "clear_read_notifications", "清除所有已读通知。", props(), Collections.emptyList());
        add(tools, "simulate_alert", "生成一条本地演示告警。", props(), Collections.emptyList());
        add(tools, "reset_demo_data", "重置全部本地演示数据。", props(), Collections.emptyList());
        add(tools, "query_vehicle", "查询指定车辆的司机、位置、速度和状态。", props("plate", str("车牌号")), req("plate"));
        add(tools, "query_cargo", "查询指定宠物旅程的路线和运输状态。", props("cargoId", str("托运任务编号")), req("cargoId"));
        add(tools, "query_alerts", "查询符合状态或级别条件的告警数量和列表。", props("status", enums("告警状态", "PENDING", "ACKNOWLEDGED", "RESOLVED"), "severity", enums("告警级别", "CRITICAL", "WARNING", "INFO")), Collections.emptyList());
        return tools;
    }

    public static boolean contains(String name) { return DESCRIPTIONS.containsKey(name); }
    public static boolean requiresConfirmation(String name) { return !READ_ONLY.contains(name); }
    public static String description(String name) { return DESCRIPTIONS.getOrDefault(name, "执行伴生云途宠物托运业务操作"); }

    private static void add(List<Map<String, Object>> tools, String name, String description,
                            Map<String, Object> properties, List<String> required) {
        DESCRIPTIONS.put(name, description);
        Map<String, Object> parameters = map("type", "object", "properties", properties, "additionalProperties", false);
        if (!required.isEmpty()) parameters.put("required", required);
        Map<String, Object> function = map("name", name, "description", description, "parameters", parameters);
        tools.add(map("type", "function", "function", function));
    }

    private static Map<String, Object> props(Object... values) { return map(values); }
    private static Map<String, Object> str(String description) { return map("type", "string", "description", description); }
    private static Map<String, Object> num(String description) { return map("type", "number", "description", description); }
    private static Map<String, Object> bool(String description) { return map("type", "boolean", "description", description); }
    private static Map<String, Object> enums(String description, String... values) { return map("type", "string", "description", description, "enum", Arrays.asList(values)); }
    private static List<String> req(String... values) { return Arrays.asList(values); }

    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> map(Object... values) {
        Map<String, T> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) result.put(String.valueOf(values[i]), (T) values[i + 1]);
        return result;
    }
}
