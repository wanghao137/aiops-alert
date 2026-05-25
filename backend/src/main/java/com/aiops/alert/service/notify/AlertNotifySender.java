package com.aiops.alert.service.notify;

import com.aiops.alert.entity.AlertChannel;
import com.aiops.alert.entity.AlertNotifyLog;

/**
 * 渠道发送器抽象。
 */
public interface AlertNotifySender {

    /** 渠道类型，对应 alert_channel.channel_type */
    String channelType();

    /**
     * 发送通知。实现需要直接更新 log 的 sendStatus / providerMsgId / failureReason / sentAt。
     */
    void send(AlertNotifyLog log, AlertChannel channel);
}
