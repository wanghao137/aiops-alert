package com.aiops.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MonitorObjectRequest {

    /** 主键，新建为空 */
    private Long id;

    /** 对象编码，可不填，由后端自动生成 */
    @Size(max = 64)
    private String objectCode;

    @NotBlank(message = "对象名称不能为空")
    @Size(max = 128, message = "对象名称不能超过 128 个字符")
    private String objectName;

    @NotBlank(message = "对象类型不能为空")
    private String objectType;

    private String ownerName;
    private String ownerPhone;
    private String tags;

    /** ENABLED / DISABLED，默认 ENABLED */
    private String status;

    private String description;

    /** 扩展配置 JSON 字符串 */
    private String extConfig;
}
