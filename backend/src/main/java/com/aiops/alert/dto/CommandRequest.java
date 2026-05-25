package com.aiops.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommandRequest {

    @NotBlank(message = "请输入命令")
    @Size(max = 500)
    private String prompt;
}
