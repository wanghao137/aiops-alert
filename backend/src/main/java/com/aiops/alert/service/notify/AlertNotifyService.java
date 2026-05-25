package com.aiops.alert.service.notify;

import com.aiops.alert.common.BizException;
import com.aiops.alert.common.Enums;
import com.aiops.alert.entity.AlertChannel;
import com.aiops.alert.entity.AlertNotifyLog;
import com.aiops.alert.mapper.AlertNotifyLogMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 通知发送统一入口：按渠道类型分发到对应的 Sender，并写流水。
 */
@Slf4j
@Service
public class AlertNotifyService {

    private final Map<String, AlertNotifySender> senderRegistry = new HashMap<>();
    private final AlertNotifyLogMapper notifyLogMapper;

    public AlertNotifyService(List<AlertNotifySender> senders,
                              AlertNotifyLogMapper notifyLogMapper) {
        for (AlertNotifySender sender : senders) {
            senderRegistry.put(sender.channelType(), sender);
        }
        this.notifyLogMapper = notifyLogMapper;
        log.info("AlertNotifyService initialized with senders: {}", senderRegistry.keySet());
    }

    /**
     * 创建一条通知记录并立即发送。返回的 log 会带最终状态。
     */
    public AlertNotifyLog dispatch(AlertNotifyLog log, AlertChannel channel) {
        if (log.getId() == null) {
            log.setSendStatus(Enums.NotifyStatus.PENDING);
            notifyLogMapper.insert(log);
        }
        if (channel == null) {
            log.setSendStatus(Enums.NotifyStatus.FAILED);
            log.setFailureReason("渠道不存在");
            notifyLogMapper.updateById(log);
            return log;
        }
        if (!Enums.Status.ENABLED.equals(channel.getStatus())) {
            log.setSendStatus(Enums.NotifyStatus.FAILED);
            log.setFailureReason("渠道未启用");
            notifyLogMapper.updateById(log);
            return log;
        }
        AlertNotifySender sender = senderRegistry.get(channel.getChannelType());
        if (sender == null) {
            log.setSendStatus(Enums.NotifyStatus.FAILED);
            log.setFailureReason("不支持的渠道类型：" + channel.getChannelType());
            notifyLogMapper.updateById(log);
            return log;
        }
        try {
            sender.send(log, channel);
        } catch (Exception e) {
            log.setSendStatus(Enums.NotifyStatus.FAILED);
            log.setFailureReason("发送异常：" + e.getMessage());
        }
        notifyLogMapper.updateById(log);
        return log;
    }

    /** 根据渠道类型获取 Sender，必要时抛错。 */
    public AlertNotifySender requireSender(String channelType) {
        AlertNotifySender sender = senderRegistry.get(channelType);
        if (sender == null) {
            throw new BizException("不支持的渠道类型：" + channelType);
        }
        return sender;
    }
}
