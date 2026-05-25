package com.aiops.alert.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.BizException;
import com.aiops.alert.common.Enums;
import com.aiops.alert.dto.AlertRuleChannelBindingDto;
import com.aiops.alert.dto.AlertRuleConditionDto;
import com.aiops.alert.dto.AlertRuleRequest;
import com.aiops.alert.dto.NlRuleDraftRequest;
import com.aiops.alert.dto.NlRuleDraftResponse;
import com.aiops.alert.entity.AlertChannel;
import com.aiops.alert.entity.MonitorObject;
import com.aiops.alert.mapper.AlertChannelMapper;
import com.aiops.alert.mapper.MonitorObjectMapper;
import com.aiops.alert.service.core.MetricCatalogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 自然语言生成告警规则草稿。
 *
 * 设计要点：
 * 1. 把"对象列表 / 渠道列表 / 指标字典 / 比较符 / 级别 / 类型"完整喂给 LLM；
 * 2. 强制 JSON 输出 + 明确的 schema；
 * 3. 后端拿到结构化结果后再做一次"对齐校验"：对象类型一致、metricCode 在字典里、对象 id 真实存在、渠道 id 真实存在；
 * 4. 不一致或缺失项收集到 warnings，前端高亮展示但不阻断保存。
 */
@Slf4j
@Service
public class NlRuleService {

    private final LlmClient llmClient;
    private final LlmModelConfigService configService;
    private final ObjectMapper objectMapper;
    private final MetricCatalogService metricCatalog;
    private final MonitorObjectMapper objectMapperDao;
    private final AlertChannelMapper channelMapper;

    public NlRuleService(LlmClient llmClient,
                         LlmModelConfigService configService,
                         ObjectMapper objectMapper,
                         MetricCatalogService metricCatalog,
                         MonitorObjectMapper objectMapperDao,
                         AlertChannelMapper channelMapper) {
        this.llmClient = llmClient;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.metricCatalog = metricCatalog;
        this.objectMapperDao = objectMapperDao;
        this.channelMapper = channelMapper;
    }

    public boolean available() {
        return configService.hasUsableConfig();
    }

