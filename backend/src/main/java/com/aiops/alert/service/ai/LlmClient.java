package com.aiops.alert.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.BizException;
import com.aiops.alert.entity.AiCallLog;
import com.aiops.alert.entity.LlmModelConfig;
import com.aiops.alert.mapper.AiCallLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * LLM 调用客户端 (OpenAI 兼容协议)。
 *
 * 所有 LLM 业务都走这里：
 * - 自动选择默认模型配置
 * - 自动写 ai_call_log 流水
 * - 提供 chat 与 chatJson 两种入口（chatJson 强制 response_format=json_object）
 */
@Slf4j
@Component
public class LlmClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final LlmModelConfigService configService;
    private final AiCallLogMapper callLogMapper;

    public LlmClient(RestTemplateBuilder builder,
                     ObjectMapper objectMapper,
                     LlmModelConfigService configService,
                     AiCallLogMapper callLogMapper) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(180))
                .build();
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.callLogMapper = callLogMapper;
    }

    /** 普通文本对话。 */
    public ChatResult chat(String scene, String systemPrompt, String userPrompt) {
        return doChat(scene, systemPrompt, userPrompt, false);
    }

    /** 强制返回 JSON。提示词需要明确声明 JSON Schema，模型不靠谱时建议 retry。 */
    public ChatResult chatJson(String scene, String systemPrompt, String userPrompt) {
        return doChat(scene, systemPrompt, userPrompt, true);
    }

    private ChatResult doChat(String scene, String systemPrompt, String userPrompt, boolean json) {
        LlmModelConfig config = configService.requireDefault();

        AiCallLog log = new AiCallLog();
        log.setScene(scene);
        log.setModelConfigId(config.getId());
        log.setModelName(config.getModelName());
        log.setStatus("SUCCESS");

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModelName());
            body.put("temperature", config.getTemperature() == null ? 0.2 : config.getTemperature());
            // max_tokens：推理类模型（GLM-5.1/4.7/4.5 等）思考会消耗 token，要给足
            // 这里取配置值并做下限保护，至少 8192
            int maxTokens = config.getMaxTokens() == null ? 8192 : Math.max(8192, config.getMaxTokens());
            body.put("max_tokens", maxTokens);
            // 思考类模型：清理历史思考块，控制上下文长度
            body.put("thinking", Map.of("clear_thinking", true));
            List<Map<String, Object>> messages = new ArrayList<>();
            if (StrUtil.isNotBlank(systemPrompt)) {
                messages.add(message("system", systemPrompt));
            }
            messages.add(message("user", userPrompt));
            body.put("messages", messages);
            if (json) {
                body.put("response_format", Map.of("type", "json_object"));
            }

            String reqJson = objectMapper.writeValueAsString(body);
            log.setRequestPayload(truncate(reqJson, 32000));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StrUtil.isNotBlank(config.getApiKey())) {
                headers.setBearerAuth(config.getApiKey());
            }

            String url = StrUtil.removeSuffix(config.getBaseUrl(), "/") + "/chat/completions";
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), String.class);

            String respText = response.getBody() == null ? "{}" : response.getBody();
            log.setResponsePayload(truncate(respText, 32000));

            JsonNode root = objectMapper.readTree(respText);
            JsonNode message = root.path("choices").path(0).path("message");
            String content = message.path("content").asText("");
            // 推理类模型可能把答案放在 reasoning_content 里
            if (StrUtil.isBlank(content)) {
                content = message.path("reasoning_content").asText("");
            }
            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode()) {
                log.setPromptTokens(usage.path("prompt_tokens").asInt(0));
                log.setCompletionTokens(usage.path("completion_tokens").asInt(0));
            }
            log.setDurationMs((int) (System.currentTimeMillis() - start));
            callLogMapper.insert(log);

            return new ChatResult(content, log);
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorMessage(StrUtil.maxLength(e.getMessage(), 1000));
            log.setDurationMs((int) (System.currentTimeMillis() - start));
            callLogMapper.insert(log);
            throw new BizException("AI 调用失败：" + e.getMessage());
        }
    }

    private Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...[truncated]";
    }

    /**
     * 解析返回 JSON。允许 markdown 代码块包裹（```json ... ```）。
     */
    public JsonNode parseJson(String content) {
        if (StrUtil.isBlank(content)) {
            throw new BizException("AI 返回为空");
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        try {
            return objectMapper.readTree(text);
        } catch (JsonProcessingException e) {
            throw new BizException("AI 返回不是合法 JSON：" + e.getMessage());
        }
    }

    @Data
    public static class ChatResult {
        private final String content;
        private final AiCallLog log;
    }
}
