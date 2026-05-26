package com.aiops.alert.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 命令面板的标准回复结构。
 * intent：路由到具体能力（list_events / count_events / route / unknown ...）
 * answer：人类可读摘要（一两句话）
 * data：可选的结构化结果（事件列表、跳转目标等），给前端快速渲染
 */
@Data
@Builder
public class CommandResponse {

    private String intent;
    private String answer;
    /** 跳转：当 intent=route 时，前端跳到 path */
    private String routePath;
    /** 事件列表（用于 list_events） */
    private List<AlertEventResponse> events;
    /** 数字结果（用于 count_*） */
    private Long total;
    private Long pending;
    private Long critical;
    /** 兜底：原始模型输出 */
    private String rawOutput;
    /** 模型思考过程（推理类模型） */
    private String reasoning;
    private String modelName;
    private Integer durationMs;
}