    public NlRuleDraftResponse draft(NlRuleDraftRequest request) {
        // 1. 收集上下文
        List<MonitorObject> objects = objectMapperDao.selectList(
                new LambdaQueryWrapper<MonitorObject>()
                        .eq(MonitorObject::getStatus, Enums.Status.ENABLED));
        List<AlertChannel> channels = channelMapper.selectList(
                new LambdaQueryWrapper<AlertChannel>()
                        .eq(AlertChannel::getStatus, Enums.Status.ENABLED));

        if (objects.isEmpty()) {
            throw new BizException("当前没有启用的监控对象，先去「监控对象」添加再来用 AI 建规则");
        }

        // 2. 调 LLM
        String systemPrompt = buildSystemPrompt(objects, channels);
        long start = System.currentTimeMillis();
        LlmClient.ChatResult chat = llmClient.chatJson("NL2RULE", systemPrompt, request.getPrompt());
        JsonNode json = llmClient.parseJson(chat.getContent());

        // 3. 把 JSON 映射成 AlertRuleRequest
        AlertRuleRequest draft = new AlertRuleRequest();
        draft.setRuleName(textOrNull(json, "ruleName"));
        draft.setObjectType(textOrNull(json, "objectType"));
        draft.setAlertLevel(textOrNull(json, "alertLevel"));
        draft.setConditionLogic(StrUtil.blankToDefault(textOrNull(json, "conditionLogic"), "AND"));
        draft.setTriggerTimes(intOrNull(json, "triggerTimes"));
        draft.setTimeWindowMinutes(intOrNull(json, "timeWindowMinutes"));
        draft.setMinAlertIntervalMinutes(intOrNull(json, "minAlertIntervalMinutes"));
        draft.setRecoverNotify(boolOrNull(json, "recoverNotify"));
        draft.setRepeatNotify(boolOrNull(json, "repeatNotify"));
        draft.setStatus(Enums.Status.ENABLED);
        draft.setPriority(intOrNull(json, "priority"));
        draft.setDescription(textOrNull(json, "description"));

        // conditions
        List<AlertRuleConditionDto> conditions = new ArrayList<>();
        JsonNode conditionsNode = json.path("conditions");
        if (conditionsNode.isArray()) {
            int order = 1;
            for (JsonNode c : conditionsNode) {
                AlertRuleConditionDto dto = new AlertRuleConditionDto();
                dto.setConditionOrder(order++);
                dto.setMetricCode(textOrNull(c, "metricCode"));
                dto.setMetricName(textOrNull(c, "metricName"));
                dto.setCompareOp(textOrNull(c, "compareOp"));
                dto.setThresholdValue(textOrNull(c, "thresholdValue"));
                dto.setThresholdUnit(textOrNull(c, "thresholdUnit"));
                conditions.add(dto);
            }
        }
        draft.setConditions(conditions);

        // objectIds
        List<Long> objectIds = new ArrayList<>();
        JsonNode objectIdsNode = json.path("objectIds");
        if (objectIdsNode.isArray()) {
            for (JsonNode v : objectIdsNode) {
                if (v.isNumber()) objectIds.add(v.asLong());
            }
        }
        draft.setObjectIds(objectIds);

        // channelBindings
        List<AlertRuleChannelBindingDto> bindings = new ArrayList<>();
        JsonNode bindingsNode = json.path("channelBindings");
        if (bindingsNode.isArray()) {
            for (JsonNode b : bindingsNode) {
                AlertRuleChannelBindingDto dto = new AlertRuleChannelBindingDto();
                if (b.path("channelId").isNumber()) {
                    dto.setChannelId(b.path("channelId").asLong());
                    dto.setReceiverValue(textOrNull(b, "receiverValue"));
                    dto.setTemplateCode(textOrNull(b, "templateCode"));
                    bindings.add(dto);
                }
            }
        }
        draft.setChannelBindings(bindings);

        // 4. 对齐校验，收集 warnings
        List<String> warnings = postValidate(draft, objects, channels);
        String understanding = textOrNull(json, "understanding");

        int duration = (int) (System.currentTimeMillis() - start);
        return NlRuleDraftResponse.builder()
                .draft(draft)
                .understanding(understanding)
                .warnings(warnings)
                .durationMs(duration)
                .modelName(chat.getLog().getModelName())
                .build();
    }

    // ---------------- prompt ----------------

