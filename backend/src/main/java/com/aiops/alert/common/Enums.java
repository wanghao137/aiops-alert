package com.aiops.alert.common;

import java.util.Arrays;
import java.util.List;

/**
 * 统一枚举与字典常量。
 */
public final class Enums {

    private Enums() {}

    /** 监控对象类型 */
    public static final class ObjectType {
        public static final String SERVER = "SERVER";
        public static final String DATABASE = "DATABASE";
        public static final String SYNC_JOB = "SYNC_JOB";
        public static final String PROCESS_JOB = "PROCESS_JOB";

        public static final List<String> ALL = Arrays.asList(SERVER, DATABASE, SYNC_JOB, PROCESS_JOB);

        public static boolean isValid(String type) {
            return type != null && ALL.contains(type);
        }
    }

    /** 渠道类型 */
    public static final class ChannelType {
        public static final String WECOM = "WECOM";
        public static final String EMAIL = "EMAIL";
        public static final String SMS = "SMS";
    }

    /** 通用启停状态 */
    public static final class Status {
        public static final String ENABLED = "ENABLED";
        public static final String DISABLED = "DISABLED";
    }

    /** 告警级别 */
    public static final class AlertLevel {
        public static final String NOTICE = "NOTICE";
        public static final String NORMAL = "NORMAL";
        public static final String SERIOUS = "SERIOUS";
        public static final String CRITICAL = "CRITICAL";
    }

    /** 告警事件状态 */
    public static final class EventStatus {
        public static final String PENDING = "PENDING";
        public static final String CONFIRMED = "CONFIRMED";
        public static final String RECOVERED = "RECOVERED";
        public static final String CLOSED = "CLOSED";
    }

    /** 通知发送状态 */
    public static final class NotifyStatus {
        public static final String PENDING = "PENDING";
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
    }
}
