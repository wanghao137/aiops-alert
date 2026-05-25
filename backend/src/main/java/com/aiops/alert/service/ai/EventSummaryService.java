package com.aiops.alert.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.entity.AlertEvent;
import com.aiops.alert.entity.AlertRule;
import com.aiops.alert.entity.MonitorObject;
import com.aiops.alert.mapper.AlertEventMapper;
import com.aiops.alert.mapper.AlertRuleMapper;
import com.aiops.alert.mapper.MonitorObjectMapper;
import com.aiops.alert.service.stream.AlertStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 告警事件 AI 摘要：异步生成"发生了什么 / 影响范围 / 可能原因 / 建议动作"。
 *
 * 调用 LlmClient.chatJson() 获取严格 JSON，写回 alert_event.ai_summary，并通过 SSE 广播。
 */
@Slf4j
@Service
public class EventSummaryService {

    private final LlmClient llm;
    private final LlmModelConfigService configService;
    private final AlertEventMapper eventMapper;
    private final AlertRuleMapper ruleMapper;
    private final MonitorObjectMapper objectMapper;
    private final ObjectMapper jsonMapper;
    private final AlertStreamService streamService;

    public EventSummaryService(LlmClient llm,
                               LlmModelConfigService configService,
                               AlertEventMapper eventMapper,
                               AlertRuleMapper ruleMapper,
                               MonitorObjectMapper objectMapper,
                               ObjectMapper jsonMapper,
                               AlertStreamService streamService) {
        this.llm = llm;
        this.configService = configService;
        this.eventMapper = eventMapper;
        this.ruleMapper = ruleMapper;
        this.objectMapper = objectMapper;
        this.jsonMapper = jsonMapper;
        this.streamService = streamService;
    }

    @Async
    public void summarizeAsync(Long eventId) {
        AlertEvent event = eventMapper.selectById(eventId);
        if (event == null) return;

        if (!configService.hasUsableConfig()) {
            event.setAiSummaryStatus("FAILED");
            event.setAiSummary(jsonOf(Map.of(
                    "error", "AI 未配置",
                    "what", event.getEventTitle(),
                    "impact", "请在系统设置中配置并启用 LLM",
                    "causes", List.of(),
                    "actions", List.of()
            )));
            eventMapper.updateById(event);
            broadcastSummary(event);
            return;
        }

        AlertRule rule = ruleMapper.selectById(event.getRuleId());
        MonitorObject object = objectMapper.selectById(event.getObjectId());

        String system = """
                你是一名资深 SRE / DevOps 工程师，擅长在 5 秒内对告警做出冷静、专业的分析。
                你将收到一条 JSON 格式的告警事件，请输出严格的 JSON：
                {
                  "what":     "一句话精炼描述当前发生了什么（不超过 60 字）",
                  "impact":   "可能造成的影响（不超过 80 字，提到下游/业务）",
                  "causes":   ["可能原因 1", "可能原因 2", "可能原因 3"],
                  "actions":  ["建议动作 1", "建议动作 2", "建议动作 3"]
                }
                要求：中文、不夸张、不臆造、对运维有指导价值。除 JSON 外不要输出任何其他内容。
                """;

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("eventTitle", event.getEventTitle());
        ctx.put("eventReason", event.getEventReason());
        ctx.put("alertLevel", event.getAlertLevel());
        ctx.put("metricCode", event.getMetricCode());
        ctx.put("metricName", event.getMetricName());
        ctx.put("currentValue", event.getCurrentValue());
        ctx.put("thresholdValue", event.getThresholdValue());
        ctx.put("objectType", event.getObjectType());
        ctx.put("objectName", event.getObjectName());
        if (rule != null) {
            ctx.put("ruleName", rule.getRuleName());
            ctx.put("ruleDescription", rule.getDescription());
            ctx.put("triggerTimes", rule.getTriggerTimes());
            ctx.put("timeWindowMinutes", rule.getTimeWindowMinutes());
        }
        if (object != null) {
            ctx.put("ownerName", object.getOwnerName());
            ctx.put("tags", object.getTags());
            ctx.put("objectExtConfig", object.getExtConfig());
        }

        String userPrompt = "告警事件 JSON：\n" + jsonOf(ctx) + "\n\n请输出严格 JSON。";

        try {
            LlmClient.ChatResult result = llm.chatJson("EVENT_SUMMARY", system, userPrompt);
            // 解析校验，失败抛异常进 catch 兜底
            String content = StrUtil.blankToDefault(result.getContent(), "{}").trim();
            // 容错：去 markdown 包装
            if (content.startsWith("```")) {
                content = content.replaceAll("^```(?:json)?", "").replaceAll("```$", "").trim();
            }
            jsonMapper.readTree(content); // 校验 JSON 合法性
            event.setAiSummary(content);
            event.setAiSummaryStatus("SUCCESS");
            eventMapper.updateById(event);
            broadcastSummary(event);
        } catch (Exception e) {
            log.warn("event summary failed: {}", e.getMessage());
            event.setAiSummary(jsonOf(Map.of(
                    "error", String.valueOf(e.getMessage()),
                    "what", event.getEventTitle(),
                    "impact", "AI 生成失败，请人工分析",
                    "causes", List.of(),
                    "actions", List.of()
            )));
            event.setAiSummaryStatus("FAILED");
            eventMapper.updateById(event);
            broadcastSummary(event);
        }
    }

    private void broadcastSummary(AlertEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", event.getId());
        data.put("status", event.getAiSummaryStatus());
        data.put("summary", event.getAiSummary());
        streamService.broadcast("ai-summary", data);
    }

    private String jsonOf(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
