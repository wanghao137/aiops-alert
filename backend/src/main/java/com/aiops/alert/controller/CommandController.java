package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.dto.CommandRequest;
import com.aiops.alert.dto.CommandResponse;
import com.aiops.alert.service.ai.CommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/command")
public class CommandController {

    private final CommandService service;

    public CommandController(CommandService service) {
        this.service = service;
    }

    @PostMapping
    public Result<CommandResponse> command(@Valid @RequestBody CommandRequest request) {
        return Result.success(service.handle(request));
    }
}
