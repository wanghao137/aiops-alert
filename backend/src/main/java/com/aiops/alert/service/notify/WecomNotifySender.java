package com.aiops.alert.service.notify;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.Enums;
import com.aiops.alert.entity.AlertChannel;
import com.aiops.alert.entity.AlertNotifyLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 企业微信群机器人 webhook 发送。
 *
 * configJson 示例：
 * {
 *   "webhook": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx",
 *   "mentionedMobileList": "13800000001,13800000002"
 * }
 */
@Slf4j
@Component
public class WecomNotifySender implements AlertNotifySender {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WecomNotifySender(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(8))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String channelType() {
        return Enums.ChannelType.WECOM;
    }

    @Override
    public void send(AlertNotifyLog notifyLog, AlertChannel channel) {
        try {
            JsonNode config = parseConfig(channel.getConfigJson());
            String webhook = text(config, "webhook");
            if (StrUtil.isBlank(webhook)) {
                fail(notifyLog, "企业微信 webhook 不能为空");
                return;
            }

            Map<String, Object> markdown = new HashMap<>();
            markdown.put("content", buildMarkdown(notifyLog, text(config, "mentionedMobileList")));
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate
                    .postForEntity(webhook, new HttpEntity<>(body, headers), String.class);

            JsonNode result = objectMapper.readTree(
                    response.getBody() == null ? "{}" : response.getBody());
            int errCode = result.path("errcode").asInt(-1);
            if (errCode == 0) {
                notifyLog.setSendStatus(Enums.NotifyStatus.SUCCESS);
                notifyLog.setSentAt(LocalDateTime.now());
                notifyLog.setProviderMsgId(result.path("msgid").asText(""));
                notifyLog.setFailureReason(null);
            } else {
                fail(notifyLog, "企业微信返回失败：" + result.path("errmsg").asText(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("wecom send error: {}", e.getMessage());
            fail(notifyLog, "企业微信发送异常：" + e.getMessage());
        }
    }

    private String buildMarkdown(AlertNotifyLog notifyLog, String mentionedMobileList) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(safe(notifyLog.getNotifyTitle())).append("\n");
        sb.append("> ").append(safe(notifyLog.getNotifyContent())).append("\n\n");
        if (StrUtil.isNotBlank(notifyLog.getReceiverValue())) {
            sb.append("接收人：").append(notifyLog.getReceiverValue()).append("\n");
        }
        if (StrUtil.isNotBlank(mentionedMobileList)) {
            for (String phone : mentionedMobileList.split(",")) {
                if (StrUtil.isNotBlank(phone)) {
                    sb.append("<@").append(phone.trim()).append("> ");
                }
            }
        }
        return sb.toString();
    }

    private JsonNode parseConfig(String configJson) throws Exception {
        if (StrUtil.isBlank(configJson)) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(configJson);
    }

    private String text(JsonNode node, String field) {
        return node == null || node.path(field).isMissingNode() ? "" : node.path(field).asText("");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void fail(AlertNotifyLog notifyLog, String reason) {
        notifyLog.setSendStatus(Enums.NotifyStatus.FAILED);
        notifyLog.setFailureReason(reason);
    }
}
