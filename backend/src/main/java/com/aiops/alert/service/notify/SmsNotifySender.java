package com.aiops.alert.service.notify;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.Enums;
import com.aiops.alert.entity.AlertChannel;
import com.aiops.alert.entity.AlertNotifyLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短信渠道（占位实现）。
 *
 * 真实接入需要根据具体服务商（阿里云/腾讯云/华为云）调用其 SDK，本期保留扩展点：
 * 当 configJson.dryRun = true 时，直接标记成功，便于演示。
 *
 * configJson 示例：
 * { "provider":"aliyun","accessKey":"xx","secretKey":"xx","sign":"AIOps","template":"SMS_xxx","dryRun":true }
 */
@Slf4j
@Component
public class SmsNotifySender implements AlertNotifySender {

    private final ObjectMapper objectMapper;

    public SmsNotifySender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String channelType() {
        return Enums.ChannelType.SMS;
    }

    @Override
    public void send(AlertNotifyLog notifyLog, AlertChannel channel) {
        try {
            JsonNode config = parseConfig(channel.getConfigJson());
            String receivers = notifyLog.getReceiverValue();
            if (StrUtil.isBlank(receivers)) {
                fail(notifyLog, "短信接收人不能为空");
                return;
            }
            boolean dryRun = config.path("dryRun").asBoolean(true);
            if (dryRun) {
                notifyLog.setSendStatus(Enums.NotifyStatus.SUCCESS);
                notifyLog.setSentAt(LocalDateTime.now());
                notifyLog.setProviderMsgId("MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                notifyLog.setFailureReason("dryRun 模式，未真实发送");
                return;
            }
            // TODO: 接入实际短信服务商
            fail(notifyLog, "短信渠道暂未接入真实服务商，请将 configJson.dryRun 设为 true 用于演示");
        } catch (Exception e) {
            log.warn("sms send error: {}", e.getMessage());
            fail(notifyLog, "短信发送异常：" + e.getMessage());
        }
    }

    private JsonNode parseConfig(String configJson) throws Exception {
        if (StrUtil.isBlank(configJson)) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(configJson);
    }

    private void fail(AlertNotifyLog log, String reason) {
        log.setSendStatus(Enums.NotifyStatus.FAILED);
        log.setFailureReason(reason);
    }
}
