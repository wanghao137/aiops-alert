package com.aiops.alert.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.BizException;
import com.aiops.alert.common.Enums;
import com.aiops.alert.dto.LlmModelConfigRequest;
import com.aiops.alert.dto.LlmModelConfigResponse;
import com.aiops.alert.dto.LlmTestResponse;
import com.aiops.alert.entity.LlmModelConfig;
import com.aiops.alert.mapper.LlmModelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LLM 模型配置服务。
 */
@Service
public class LlmModelConfigService {

    private final LlmModelConfigMapper mapper;

    public LlmModelConfigService(LlmModelConfigMapper mapper) {
        this.mapper = mapper;
    }

    public List<LlmModelConfigResponse> list() {
        return mapper.selectList(new LambdaQueryWrapper<LlmModelConfig>()
                        .orderByDesc(LlmModelConfig::getIsDefault)
                        .orderByDesc(LlmModelConfig::getUpdatedAt))
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public LlmModelConfigResponse save(LlmModelConfigRequest request) {
        LlmModelConfig entity;
        if (request.getId() == null) {
            entity = new LlmModelConfig();
        } else {
            entity = mapper.selectById(request.getId());
            if (entity == null) {
                throw new BizException("配置不存在");
            }
        }

        String code = StrUtil.blankToDefault(request.getConfigCode(),
                "LLM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());

        entity.setConfigCode(code);
        entity.setConfigName(request.getConfigName());
        entity.setProvider(StrUtil.blankToDefault(request.getProvider(), "OPENAI"));
        entity.setBaseUrl(request.getBaseUrl());
        // apiKey 为空时保留原值，避免编辑场景被清空
        if (StrUtil.isNotBlank(request.getApiKey())) {
            entity.setApiKey(request.getApiKey());
        }
        entity.setModelName(request.getModelName());
        entity.setTemperature(request.getTemperature() == null ? new BigDecimal("0.20") : request.getTemperature());
        entity.setMaxTokens(request.getMaxTokens() == null ? 2048 : request.getMaxTokens());
        entity.setStatus(StrUtil.blankToDefault(request.getStatus(), Enums.Status.ENABLED));
        entity.setDescription(request.getDescription());
        entity.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()) ? 1 : 0);

        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            mapper.update(null, new LambdaUpdateWrapper<LlmModelConfig>()
                    .ne(LlmModelConfig::getId, entity.getId())
                    .set(LlmModelConfig::getIsDefault, 0));
        }
        return toResponse(entity);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Transactional
    public LlmModelConfigResponse setDefault(Long id) {
        LlmModelConfig entity = mapper.selectById(id);
        if (entity == null) {
            throw new BizException("配置不存在");
        }
        mapper.update(null, new LambdaUpdateWrapper<LlmModelConfig>()
                .set(LlmModelConfig::getIsDefault, 0));
        entity.setIsDefault(1);
        mapper.updateById(entity);
        return toResponse(entity);
    }

    /** 拿到默认 + 启用状态的配置；若无则报业务错。 */
    public LlmModelConfig requireDefault() {
        LlmModelConfig config = mapper.selectOne(new LambdaQueryWrapper<LlmModelConfig>()
                .eq(LlmModelConfig::getIsDefault, 1)
                .eq(LlmModelConfig::getStatus, Enums.Status.ENABLED)
                .last("limit 1"));
        if (config == null) {
            // 兜底：启用的任意一条
            config = mapper.selectOne(new LambdaQueryWrapper<LlmModelConfig>()
                    .eq(LlmModelConfig::getStatus, Enums.Status.ENABLED)
                    .orderByDesc(LlmModelConfig::getUpdatedAt)
                    .last("limit 1"));
        }
        if (config == null) {
            throw new BizException("尚未配置可用的 LLM 模型，请先到「系统设置」添加");
        }
        if (StrUtil.isBlank(config.getBaseUrl()) || StrUtil.isBlank(config.getModelName())) {
            throw new BizException("LLM 配置不完整：baseUrl / modelName 必填");
        }
        return config;
    }

    public boolean hasUsableConfig() {
        try {
            requireDefault();
            return true;
        } catch (BizException e) {
            return false;
        }
    }

