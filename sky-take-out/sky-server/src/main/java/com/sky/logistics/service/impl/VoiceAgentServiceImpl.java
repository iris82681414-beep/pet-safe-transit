package com.sky.logistics.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.logistics.dto.AgentActionVO;
import com.sky.logistics.dto.AgentCommandRequest;
import com.sky.logistics.entity.VoiceCommandLog;
import com.sky.logistics.mapper.VoiceCommandLogMapper;
import com.sky.logistics.service.VoiceAgentService;
import com.sky.logistics.service.AgentToolCatalog;
import com.sky.logistics.service.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class VoiceAgentServiceImpl implements VoiceAgentService {

    private final VoiceCommandLogMapper logMapper;
    private final LLMService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 车牌正则 */
    private static final Pattern PLATE_PATTERN = Pattern.compile(
            "([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼]" +
            "[A-Z]\\s*[·\\-\\s]?\\s*[A-Z0-9]{4,5})");

    /** 订单号正则：SH-HZ-20260629-0291 / SH20260629001 */
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile(
            "([A-Z]{2,4}-[A-Z]{2,4}-\\d{8}-\\d{4})|([A-Z]{2}\\d{11})");

    /** 需要调度员及以上权限的指令 */
    private static final List<String> DISPATCHER_COMMANDS = Arrays.asList(
            "SEND_REROUTE_COMMAND", "SEND_NOTIFICATION", "CLOSE_ORDER",
            "IGNORE_ALERT", "FORCE_STOP_VEHICLE");

    private static final List<String> PAGE_WHITELIST = Arrays.asList(
            "Dashboard", "FleetOverview", "CargoTracking",
            "OrderList", "OrderDetail", "DeviceStatus",
            "RiskCenter", "DriverRating", "AddressChangeReview",
            "UnloadAddressConfirm", "PersonnelManagement", "DriverTasks",
            "SmartAssistant", "WarehouseManagement", "NavigationPortal"
    );

    public VoiceAgentServiceImpl(VoiceCommandLogMapper logMapper, LLMService llmService) {
        this.logMapper = logMapper;
        this.llmService = llmService;
    }

    @Override
    public Map<String, Object> textToAction(AgentCommandRequest request, String userId) {
        String text = (request != null) ? trim(request.getText()) : null;
        if (!StringUtils.hasText(text)) {
            return buildResponse(null, "NOOP", "请说出您的指令", null, false);
        }

        log.info("Agent 收到文本: {}, userId={}", text, userId);

        Map<String, Object> llmResult = tryLlmFunctionCalling(request, text);
        if (llmResult != null) return llmResult;

        String plate = extractPlate(text);
        String orderId = extractOrderId(text);

        // 标准函数调用：后端只负责把自然语言整理成函数名和参数，
        // 前端统一函数注册表负责校验、二次确认并调用真实 store/API。
        if (plate != null && orderId != null && containsAny(text, "调度货物", "分配货物", "绑定车辆", "装到", "装入", "派给")) {
            return buildFunction(text, "BIND_CARGO_VEHICLE", "bind_cargo_vehicle",
                    toMap("cargoId", orderId, "plate", plate),
                    "即将把货物 " + orderId + " 调度到 " + plate, true);
        }
        if (orderId != null && containsAny(text, "解除绑定", "取消绑定", "移出车辆", "卸下货物")) {
            return buildFunction(text, "UNBIND_CARGO_VEHICLE", "unbind_cargo_vehicle",
                    toMap("cargoId", orderId), "即将解除货物 " + orderId + " 的车辆绑定", true);
        }
        if (orderId != null && containsAny(text, "确认收货", "完成收货", "货物签收", "签收货物")) {
            return buildFunction(text, "CONFIRM_RECEIPT", "confirm_receipt",
                    toMap("cargoId", orderId), "即将确认货物 " + orderId + " 收货", true);
        }
        if (orderId != null && containsAny(text, "设为已装货", "改成已装货", "开始运输", "设为运输中", "取消货物", "取消订单", "关闭订单")) {
            String status = containsAny(text, "已装货") ? "LOADED"
                    : containsAny(text, "运输中", "开始运输") ? "IN_TRANSIT" : "CANCELLED";
            return buildFunction(text, "UPDATE_CARGO_STATUS", "update_cargo_status",
                    toMap("cargoId", orderId, "status", status, "remark", "由语音 Agent 更新"),
                    "即将更新货物 " + orderId + " 状态", true);
        }
        if (plate != null && containsAny(text, "新增车辆", "添加车辆", "创建车辆")) {
            return buildFunction(text, "CREATE_VEHICLE", "create_vehicle", toMap("plate", plate),
                    "即将新增车辆 " + plate, true);
        }
        if (plate != null && containsAny(text, "删除车辆", "移除车辆")) {
            return buildFunction(text, "DELETE_VEHICLE", "delete_vehicle", toMap("plate", plate),
                    "即将删除车辆 " + plate, true);
        }
        if (containsAny(text, "确认告警", "处理告警", "接收告警", "确认最新告警", "处理最新告警")) {
            return buildFunction(text, "ACKNOWLEDGE_ALERT", "acknowledge_alert", new HashMap<>(),
                    "即将确认最新待处理告警", true);
        }
        if (containsAny(text, "关闭告警", "解决告警", "消除告警", "关闭最新告警", "解决最新告警")) {
            return buildFunction(text, "RESOLVE_ALERT", "resolve_alert", new HashMap<>(),
                    "即将关闭最新可处理告警", true);
        }
        if (containsAny(text, "删除告警", "移除告警")) {
            return buildFunction(text, "DELETE_RESOLVED_ALERT", "delete_resolved_alert", new HashMap<>(),
                    "即将删除并归档最新已关闭告警", true);
        }
        if (containsAny(text, "执行调度指令", "执行最新指令", "确认执行指令")) {
            return buildFunction(text, "EXECUTE_DISPATCH_COMMAND", "execute_dispatch_command", new HashMap<>(),
                    "即将执行最新收到的调度指令", true);
        }
        if (containsAny(text, "全部通知已读", "所有通知已读")) {
            return buildFunction(text, "MARK_NOTIFICATIONS_READ", "mark_notifications_read", new HashMap<>(),
                    "即将把全部通知设为已读", true);
        }
        if (containsAny(text, "清除已读通知", "删除已读通知")) {
            return buildFunction(text, "CLEAR_READ_NOTIFICATIONS", "clear_read_notifications", new HashMap<>(),
                    "即将清除已读通知", true);
        }
        if (containsAny(text, "模拟告警", "生成演示告警")) {
            return buildFunction(text, "SIMULATE_ALERT", "simulate_alert", new HashMap<>(),
                    "即将生成一条演示告警", true);
        }
        if (containsAny(text, "重置演示数据", "恢复演示数据")) {
            return buildFunction(text, "RESET_DEMO_DATA", "reset_demo_data", new HashMap<>(),
                    "即将重置本地演示数据", true);
        }

        // ═══════════════════════════════════════════════════════
        // 1. SYSTEM 系统交互
        // ═══════════════════════════════════════════════════════
        if (containsAny(text, "刷新", "重新加载")) {
            return buildNavigate(text, "RELOAD_PAGE", "_REFRESH", "刷新页面");
        }
        if (containsAny(text, "返回","退回")) {
            return buildNavigate(text, "GO_BACK", "_BACK", "返回上一页");
        }
        if (containsAny(text, "帮助", "怎么用")) {
            AgentActionVO action = AgentActionVO.builder()
                    .type("OPEN_MODAL").targetType("HELP").build();
            return buildResponse(text, "OPEN_HELP", "打开帮助文档", action, false);
        }

        // ═══════════════════════════════════════════════════════
        // 2. NAVIGATE 页面跳转
        // ═══════════════════════════════════════════════════════
        if (containsAny(text, "首页", "主页", "仪表盘", "dashboard")) {
            return buildNavigate(text, "OPEN_DASHBOARD", "Dashboard", "打开首页");
        }
        if (containsAny(text, "车辆调度", "车队", "车辆总览")) {
            return buildNavigate(text, "OPEN_FLEET_OVERVIEW", "FleetOverview", "打开车辆调度");
        }
        if (containsAny(text, "订单", "运单")) {
            if (orderId != null) {
                AgentActionVO action = AgentActionVO.builder()
                        .type("NAVIGATE").targetType("ORDER")
                        .routeName("OrderDetail").targetValue(orderId)
                        .params(toMap("orderId", orderId))
                        .query(toMap("orderId", orderId))
                        .build();
                return buildResponse(text, "OPEN_ORDER_DETAIL", "打开订单详情", action, false);
            }
            return buildNavigate(text, "OPEN_ORDER_LIST", "OrderList", "打开订单列表");
        }
        if (containsAny(text, "设备在线", "设备状态", "在线设备", "终端在线")
                || (containsAny(text, "在线") && containsAny(text, "车辆", "车"))) {
            return buildNavigate(text, "OPEN_DEVICE_STATUS", "DeviceStatus", "打开设备在线");
        }
        if (containsAny(text, "货物追踪", "追踪")) {
            return buildNavigate(text, "OPEN_CARGO_TRACKING", "CargoTracking", "打开货物追踪");
        }
        if (containsAny(text, "风险", "告警中心", "告警", "报警", "异常", "未处理告警")) {
            return buildNavigate(text, "OPEN_RISK_CENTER", "RiskCenter", "打开告警中心");
        }
        if (containsAny(text, "司机评分", "司机评价", "驾驶员评分", "驾驶员评价", "张建国评分")) {
            return buildNavigate(text, "OPEN_DRIVER_RATING", "DriverRating", "打开司机评分");
        }
        if (containsAny(text, "改址审核", "地址审核", "改地址审核")) {
            return buildNavigate(text, "OPEN_ADDRESS_CHANGE_REVIEW", "AddressChangeReview", "打开改址审核");
        }
        if (containsAny(text, "卸货地址", "卸货点", "卸货确认")) {
            return buildNavigate(text, "OPEN_UNLOAD_ADDRESS_CONFIRM", "UnloadAddressConfirm", "打开卸货地址确认");
        }

        // ═══════════════════════════════════════════════════════
        // 3. CALL_API 操作控制（需确认 + 角色校验）
        // ═══════════════════════════════════════════════════════
        if (containsAny(text, "下发改道", "改道")) {
            if (!hasRole(userId, "DISPATCHER")) {
                return buildResponse(text, "PERMISSION_DENIED",
                        "您没有权限执行此操作，请联系调度员", null, false);
            }
            if (plate == null) {
                return buildResponse(text, "NOOP",
                        "请指定目标车辆，例如：给沪A C0291下发改道指令", null, false);
            }
            AgentActionVO action = AgentActionVO.builder()
                    .type("CALL_FUNCTION").targetType("VEHICLE").plate(plate)
                    .functionName("dispatch_vehicle")
                    .arguments(toMap("plate", plate, "commandType", "REROUTE",
                            "content", "语音指令触发改道，请按调度建议路线行驶。"))
                    .build();
            return buildResponse(text, "SEND_REROUTE_COMMAND",
                    "即将向 " + plate + " 下发改道指令，确认执行？",
                    action, true);
        }

        if (containsAny(text, "下发通知", "发送提示", "发送消息")) {
            if (!hasRole(userId, "DISPATCHER")) {
                return buildResponse(text, "PERMISSION_DENIED",
                        "您没有权限执行此操作", null, false);
            }
            String msg = extractAfter(text, "通知", "提示", "消息");
            AgentActionVO action = AgentActionVO.builder()
                    .type("CALL_FUNCTION").targetType("VEHICLE").plate(plate)
                    .functionName("dispatch_vehicle")
                    .arguments(toMap("plate", plate, "commandType", "NOTIFY", "content", msg))
                    .build();
            return buildResponse(text, "SEND_NOTIFICATION",
                    plate != null ? "即将向 " + plate + " 发送通知" + (msg != null ? "：「" + msg + "」" : "") + "，确认？"
                            : "请指定目标车辆",
                    action, plate != null);
        }

        if (containsAny(text, "强制关闭", "关闭订单") && orderId != null) {
            if (!hasRole(userId, "DISPATCHER")) {
                return buildResponse(text, "PERMISSION_DENIED",
                        "您没有权限执行此操作", null, false);
            }
            AgentActionVO action = AgentActionVO.builder()
                    .type("CALL_API").targetType("ORDER").targetValue(orderId)
                    .apiPath("/cargo/" + orderId + "/status")
                    .apiBody(toMap("status", "CANCELLED"))
                    .build();
            return buildResponse(text, "CLOSE_ORDER",
                    "即将关闭订单 " + orderId + "，确认执行？", action, true);
        }

        // ═══════════════════════════════════════════════════════
        // 4. HIGHLIGHT_MAP_TARGET 地图定位
        // ═══════════════════════════════════════════════════════
        if (containsAny(text, "定位", "地图") && plate != null) {
            AgentActionVO action = AgentActionVO.builder()
                    .type("HIGHLIGHT_MAP_TARGET").targetType("VEHICLE")
                    .plate(plate).targetValue(plate)
                    .mapAction("CENTER_AND_ZOOM").zoom(15)
                    .build();
            return buildResponse(text, "LOCATE_VEHICLE",
                    "正在定位 " + plate, action, false);
        }

        // ═══════════════════════════════════════════════════════
        // 5. SHOW_RESULT 信息查询
        // ═══════════════════════════════════════════════════════
        if (plate != null && containsAny(text, "司机", "谁开", "驾驶员", "联系")) {
            AgentActionVO action = AgentActionVO.builder()
                    .type("SHOW_RESULT").targetType("VEHICLE").plate(plate)
                    .targetValue("/vehicles/" + plate.replace("·", "-"))
                    .build();
            return buildResponse(text, "QUERY_VEHICLE_DRIVER",
                    "查询 " + plate + " 的司机信息", action, false);
        }

        // 车辆位置（车牌 + 位置相关词）
        if (plate != null && containsAny(text, "实时", "最新", "当前", "查", "到哪", "在哪", "位置")) {
            AgentActionVO action = AgentActionVO.builder()
                    .type("SHOW_RESULT").targetType("VEHICLE").plate(plate)
                    .targetValue("/cargo/" + (orderId != null ? orderId : "") + "/position")
                    .build();
            return buildResponse(text, "QUERY_VEHICLE_POSITION",
                    "查询 " + plate + " 的实时位置", action, false);
        }

        // 订单状态（有订单号）
        if (containsAny(text, "订单", "状态") && orderId != null) {
            AgentActionVO action = AgentActionVO.builder()
                    .type("SHOW_RESULT").targetType("ORDER").targetValue(orderId)
                    .build();
            return buildResponse(text, "QUERY_ORDER_STATUS",
                    "查询订单 " + orderId + " 状态", action, false);
        }

        if (containsAny(text, "司机") && !containsAny(text, "打开", "导航")) {
            AgentActionVO action = AgentActionVO.builder()
                    .type("SHOW_RESULT").targetType("DRIVER_LIST").build();
            return buildResponse(text, "QUERY_DRIVER_LIST", "查询司机列表", action, false);
        }
        if (containsAny(text, "今天", "统计", "准点", "延误", "KPI", "里程")) {
            AgentActionVO action = AgentActionVO.builder()
                    .type("SHOW_RESULT").targetType("STATS").targetValue("TODAY")
                    .build();
            return buildResponse(text, "QUERY_STATS",
                    "查询今日统计数据", action, false);
        }

        // ═══════════════════════════════════════════════════════
        // 6. CHAT 对话（最低优先级，只在无业务匹配时触发）
        // ═══════════════════════════════════════════════════════
        if (isChat(text)) {
            return handleChat(text);
        }

        // ═══════════════════════════════════════════════════════
        // 7. NOOP 兜底
        // ═══════════════════════════════════════════════════════
        return buildResponse(text, "NOOP",
                "未识别到指令。试试：打开首页、定位车辆、查司机信息、打开车辆调度", null, false);
    }

    // ═══════════════════════════════════════════════════════
    // CHAT 对话处理
    // ═══════════════════════════════════════════════════════

    private boolean isChat(String text) {
        return containsAny(text, "你好", "在吗", "谢谢", "你是谁", "你叫什么",
                "现在几点", "几点了", "再见", "拜拜", "早上好", "下午好", "晚上好");
    }

    private Map<String, Object> handleChat(String text) {
        String reply;
        if (containsAny(text, "你好", "在吗", "早上好", "下午好", "晚上好")) {
            reply = "您好，我在。请问有什么可以帮您？";
        } else if (containsAny(text, "谢谢", "感谢")) {
            reply = "不客气，很高兴为您服务。";
        } else if (containsAny(text, "你是谁", "你叫什么")) {
            reply = "我是智能物流助手，您可以叫我小智。我可以帮您查看车辆位置、司机信息、订单状态，还可以通过语音下发调度指令。";
        } else if (containsAny(text, "几点", "几点了")) {
            reply = "现在是 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"));
        } else if (containsAny(text, "再见", "拜拜")) {
            reply = "再见，有需要随时叫我。";
        } else {
            reply = "您好，请问有什么可以帮您？";
        }
        return buildResponse(text, "CHAT", reply, null, false);
    }

    // ═══════════════════════════════════════════════════════
    // 标准接口方法
    // ═══════════════════════════════════════════════════════

    @Override
    public Map<String, Object> executeAction(String logId) {
        VoiceCommandLog logEntry = logMapper.findById(logId);
        if (logEntry == null) {
            return toMap("success", false, "message", "指令不存在");
        }
        logMapper.updateExecuted(logId, true);
        log.info("Action 已执行, logId={}, intent={}", logId, logEntry.getIntent());
        return toMap("success", true, "message", "指令已执行", "logId", logId);
    }

    @Override
    public Map<String, Object> getExamples() {
        List<Map<String, String>> examples = new ArrayList<>();
        addExample(examples, "打开首页", "跳转到 Dashboard");
        addExample(examples, "打开车辆调度", "跳转到车队总览");
        addExample(examples, "打开设备在线", "查看设备在线状态");
        addExample(examples, "打开货物追踪", "查看货物运输状态");
        addExample(examples, "打开告警中心", "查看告警列表");
        addExample(examples, "定位沪A C0291", "地图定位指定车辆");
        addExample(examples, "查一下沪A C0291的司机是谁", "查询司机信息");
        addExample(examples, "沪A C0291到哪了", "查询车辆实时位置");
        addExample(examples, "查一下SH-HZ-20260629-0291到哪了", "查询订单状态");
        addExample(examples, "给沪A C0291下发改道指令", "下发调度指令（需确认）");
        addExample(examples, "给沪A C0291发送前方事故的提示", "下发通知（需确认）");
        addExample(examples, "今天的准点率是多少", "查询今日统计");
        addExample(examples, "刷新页面", "重新加载当前页面");
        addExample(examples, "你好 / 谢谢 / 现在几点", "智能对话");
        return toMap("examples", examples, "totalCount", examples.size());
    }

    @Override
    public Map<String, Object> getLogs(String userId, String intent, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, 100);
        int offset = (p - 1) * s;

        Long total = logMapper.count(userId, intent);
        List<VoiceCommandLog> logs = logMapper.findPage(userId, intent, offset, s);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logs", logs);
        result.put("total", total != null ? total : 0);
        result.put("page", p);
        result.put("size", s);
        return result;
    }

    // ═══════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════

    private String extractPlate(String text) {
        Matcher m = PLATE_PATTERN.matcher(text);
        return m.find() ? m.group(1).replaceAll("[\\s\\-]", "·") : null;
    }

    private String extractOrderId(String text) {
        Matcher m = ORDER_ID_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    /** 提取关键词后的内容 */
    private String extractAfter(String text, String... keywords) {
        for (String kw : keywords) {
            int idx = text.indexOf(kw);
            if (idx >= 0) {
                String after = text.substring(idx + kw.length()).trim();
                if (after.length() > 1) return after;
            }
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /** 角色校验——一期宽松处理，记录日志但不硬拦截 */
    private boolean hasRole(String userId, String requiredRole) {
        // TODO: 接入真实用户角色查询（通过 JWT 或查 users 表）
        // 一期：所有用户允许，只记录警告日志
        if (!"DISPATCHER".equals(requiredRole) && !"ADMIN".equals(requiredRole)) {
            return true;
        }
        log.warn("权限校验（一期宽松模式）：需要 {} 角色, userId={}", requiredRole, userId);
        return true; // 一期全放行
    }

    private Map<String, Object> buildNavigate(String text, String intent,
                                               String page, String reply) {
        AgentActionVO action = AgentActionVO.builder()
                .type("NAVIGATE").targetType("PAGE").routeName(page).targetValue(page).build();
        return buildResponse(text, intent, reply, action, false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tryLlmFunctionCalling(AgentCommandRequest request, String text) {
        String context = "当前页面=" + trimToDefault(request.getSourcePage(), "未知")
                + "；当前选中对象类型=" + trimToDefault(request.getSelectedEntityType(), "无")
                + "；当前选中对象编号=" + trimToDefault(request.getSelectedEntityId(), "无");
        String systemPrompt = "你是伴生云途宠物托运工作人员平台的语音 Agent 羊小智。你必须理解工作人员真实意图，并在需要操作或查询系统时从 tools 中选择最合适的一个函数。"
                + "内部 cargo 字段代表宠物托运任务，面向用户必须称为宠物、宠物旅程或托运任务，不能称为货物。优先保障宠物安全、健康与动物福利。"
                + "不要用关键词机械匹配。用户没有说出的车牌、托运任务编号、申请编号、地址或坐标绝对不能编造；缺少必填信息时不要调用工具，而要用简短中文追问。"
                + "页面跳转使用 navigate_page；安排宠物托运任务到车辆使用 bind_cargo_vehicle；向司机下发指令使用 dispatch_vehicle。"
                + "所有修改数据的动作稍后都会由前端再次确认。只返回一个最合适的工具调用，普通寒暄或缺少信息时直接中文回答。"
                + "系统上下文：" + context;
        try {
            Map<String, Object> selected = llmService.chooseTool(systemPrompt, text, AgentToolCatalog.tools());
            String functionName = selected.get("name") == null ? null : String.valueOf(selected.get("name"));
            if (StringUtils.hasText(functionName)) {
                if (!AgentToolCatalog.contains(functionName)) {
                    log.warn("LLM 返回未注册工具: {}", functionName);
                    return null;
                }
                Map<String, Object> arguments = selected.get("arguments") instanceof Map
                        ? (Map<String, Object>) selected.get("arguments") : new LinkedHashMap<>();
                AgentActionVO action = AgentActionVO.builder()
                        .type("CALL_FUNCTION")
                        .targetType("FUNCTION")
                        .functionName(functionName)
                        .arguments(arguments)
                        .build();
                boolean needConfirm = AgentToolCatalog.requiresConfirmation(functionName);
                Map<String, Object> response = buildResponse(text, "LLM_TOOL_" + functionName.toUpperCase(),
                        AgentToolCatalog.description(functionName), action, needConfirm);
                response.put("agentMode", "LLM_FUNCTION_CALLING");
                response.put("toolCallId", selected.get("toolCallId"));
                return response;
            }
            String content = selected.get("content") == null ? null : String.valueOf(selected.get("content"));
            if (StringUtils.hasText(content)) {
                Map<String, Object> response = buildResponse(text, "LLM_CHAT", content, null, false);
                response.put("agentMode", "LLM_CHAT");
                return response;
            }
        } catch (Exception e) {
            log.warn("LLM Agent 不可用，进入规则兜底: {}", e.getMessage());
        }
        return null;
    }

    private String trimToDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private Map<String, Object> buildFunction(String text, String intent, String functionName,
                                               Object arguments, String reply, boolean needConfirm) {
        AgentActionVO action = AgentActionVO.builder()
                .type("CALL_FUNCTION")
                .targetType("FUNCTION")
                .functionName(functionName)
                .arguments(arguments)
                .build();
        return buildResponse(text, intent, reply, action, needConfirm);
    }

    private Map<String, Object> buildResponse(String text, String intent, String reply,
                                               AgentActionVO action, boolean needConfirm) {
        String logId = "VCL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        if (action != null) {
            action.setNeedConfirm(needConfirm);
            if (needConfirm && !StringUtils.hasText(action.getConfirmText())) {
                action.setConfirmText(reply);
            }
        }
        try {
            VoiceCommandLog logEntry = VoiceCommandLog.builder()
                    .id(logId).userId("SYSTEM").recognizedText(text)
                    .intent(intent)
                    .actionType(action != null ? action.getType() : null)
                    .actionJson(action != null ? objectMapper.writeValueAsString(action) : null)
                    .needConfirm(needConfirm).confirmed(false).executed(false)
                    .reply(reply).build();
            logMapper.insert(logEntry);
        } catch (JsonProcessingException e) {
            log.error("写语音日志失败: {}", e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logId", logId);
        result.put("recognizedText", text);
        result.put("intent", intent);
        result.put("reply", reply);
        result.put("needConfirm", needConfirm);
        if (action != null) result.put("action", action);
        return result;
    }

    private void addExample(List<Map<String, String>> list, String text, String desc) {
        Map<String, String> m = new HashMap<>();
        m.put("text", text);
        m.put("description", desc);
        list.add(m);
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, T> toMap(Object... keysAndValues) {
        Map<String, T> map = new HashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            map.put(String.valueOf(keysAndValues[i]), (T) keysAndValues[i + 1]);
        }
        return map;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
