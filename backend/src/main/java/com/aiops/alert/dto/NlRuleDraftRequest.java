package com.aiops.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NlRuleDraftRequest {

    @NotBlank(message = "请输入需求描述")
    @Size(max = 1000, message = "描述不能超过 1000 字")
    private String prompt;
}