    /**
     * 测试连通性：直接调一次极简 prompt，验证 baseUrl + apiKey + model 可用。
     * 不依赖默认配置，按 id 测目标配置。
     */
    public LlmTestResponse test(Long id) {
        LlmModelConfig config = mapper.selectById(id);
        if (config == null) {
            throw new BizException("配置不存在");
        }
        if (StrUtil.isBlank(config.getBaseUrl()) || StrUtil.isBlank(config.getModelName())) {
            throw new BizException("baseUrl / modelName 必填");
        }

        long start = System.currentTimeMillis();
        try {
            // 直接构造一次最小请求验证连通；复用 RestTemplate
            org.springframework.boot.web.client.RestTemplateBuilder builder =
                    new org.springframework.boot.web.client.RestTemplateBuilder()
                            .setConnectTimeout(java.time.Duration.ofSeconds(10))
                            .setReadTimeout(java.time.Duration.ofSeconds(60));
            org.springframework.web.client.RestTemplate rt = builder.build();
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", config.getModelName());
            body.put("temperature", 0.2);
            // GLM-5.1/GLM-5/GLM-4.7 等模型强制思考(thinking compulsorily)，给小了 content 会为空
            // 4096 留给思考 + 简短回答足够，最大耗时也可控
            body.put("max_tokens", 4096);
            body.put("messages", java.util.List.of(
                    java.util.Map.of("role", "user", "content", "用 2 个字回答：你好")
            ));
            // 思考类模型清理历史思考块（仅智谱端点支持，其他厂商会忽略）
            if (config.getBaseUrl() != null && config.getBaseUrl().contains("bigmodel.cn")) {
                body.put("thinking", java.util.Map.of("clear_thinking", true));
            }

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            if (StrUtil.isNotBlank(config.getApiKey())) {
                headers.setBearerAuth(config.getApiKey());
            }

            String url = StrUtil.removeSuffix(config.getBaseUrl(), "/") + "/chat/completions";
            org.springframework.http.ResponseEntity<String> resp = rt.postForEntity(
                    url, new org.springframework.http.HttpEntity<>(body, headers), String.class);
            String respText = resp.getBody() == null ? "{}" : resp.getBody();
            com.fasterxml.jackson.databind.ObjectMapper jm = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = jm.readTree(respText);
            com.fasterxml.jackson.databind.JsonNode choice0 = root.path("choices").path(0);
            com.fasterxml.jackson.databind.JsonNode message = choice0.path("message");
            String content = message.path("content").asText("");
            String reasoning = message.path("reasoning_content").asText("");
            String finishReason = choice0.path("finish_reason").asText("");
            int duration = (int) (System.currentTimeMillis() - start);

            // 1) 正常 content：成功
            if (StrUtil.isNotBlank(content)) {
                String reply = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                return LlmTestResponse.builder()
                        .success(true)
                        .reply(reply)
                        .reasoning(reasoning)
                        .durationMs(duration)
                        .modelName(config.getModelName())
                        .build();
            }
            // 2) 没有 content 但有 reasoning：模型仍在思考被截断（length），算半成功，提示用户
            if (StrUtil.isNotBlank(reasoning)) {
                String hint = reasoning.length() > 80 ? reasoning.substring(0, 80) + "..." : reasoning;
                return LlmTestResponse.builder()
                        .success(true)
                        .reply("模型可达（思考输出: " + hint + "），但 max_tokens 偏小，业务调用建议 ≥ 8192")
                        .reasoning(reasoning)
                        .durationMs(duration)
                        .modelName(config.getModelName())
                        .build();
            }
            // 3) 真的什么都没有
            return LlmTestResponse.builder()
                    .success(false)
                    .error("模型返回空内容（finish_reason=" + finishReason
                            + "），可能 baseUrl 路径不对，或 API Key 无效")
                    .durationMs(duration)
                    .modelName(config.getModelName())
                    .build();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            String hint;
            if (status == 401 || status == 403) {
                hint = "认证失败：API Key 无效，或套餐已到期/未授权该 baseUrl";
            } else if (status == 404) {
                hint = "端点不存在：检查 baseUrl 路径与模型名是否匹配（智谱 Coding Plan 用 /api/coding/paas/v4，普通用 /api/paas/v4）";
            } else if (status == 429) {
                hint = "限流：请稍后重试或升级套餐";
            } else if (status >= 500) {
                hint = "AI 服务端 5xx 错误";
            } else {
                hint = "HTTP " + status;
            }
            return LlmTestResponse.builder()
                    .success(false)
                    .error(hint + " | 原始响应: " + StrUtil.maxLength(e.getResponseBodyAsString(), 200))
                    .durationMs((int) (System.currentTimeMillis() - start))
                    .modelName(config.getModelName())
                    .build();
        } catch (org.springframework.web.client.ResourceAccessException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            String hint;
            if (msg.contains("connection reset")) {
                hint = "连接被对端中断（Connection reset），可能是端点不接受此调用方式或瞬时网络抖动";
            } else if (msg.contains("timeout") || msg.contains("timed out")) {
                hint = "网络/读取超时";
            } else if (msg.contains("unknownhost")) {
                hint = "域名解析失败：检查网络代理/DNS 或 baseUrl 拼写";
            } else {
                hint = "网络错误";
            }
            return LlmTestResponse.builder()
                    .success(false)
                    .error(hint + ": " + StrUtil.maxLength(e.getMessage(), 200))
                    .durationMs((int) (System.currentTimeMillis() - start))
                    .modelName(config.getModelName())
                    .build();
        } catch (Exception e) {
            return LlmTestResponse.builder()
                    .success(false)
                    .error(StrUtil.maxLength(e.getMessage(), 800))
                    .durationMs((int) (System.currentTimeMillis() - start))
                    .modelName(config.getModelName())
                    .build();
        }
    }

    private LlmModelConfigResponse toResponse(LlmModelConfig entity) {
        return LlmModelConfigResponse.builder()
                .id(entity.getId())
                .configCode(entity.getConfigCode())
                .configName(entity.getConfigName())
                .provider(entity.getProvider())
                .baseUrl(entity.getBaseUrl())
                .apiKeyMasked(maskKey(entity.getApiKey()))
                .modelName(entity.getModelName())
                .temperature(entity.getTemperature())
                .maxTokens(entity.getMaxTokens())
                .isDefault(entity.getIsDefault() != null && entity.getIsDefault() == 1)
                .status(entity.getStatus())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String maskKey(String key) {
        if (StrUtil.isBlank(key)) return "";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