    private String buildSystemPrompt(List<MonitorObject> objects, List<AlertChannel> channels) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("objectTypes", List.of(
                Map.of("value", "SERVER", "label", "服务器"),
                Map.of("value", "DATABASE", "label", "数据库"),
                Map.of("value", "SYNC_JOB", "label", "数据同步作业"),
                Map.of("value", "PROCESS_JOB", "label", "数据加工作业")
        ));
        ctx.put("alertLevels", List.of(
                Map.of("value", "NOTICE", "label", "提示"),
                Map.of("value", "NORMAL", "label", "一般"),
                Map.of("value", "SERIOUS", "label", "严重"),
                Map.of("value", "CRITICAL", "label", "紧急")
        ));
        ctx.put("compareOps", metricCatalog.compareOps().stream().map(op -> Map.of(
                "code", op.getCode(),
                "label", op.getLabel(),
                "symbol", op.getSymbol(),
                "inputKind", op.getInputKind()
        )).collect(Collectors.toList()));
        ctx.put("metricsByType", metricCatalog.all().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream().map(m -> {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    mm.put("code", m.getCode());
                    mm.put("name", m.getName());
                    mm.put("valueType", m.getValueType());
                    if (m.getUnit() != null) mm.put("unit", m.getUnit());
                    if (m.getDefaultCompareOp() != null) mm.put("defaultCompareOp", m.getDefaultCompareOp());
                    if (m.getDefaultThreshold() != null) mm.put("defaultThreshold", m.getDefaultThreshold());
                    if (m.getOptions() != null && !m.getOptions().isEmpty()) {
                        mm.put("options", m.getOptions());
                    }
                    return mm;
                }).collect(Collectors.toList())
        )));
        ctx.put("availableObjects", objects.stream().map(o -> Map.of(
                "id", o.getId(),
                "objectName", StrUtil.nullToEmpty(o.getObjectName()),
                "objectCode", StrUtil.nullToEmpty(o.getObjectCode()),
                "objectType", StrUtil.nullToEmpty(o.getObjectType()),
                "tags", StrUtil.nullToEmpty(o.getTags()),
                "description", StrUtil.nullToEmpty(o.getDescription())
        )).collect(Collectors.toList()));
        ctx.put("availableChannels", channels.stream().map(c -> Map.of(
                "id", c.getId(),
                "channelName", StrUtil.nullToEmpty(c.getChannelName()),
                "channelType", StrUtil.nullToEmpty(c.getChannelType()),
                "providerName", StrUtil.nullToEmpty(c.getProviderName()),
                "description", StrUtil.nullToEmpty(c.getDescription())
        )).collect(Collectors.toList()));

        String ctxJson;
        try {
            ctxJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ctx);
        } catch (Exception e) {
            ctxJson = "{}";
        }

        return """
                你是一名 SRE / AIOps 智能助手，专注于把运维同学的"一句话告警需求"翻译成结构化告警规则 JSON。

                # 任务
                根据用户输入的中文描述，从给定的"可选对象 / 可选渠道 / 指标字典"中匹配最合适的项，生成一条告警规则草稿。

                # 输出 JSON Schema (字段必须严格匹配)
                {
                  "ruleName": "string，简洁、能体现对象 + 指标 + 触发逻辑",
                  "objectType": "SERVER | DATABASE | SYNC_JOB | PROCESS_JOB",
                  "alertLevel": "NOTICE | NORMAL | SERIOUS | CRITICAL",
                  "conditionLogic": "AND | OR，多条件时使用",
                  "triggerTimes": 整数，连续触发次数，默认 1,
                  "timeWindowMinutes": 整数，观察窗口分钟，默认 5,
                  "minAlertIntervalMinutes": 整数，最小告警间隔分钟，默认 30,
                  "recoverNotify": boolean，恢复时是否再通知，默认 true,
                  "repeatNotify": boolean，未恢复时是否重复通知，默认 false,
                  "priority": 整数，1-999，越小越高，默认 100,
                  "description": "string，可选，更详细描述",
                  "conditions": [
                    {
                      "metricCode": "字典中的 code",
                      "metricName": "对应字典中的 name",
                      "compareOp": "GT | GE | LT | LE | EQ | NE | OFFLINE | FAILED | TIMEOUT | IN",
                      "thresholdValue": "数值或枚举值字符串",
                      "thresholdUnit": "单位字符串，无则为空"
                    }
                  ],
                  "objectIds": [对应 availableObjects 里 id 的整数列表],
                  "channelBindings": [
                    { "channelId": 整数, "receiverValue": "可选，接收人，逗号分隔" }
                  ],
                  "understanding": "用一句中文复述你的理解，便于人工二次确认"
                }

                # 关键规则
                - 必须只能选择 availableObjects 中实际存在的对象 id；如果用户说"生产 MySQL"，请按对象名/标签/描述匹配最相似的，不要编造 id。
                - metricCode 必须严格来自 metricsByType[objectType] 列表，不要发明新指标。
                - 对状态类指标（valueType=state），thresholdValue 必须是 options 中的某个 value。
                - 比较符按指标的 inputKind 选择：numeric 类指标用 GT/GE/LT/LE/EQ/NE；state 类指标用 EQ/NE/OFFLINE/FAILED/TIMEOUT。
                - 渠道：用户只说"企微"则匹配 channelType=WECOM，"邮件"则 EMAIL，"短信" 则 SMS；按名称/描述精挑。
                - 多条件时 conditionLogic 默认 AND，除非用户明确说"或"。
                - 输出必须是单个合法 JSON 对象，不要使用 markdown，不要任何额外解释。

                # 上下文（可选对象 / 可选渠道 / 字典）
                """ + ctxJson + """

                # 输出
                直接输出 JSON 对象，不要解释。
                """;
    }

    // ---------------- post validate ----------------

    private List<String> postValidate(AlertRuleRequest draft, List<MonitorObject> objects, List<AlertChannel> channels) {
        List<String> warnings = new ArrayList<>();

        // 必填基础字段
        if (StrUtil.isBlank(draft.getRuleName())) warnings.add("规则名称未生成，请补充");
        if (!Enums.ObjectType.isValid(draft.getObjectType())) {
            warnings.add("对象类型异常：" + draft.getObjectType() + "，请手工选择");
            // 不再继续校验对象/指标，避免误报
            return warnings;
        }
        if (!List.of("NOTICE", "NORMAL", "SERIOUS", "CRITICAL").contains(draft.getAlertLevel())) {
            warnings.add("告警级别异常：" + draft.getAlertLevel());
        }

        // 条件校验
        if (draft.getConditions() == null || draft.getConditions().isEmpty()) {
            warnings.add("AI 未生成触发条件，请手动添加至少一个");
        } else {
            for (AlertRuleConditionDto c : draft.getConditions()) {
                MetricCatalogService.Metric m = metricCatalog.findMetric(draft.getObjectType(), c.getMetricCode());
                if (m == null) {
                    warnings.add("指标 " + c.getMetricCode() + " 不属于该对象类型，请重选");
                } else {
                    // 自动对齐 metricName/threshold
                    c.setMetricName(m.getName());
                    if (StrUtil.isBlank(c.getThresholdUnit()) && m.getUnit() != null) {
                        c.setThresholdUnit(m.getUnit());
                    }
                    if (StrUtil.isBlank(c.getThresholdValue()) && m.getDefaultThreshold() != null) {
                        c.setThresholdValue(m.getDefaultThreshold());
                    }
                }
            }
        }

        // 对象校验：保留属于该 objectType 的、id 真实存在的
        Map<Long, MonitorObject> objMap = objects.stream()
                .collect(Collectors.toMap(MonitorObject::getId, o -> o));
        List<Long> validObjIds = new ArrayList<>();
        if (draft.getObjectIds() != null) {
            for (Long id : draft.getObjectIds()) {
                MonitorObject o = objMap.get(id);
                if (o == null) {
                    warnings.add("AI 选择的对象 id=" + id + " 不存在，已忽略");
                } else if (!draft.getObjectType().equals(o.getObjectType())) {
                    warnings.add("对象「" + o.getObjectName() + "」类型与规则不一致，已忽略");
                } else {
                    validObjIds.add(id);
                }
            }
        }
        draft.setObjectIds(validObjIds);
        if (validObjIds.isEmpty()) {
            warnings.add("未能匹配到合适的监控对象，请手动选择");
        }

        // 渠道校验
        Set<Long> channelIds = channels.stream().map(AlertChannel::getId)
                .collect(Collectors.toCollection(HashSet::new));
        List<AlertRuleChannelBindingDto> validBindings = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        if (draft.getChannelBindings() != null) {
            for (AlertRuleChannelBindingDto b : draft.getChannelBindings()) {
                if (b.getChannelId() == null || !channelIds.contains(b.getChannelId())) {
                    warnings.add("渠道 id=" + b.getChannelId() + " 不存在，已忽略");
                    continue;
                }
                if (!seen.add(b.getChannelId())) {
                    continue;
                }
                validBindings.add(b);
            }
        }
        draft.setChannelBindings(validBindings);
        if (validBindings.isEmpty()) {
            warnings.add("未能识别通知渠道，请手动选择渠道并配置接收人");
        }

        // 默认值兜底
        if (draft.getTriggerTimes() == null) draft.setTriggerTimes(1);
        if (draft.getTimeWindowMinutes() == null) draft.setTimeWindowMinutes(5);
        if (draft.getMinAlertIntervalMinutes() == null) draft.setMinAlertIntervalMinutes(30);
        if (draft.getPriority() == null) draft.setPriority(100);
        if (draft.getRecoverNotify() == null) draft.setRecoverNotify(true);
        if (draft.getRepeatNotify() == null) draft.setRepeatNotify(false);
        if (StrUtil.isBlank(draft.getStatus())) draft.setStatus(Enums.Status.ENABLED);

        return warnings;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asInt();
    }

    private Boolean boolOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asBoolean();
    }
}
