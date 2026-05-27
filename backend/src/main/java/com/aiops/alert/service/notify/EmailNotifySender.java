package com.aiops.alert.service.notify;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.Enums;
import com.aiops.alert.entity.AlertChannel;
import com.aiops.alert.entity.AlertNotifyLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 邮件渠道发送器（SMTP）。
 *
 * configJson 示例：
 * {
 *   "host": "smtp.exmail.qq.com",
 *   "port": 465,
 *   "username": "alert@example.com",
 *   "password": "xxxx",
 *   "from": "alert@example.com",
 *   "fromName": "AIOps Alert",
 *   "ssl": true,
 *   "defaultReceivers": "ops@example.com,team@example.com"
 * }
 */
@Slf4j
@Component
public class EmailNotifySender implements AlertNotifySender {

    private final ObjectMapper objectMapper;

    public EmailNotifySender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String channelType() {
        return Enums.ChannelType.EMAIL;
    }

    @Override
    public void send(AlertNotifyLog notifyLog, AlertChannel channel) {
        try {
            JsonNode config = parseConfig(channel.getConfigJson());
            if (config.path("dryRun").asBoolean(false)) {
                notifyLog.setSendStatus(Enums.NotifyStatus.SUCCESS);
                notifyLog.setSentAt(LocalDateTime.now());
                notifyLog.setProviderMsgId("EMAIL-DRYRUN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                notifyLog.setFailureReason("dryRun 模式，未真实发送");
                return;
            }
            String host = text(config, "host");
            String username = text(config, "username");
            String password = text(config, "password");
            if (StrUtil.isBlank(host) || StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
                fail(notifyLog, "SMTP host / username / password 必填");
                return;
            }
            int port = config.path("port").asInt(465);
            boolean ssl = config.path("ssl").asBoolean(true);
            String from = StrUtil.blankToDefault(text(config, "from"), username);
            String fromName = StrUtil.blankToDefault(text(config, "fromName"), "AIOps Alert");

            String receivers = StrUtil.blankToDefault(notifyLog.getReceiverValue(),
                    text(config, "defaultReceivers"));
            if (StrUtil.isBlank(receivers)) {
                fail(notifyLog, "邮件接收人不能为空");
                return;
            }

            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(port);
            sender.setUsername(username);
            sender.setPassword(password);
            sender.setDefaultEncoding("UTF-8");
            Properties props = sender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            if (ssl) {
                props.put("mail.smtp.ssl.enable", "true");
            } else {
                props.put("mail.smtp.starttls.enable", "true");
            }
            props.put("mail.smtp.timeout", "8000");
            props.put("mail.smtp.connectiontimeout", "5000");

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            try {
                helper.setFrom(new jakarta.mail.internet.InternetAddress(from, fromName, "UTF-8"));
            } catch (Exception ignore) {
                helper.setFrom(from);
            }
            helper.setTo(splitReceivers(receivers));
            helper.setSubject(StrUtil.blankToDefault(notifyLog.getNotifyTitle(), "AIOps Alert 告警"));
            helper.setText(buildHtml(notifyLog), true);

            sender.send(message);

            notifyLog.setSendStatus(Enums.NotifyStatus.SUCCESS);
            notifyLog.setSentAt(LocalDateTime.now());
            notifyLog.setProviderMsgId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            notifyLog.setFailureReason(null);
        } catch (MessagingException e) {
            log.warn("email send error: {}", e.getMessage());
            fail(notifyLog, "邮件发送异常：" + e.getMessage());
        } catch (Exception e) {
            log.warn("email send error: {}", e.getMessage());
            fail(notifyLog, "邮件发送异常：" + e.getMessage());
        }
    }

    private String[] splitReceivers(String receivers) {
        return java.util.Arrays.stream(receivers.split("[,，;；\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private String buildHtml(AlertNotifyLog log) {
        String title = safe(log.getNotifyTitle());
        String content = safe(log.getNotifyContent()).replace("\n", "<br/>");
        return """
                <div style="font-family: 'Segoe UI', PingFang SC, sans-serif; padding: 20px; background:#0F172A; color:#F8FAFC;">
                  <div style="max-width: 640px; margin: 0 auto; padding: 24px; border-radius: 12px;
                              background: #1F2937; border: 1px solid #374151;">
                    <div style="font-size: 12px; color: #93C5FD; letter-spacing: 1.4px;">AIOPS ALERT</div>
                    <h2 style="margin: 8px 0 16px; font-size: 18px;">%s</h2>
                    <div style="color: #CBD5E1; line-height: 1.7; font-size: 14px;">%s</div>
                  </div>
                </div>
                """.formatted(title, content);
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

    private void fail(AlertNotifyLog log, String reason) {
        log.setSendStatus(Enums.NotifyStatus.FAILED);
        log.setFailureReason(reason);
    }
}
