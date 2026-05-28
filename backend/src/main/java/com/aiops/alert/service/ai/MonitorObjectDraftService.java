package com.aiops.alert.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.Enums;
import com.aiops.alert.dto.MonitorObjectDraftRequest;
import com.aiops.alert.dto.MonitorObjectDraftResponse;
import com.aiops.alert.dto.MonitorObjectRequest;
import com.aiops.alert.entity.MonitorObject;
import com.aiops.alert.mapper.MonitorObjectMapper;
import com.aiops.alert.service.core.MetricCatalogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MonitorObjectDraftService {

    private final LlmClient llmClient;
    private final LlmModelConfigService configService;
    private final ObjectMapper objectMapper;
    private final MetricCatalogService metricCatalog;
    private final MonitorObjectMapper monitorObjectMapper;

    public MonitorObjectDraftService(LlmClient llmClient,
                                     LlmModelConfigService configService,
                                     ObjectMapper objectMapper,
                                     MetricCatalogService metricCatalog,
                                     MonitorObjectMapper monitorObjectMapper) {
        this.llmClient = llmClient;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.metricCatalog = metricCatalog;
        this.monitorObjectMapper = monitorObjectMapper;
    }

    public boolean available() {
        return configService.hasUsableConfig();
    }

    public MonitorObjectDraftResponse draft(MonitorObjectDraftRequest request) {
        List<MonitorObject> existing = monitorObjectMapper.selectList(new LambdaQueryWrapper<MonitorObject>()
                .orderByDesc(MonitorObject::getUpdatedAt)
                .last("limit 80"));

        String systemPrompt = buildSystemPrompt(existing);
        long start = System.currentTimeMillis();
        LlmClient.ChatResult chat = llmClient.chatJson("OBJECT_DRAFT", systemPrompt, request.getPrompt());
        JsonNode json = llmClient.parseJson(chat.getContent());

        MonitorObjectRequest draft = new MonitorObjectRequest();
        draft.setObjectName(textOrNull(json, "objectName"));
        draft.setObjectType(textOrNull(json, "objectType"));
        draft.setObjectCode(textOrNull(json, "objectCode"));
        draft.setOwnerName(textOrNull(json, "ownerName"));
        draft.setOwnerPhone(textOrNull(json, "ownerPhone"));
        draft.setTags(textOrNull(json, "tags"));
        draft.setStatus(textOrNull(json, "status"));
        draft.setDescription(textOrNull(json, "description"));
        draft.setExtConfig(extConfigOrNull(json));

        List<String> warnings = postValidate(draft, request.getPrompt());
        int duration = (int) (System.currentTimeMillis() - start);
        return MonitorObjectDraftResponse.builder()
                .draft(draft)
                .understanding(textOrNull(json, "understanding"))
                .warnings(warnings)
                .durationMs(duration)
                .modelName(chat.getLog().getModelName())
                .reasoning(chat.getReasoning())
                .build();
    }

    private String buildSystemPrompt(List<MonitorObject> existing) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("objectTypes", List.of(
                Map.of("value", "SERVER", "label", "服务器", "extConfigExample",
                        Map.of("host", "10.0.0.11", "port", 22, "env", "prod", "service", "nginx")),
                Map.of("value", "DATABASE", "label", "数据库", "extConfigExample",
                        Map.of("host", "10.0.0.21", "port", 3306, "env", "prod", "dbType", "mysql")),
                Map.of("value", "SYNC_JOB", "label", "数据同步作业", "extConfigExample",
                        Map.of("jobName", "customer-sync", "schedule", "*/5 * * * *", "source", "crm", "target", "dw")),
                Map.of("value", "PROCESS_JOB", "label", "数据加工作业", "extConfigExample",
                        Map.of("jobName", "daily-agg", "schedule", "0 2 * * *", "ownerTeam", "data-platform"))
        ));
        ctx.put("metricsByType", metricCatalog.all().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream().map(m -> {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    mm.put("code", m.getCode());
                    mm.put("name", m.getName());
                    mm.put("valueType", m.getValueType());
                    if (m.getUnit() != null) mm.put("unit", m.getUnit());
                    return mm;
                }).collect(Collectors.toList())
        )));
        ctx.put("existingObjects", existing.stream().map(o -> Map.of(
                "objectName", StrUtil.nullToEmpty(o.getObjectName()),
                "objectCode", StrUtil.nullToEmpty(o.getObjectCode()),
                "objectType", StrUtil.nullToEmpty(o.getObjectType()),
                "tags", StrUtil.nullToEmpty(o.getTags()),
                "description", StrUtil.nullToEmpty(o.getDescription())
        )).collect(Collectors.toList()));

        String ctxJson;
        try {
            ctxJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ctx);
        } catch (Exception e) {
            ctxJson = "{}";
        }

        return """
                你是 AIOps 监控对象配置助手，负责把运维人员的一句话资产描述转换成可保存的监控对象 JSON 草稿。

                # 输出 JSON Schema
                {
                  "objectName": "string，必填，清晰表达环境 + 对象身份，例如：生产 MySQL 主库",
                  "objectType": "SERVER | DATABASE | SYNC_JOB | PROCESS_JOB",
                  "objectCode": "string，可选，建议使用大写字母/数字/短横线/下划线；不确定或可能重复时留空",
                  "ownerName": "string，可选，负责人或团队",
                  "ownerPhone": "string，可选，手机号或值班电话",
                  "tags": "string，可选，用英文逗号分隔，例如：prod,核心,7x24",
                  "status": "ENABLED | DISABLED，默认 ENABLED",
                  "description": "string，可选，描述该对象用途、风险和重点监控方向",
                  "extConfig": "string，可选，必须是 JSON 对象字符串，不要输出数组或普通文本",
                  "understanding": "string，用一句中文复述你对配置意图的理解"
                }

                # 规则
                - 必须从 objectTypes 中选择最贴合的 objectType，不能发明新类型。
                - extConfig 只放结构化连接、调度、环境、团队等配置，不要放告警规则阈值。
                - 如果用户给出 IP、端口、数据库类型、调度周期、来源目标系统，请尽量写入 extConfig。
                - objectCode 如果和 existingObjects 中明显冲突，留空，让保存接口自动生成。
                - tags 使用英文逗号分隔，标签内容可以是中文。
                - 输出必须是单个合法 JSON 对象，不要 markdown，不要解释。

                # 上下文
                """ + ctxJson + """

                # 输出
                直接输出 JSON 对象。
                """;
    }

    private List<String> postValidate(MonitorObjectRequest draft, String prompt) {
        List<String> warnings = new ArrayList<>();

        if (StrUtil.isBlank(draft.getObjectName())) {
            draft.setObjectName(defaultObjectName(prompt));
            warnings.add("AI 未生成对象名称，已用描述自动补齐，请人工确认。");
        } else {
            draft.setObjectName(StrUtil.maxLength(draft.getObjectName().trim(), 128));
        }

        if (!Enums.ObjectType.isValid(draft.getObjectType())) {
            warnings.add("对象类型异常：" + draft.getObjectType() + "，已默认按服务器处理。");
            draft.setObjectType(Enums.ObjectType.SERVER);
        }

        String code = normalizeCode(draft.getObjectCode());
        if (StrUtil.isNotBlank(code)) {
            Long count = monitorObjectMapper.selectCount(new LambdaQueryWrapper<MonitorObject>()
                    .eq(MonitorObject::getObjectCode, code));
            if (count != null && count > 0) {
                warnings.add("对象编码 " + code + " 已存在，已清空，保存时由系统自动生成。");
                code = null;
            }
        }
        draft.setObjectCode(code);

        if (!Enums.Status.DISABLED.equals(draft.getStatus())) {
            draft.setStatus(Enums.Status.ENABLED);
        }

        if (StrUtil.isNotBlank(draft.getTags())) {
            draft.setTags(draft.getTags().replace('，', ','));
        }

        if (StrUtil.isNotBlank(draft.getExtConfig())) {
            String normalized = normalizeExtConfig(draft.getExtConfig(), warnings);
            draft.setExtConfig(normalized);
        }

        return warnings;
    }

    private String normalizeExtConfig(String raw, List<String> warnings) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.isObject()) {
                warnings.add("扩展配置不是 JSON 对象，已清空，请手工补充。");
                return null;
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            warnings.add("扩展配置不是合法 JSON，已清空，请手工补充。");
            return null;
        }
    }

    private String extConfigOrNull(JsonNode node) {
        JsonNode v = node.path("extConfig");
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        try {
            if (v.isObject()) {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(v);
            }
            return v.asText();
        } catch (Exception e) {
            return v.asText();
        }
    }

    private String normalizeCode(String code) {
        if (StrUtil.isBlank(code)) {
            return null;
        }
        String normalized = code.trim()
                .replaceAll("[^A-Za-z0-9_-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("(^-|-$)", "")
                .toUpperCase();
        return StrUtil.maxLength(normalized, 64);
    }

    private String defaultObjectName(String prompt) {
        String text = StrUtil.blankToDefault(prompt, "AI 生成监控对象").trim();
        return StrUtil.maxLength(text, 40);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
