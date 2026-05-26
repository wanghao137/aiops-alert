package com.aiops.alert.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.BizException;
import com.aiops.alert.common.Enums;
import com.aiops.alert.dto.AlertEventResponse;
import com.aiops.alert.dto.CommandRequest;
import com.aiops.alert.dto.CommandResponse;
import com.aiops.alert.entity.AlertEvent;
import com.aiops.alert.mapper.AlertEventMapper;
import com.aiops.alert.service.core.AlertEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 命令面板：把自然语言查询解析为结构化意图 + 参数，然后路由到内部能力。
 *
 * 支持意图（intent）：
 *  - list_events: 查询事件列表 (filters: objectName/alertLevel/eventStatus/keyword/limit)
 *  - count_events: 统计事件 (filters 同上)
 *  - route: 跳转到某个页面 (path)
 *  - unknown: 未识别
 */
@Slf4j
@Service
public class CommandService {

    private final LlmClient llm;
    private final LlmModelConfigService configService;
    private final ObjectMapper json;
    private final AlertEventService eventService;
    private final AlertEventMapper eventMapper;

    public CommandService(LlmClient llm,
                          LlmModelConfigService configService,
                          ObjectMapper json,
                          AlertEventService eventService,
                          AlertEventMapper eventMapper) {
        this.llm = llm;
        this.configService = configService;
        this.json = json;
        this.eventService = eventService;
        this.eventMapper = eventMapper;
    }

    public CommandResponse handle(CommandRequest request) {
        String prompt = request.getPrompt().trim();

        // AI 不可用时走简单关键词匹配兜底，保证演示能跑
        if (!configService.hasUsableConfig()) {
            return fallbackKeyword(prompt);
        }

        String system = """
                你是 AIOps Alert 系统的命令面板助手。把用户的中文查询解析为结构化 JSON，不要任何解释。

                # 意图与字段
                {
                  "intent": "list_events | count_events | route | unknown",
                  "filters": {
                    "objectName":  "可选，对象名关键字",
                    "alertLevel":  "可选 NOTICE|NORMAL|SERIOUS|CRITICAL",
                    "eventStatus": "可选 PENDING|CONFIRMED|RECOVERED|CLOSED",
                    "keyword":     "可选，标题/对象/编号关键字",
                    "limit":       数字，可选
                  },
                  "routePath": "/dashboard | /events | /incidents | /rules | /objects | /channels | /settings",
                  "summary":   "一句中文，对用户提问的复述/回答（不超过 80 字）"
                }

                # 例子
                输入"现在哪些对象在告警" → {"intent":"list_events","filters":{"eventStatus":"PENDING","limit":20},"summary":"查询当前所有待处理告警事件"}
                输入"打开规则页面" → {"intent":"route","routePath":"/rules","summary":"跳转到告警规则页面"}
                输入"今天有多少紧急告警" → {"intent":"count_events","filters":{"alertLevel":"CRITICAL"},"summary":"统计当前紧急级别告警数量"}
                输入"prod-mysql 的告警" → {"intent":"list_events","filters":{"objectName":"prod-mysql","limit":20},"summary":"查询 prod-mysql 相关告警"}

                # 规则
                - 只输出一个 JSON 对象，不要 markdown
                - 不识别就 intent=unknown
                """;

        long start = System.currentTimeMillis();
        LlmClient.ChatResult result;
        try {
            result = llm.chatJson("CHAT", system, prompt);
        } catch (BizException e) {
            return fallbackKeyword(prompt);
        }

        JsonNode root = llm.parseJson(result.getContent());
        String intent = root.path("intent").asText("unknown");
        String summary = root.path("summary").asText("");
        String routePath = root.path("routePath").asText(null);
        JsonNode filters = root.path("filters");

        CommandResponse.CommandResponseBuilder rb = CommandResponse.builder()
                .intent(intent)
                .answer(StrUtil.blankToDefault(summary, "已为你处理"))
                .routePath(StrUtil.blankToDefault(routePath, null))
                .rawOutput(result.getContent())
                .reasoning(result.getReasoning())
                .modelName(result.getLog().getModelName())
                .durationMs((int) (System.currentTimeMillis() - start));

        switch (intent) {
            case "list_events" -> doListEvents(rb, filters);
            case "count_events" -> doCountEvents(rb, filters);
            case "route" -> {
                // routePath 已经填好，answer 已经填好
            }
            default -> rb.answer(StrUtil.blankToDefault(summary, "我没听懂你的意思，可以试试"));
        }

        return rb.build();
    }

