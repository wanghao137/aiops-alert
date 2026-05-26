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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * LLM 调用客户端 (OpenAI 兼容协议)。
 *
 * 所有 LLM 业务都走这里：
 * - 自动选择默认模型配置
 * - 自动写 ai_call_log 流水
 * - 自动重试瞬态网络错误（Connection reset / read timeout）
 * - 提供 chat 与 chatJson 两种入口（chatJson 强制 response_format=json_object）
 *
 * 兼容多家 OpenAI-style 端点：
 * - 智谱 BigModel  https://open.bigmodel.cn/api/paas/v4
 *   (Coding Plan)  https://open.bigmodel.cn/api/coding/paas/v4
 * - DeepSeek      https://api.deepseek.com/v1
 * - 阿里通义       https://dashscope.aliyuncs.com/compatible-mode/v1
 * - OpenAI / 任何 OpenAI 兼容代理
 */
@Slf4j
@Component
public class LlmClient {

    /** HTTP 读超时（包含模型推理时长，DAILY_BRIEF 经常 100s+） */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(240);
    /** 连接超时 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** 瞬态错误最大重试次数（不含首次） */
    private static final int MAX_RETRIES = 1;
    /** 重试间隔（毫秒） */
    private static final long RETRY_BACKOFF_MS = 800L;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final LlmModelConfigService configService;
    private final AiCallLogMapper callLogMapper;

    public LlmClient(RestTemplateBuilder builder,
                     ObjectMapper objectMapper,
                     LlmModelConfigService configService,
                     AiCallLogMapper callLogMapper) {
        this.restTemplate = builder
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
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

        AiCallLog logRow = new AiCallLog();
        logRow.setScene(scene);
        logRow.setModelConfigId(config.getId());
        logRow.setModelName(config.getModelName());
        logRow.setStatus("SUCCESS");

        Map<String, Object> body = buildRequestBody(config, systemPrompt, userPrompt, json);
        try {
            logRow.setRequestPayload(truncate(objectMapper.writeValueAsString(body), 32000));
        } catch (JsonProcessingException ignored) {
            // 不影响主流程
        }

        HttpHeaders headers = buildHeaders(config);
        String url = StrUtil.removeSuffix(config.getBaseUrl(), "/") + "/chat/completions";

        long start = System.currentTimeMillis();
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(
                        url, new HttpEntity<>(body, headers), String.class);
                ChatResult result = parseResponse(response, logRow, start);
                if (attempt > 0) {
                    log.info("LLM 调用第 {} 次重试成功，scene={}", attempt, scene);
                }
                return result;
            } catch (ResourceAccessException e) {
                // 网络层错误：connection reset / read timeout / SSL handshake 等 → 可重试
                lastException = e;
                log.warn("LLM 网络错误（第 {}/{} 次）scene={} url={} : {}",
                        attempt + 1, MAX_RETRIES + 1, scene, url, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    sleep(RETRY_BACKOFF_MS * (attempt + 1));
                    continue;
                }
            } catch (HttpStatusCodeException e) {
                // 服务端 4xx/5xx：4xx 通常是配置/认证错误，不重试；5xx 重试一次
                lastException = e;
                int status = e.getStatusCode().value();
                String errBody = StrUtil.maxLength(e.getResponseBodyAsString(), 500);
                log.warn("LLM HTTP {} scene={} url={} body={}", status, scene, url, errBody);
                if (status >= 500 && attempt < MAX_RETRIES) {
                    sleep(RETRY_BACKOFF_MS * (attempt + 1));
                    continue;
                }
                break;
            } catch (Exception e) {
                // 其他异常：响应解析等 → 不重试
                lastException = e;
                log.warn("LLM 调用异常 scene={} : {}", scene, e.getMessage());
                break;
            }
        }

        // 所有重试都失败
        logRow.setStatus("FAILED");
        logRow.setErrorMessage(StrUtil.maxLength(toFriendlyError(lastException), 1000));
        logRow.setDurationMs((int) (System.currentTimeMillis() - start));
        callLogMapper.insert(logRow);
        throw new BizException(toFriendlyError(lastException));
    }

    private Map<String, Object> buildRequestBody(LlmModelConfig config, String systemPrompt,
                                                  String userPrompt, boolean json) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.getModelName());
        body.put("temperature", config.getTemperature() == null ? 0.2 : config.getTemperature());
        // max_tokens：推理类模型（GLM-5.1/4.7/4.5 等）思考会消耗 token，要给足
        // 这里取配置值并做下限保护，至少 8192
        int maxTokens = config.getMaxTokens() == null ? 8192 : Math.max(8192, config.getMaxTokens());
        body.put("max_tokens", maxTokens);

        // 智谱思考类模型独有：清理历史思考块，控制上下文长度。
        // 其他厂商（DeepSeek/OpenAI/通义）会忽略这个未知字段，所以加上不会报错。
        if (isZhipuEndpoint(config.getBaseUrl())) {
            body.put("thinking", Map.of("clear_thinking", true));
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(message("system", systemPrompt));
        }
        messages.add(message("user", userPrompt));
        body.put("messages", messages);

        if (json) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    private HttpHeaders buildHeaders(LlmModelConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StrUtil.isNotBlank(config.getApiKey())) {
            headers.setBearerAuth(config.getApiKey());
        }
        return headers;
    }

    private ChatResult parseResponse(ResponseEntity<String> response, AiCallLog logRow, long start)
            throws JsonProcessingException {
        String respText = response.getBody() == null ? "{}" : response.getBody();
        logRow.setResponsePayload(truncate(respText, 32000));

        JsonNode root = objectMapper.readTree(respText);
        JsonNode message = root.path("choices").path(0).path("message");
        String content = message.path("content").asText("");
        // 推理类模型把思考过程放在 reasoning_content
        String reasoning = message.path("reasoning_content").asText("");
        // 兜底：如果没有正文，就用思考内容当结果（避免空响应）
        if (StrUtil.isBlank(content) && StrUtil.isNotBlank(reasoning)) {
            content = reasoning;
        }
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode()) {
            logRow.setPromptTokens(usage.path("prompt_tokens").asInt(0));
            logRow.setCompletionTokens(usage.path("completion_tokens").asInt(0));
        }
        logRow.setReasoningContent(truncate(reasoning, 32000));
        logRow.setDurationMs((int) (System.currentTimeMillis() - start));
        callLogMapper.insert(logRow);

        return new ChatResult(content, reasoning, logRow);
    }

    /** 是否是智谱 BigModel 端点（包括通用 paas 和 coding plan）。 */
    private boolean isZhipuEndpoint(String baseUrl) {
        return baseUrl != null && baseUrl.contains("bigmodel.cn");
    }

    /** 把底层异常转成对用户友好的提示。 */
    private String toFriendlyError(Exception e) {
        if (e == null) return "AI 调用失败：未知错误";
        if (e instanceof ResourceAccessException) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("connection reset") || msg.contains("socketexception")) {
                return "AI 服务连接被中断，可能是网络抖动或限流，请稍后重试";
            }
            if (msg.contains("timed out") || msg.contains("timeout")) {
                return "AI 服务响应超时（>240s），模型负载过高或上下文过长，请稍后重试";
            }
            if (msg.contains("unknownhostexception") || msg.contains("nodename")) {
                return "无法解析 AI 服务域名，请检查网络或 baseUrl 配置";
            }
            return "AI 服务网络错误：" + StrUtil.maxLength(e.getMessage(), 200);
        }
        if (e instanceof HttpStatusCodeException) {
            HttpStatusCodeException he = (HttpStatusCodeException) e;
            int status = he.getStatusCode().value();
            if (status == 401 || status == 403) {
                return "AI 服务认证失败（HTTP " + status + "），请检查 API Key 是否正确或套餐是否到期";
            }
            if (status == 429) {
                return "AI 服务限流（HTTP 429），请稍后重试或升级套餐";
            }
            if (status == 404) {
                return "AI 服务端点不存在（HTTP 404），请检查 baseUrl 与模型名是否匹配";
            }
            return "AI 服务返回错误 HTTP " + status + "：" + StrUtil.maxLength(he.getResponseBodyAsString(), 200);
        }
        return "AI 调用失败：" + StrUtil.maxLength(e.getMessage(), 200);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        /** 模型最终回复（content） */
        private final String content;
        /** 模型思考过程（reasoning_content），推理类模型才有 */
        private final String reasoning;
        /** 调用流水 */
        private final AiCallLog log;
    }
}