    private void doListEvents(CommandResponse.CommandResponseBuilder rb, JsonNode filters) {
        String objectName = textOrNull(filters, "objectName");
        String alertLevel = textOrNull(filters, "alertLevel");
        String eventStatus = textOrNull(filters, "eventStatus");
        String keyword = textOrNull(filters, "keyword");
        Integer limit = filters.path("limit").isInt() ? filters.path("limit").asInt() : 10;

        // objectName 转关键字
        String effKeyword = StrUtil.isNotBlank(objectName) ? objectName : keyword;
        List<AlertEventResponse> events = eventService.list(null, alertLevel, eventStatus, effKeyword, limit);
        rb.events(events);
        rb.total((long) events.size());
        if (events.isEmpty()) {
            rb.answer("没有匹配的事件");
        }
    }

    private void doCountEvents(CommandResponse.CommandResponseBuilder rb, JsonNode filters) {
        String alertLevel = textOrNull(filters, "alertLevel");
        String eventStatus = textOrNull(filters, "eventStatus");

        long total = eventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(StrUtil.isNotBlank(alertLevel), AlertEvent::getAlertLevel, alertLevel)
                .eq(StrUtil.isNotBlank(eventStatus), AlertEvent::getEventStatus, eventStatus));
        long pending = eventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getEventStatus, Enums.EventStatus.PENDING));
        long critical = eventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getAlertLevel, Enums.AlertLevel.CRITICAL));

        rb.total(total).pending(pending).critical(critical);
    }

    /** AI 不可用时的关键词兜底 */
    private CommandResponse fallbackKeyword(String prompt) {
        String p = prompt.toLowerCase();

        if (p.contains("待处理") || p.contains("正在告警") || p.contains("当前")) {
            List<AlertEventResponse> events = eventService.list(null, null,
                    Enums.EventStatus.PENDING, null, 10);
            return CommandResponse.builder()
                    .intent("list_events")
                    .answer("当前待处理事件：" + events.size() + " 条")
                    .events(events)
                    .total((long) events.size())
                    .build();
        }
        if (p.contains("紧急")) {
            List<AlertEventResponse> events = eventService.list(null,
                    Enums.AlertLevel.CRITICAL, null, null, 10);
            return CommandResponse.builder()
                    .intent("list_events")
                    .answer("紧急级别事件：" + events.size() + " 条")
                    .events(events)
                    .total((long) events.size())
                    .build();
        }
        if (p.contains("仪表") || p.contains("看板") || p.contains("总览") || p.contains("dashboard")) {
            return CommandResponse.builder()
                    .intent("route")
                    .routePath("/dashboard")
                    .answer("跳转总览大屏")
                    .build();
        }
        if (p.contains("规则") || p.contains("rule")) {
            return CommandResponse.builder()
                    .intent("route")
                    .routePath("/rules")
                    .answer("跳转告警规则")
                    .build();
        }
        if (p.contains("incident") || p.contains("故障") || p.contains("归并")) {
            return CommandResponse.builder()
                    .intent("route")
                    .routePath("/incidents")
                    .answer("跳转 Incident 视图")
                    .build();
        }
        if (p.contains("渠道") || p.contains("channel")) {
            return CommandResponse.builder()
                    .intent("route")
                    .routePath("/channels")
                    .answer("跳转通知渠道")
                    .build();
        }
        if (p.contains("对象") || p.contains("object")) {
            return CommandResponse.builder()
                    .intent("route")
                    .routePath("/objects")
                    .answer("跳转监控对象")
                    .build();
        }
        if (p.contains("设置") || p.contains("配置") || p.contains("setting")) {
            return CommandResponse.builder()
                    .intent("route")
                    .routePath("/settings")
                    .answer("跳转系统设置")
                    .build();
        }
        // 默认看作关键字搜索事件
        List<AlertEventResponse> events = eventService.list(null, null, null, prompt, 10);
        return CommandResponse.builder()
                .intent("list_events")
                .answer(events.isEmpty() ? "未找到匹配事件" : "搜到 " + events.size() + " 条相关事件")
                .events(events)
                .total((long) events.size())
                .build();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() || StrUtil.isBlank(v.asText()) ? null : v.asText();
    }
}
