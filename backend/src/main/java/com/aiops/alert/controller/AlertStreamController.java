package com.aiops.alert.controller;

import com.aiops.alert.service.stream.AlertStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/stream")
public class AlertStreamController {

    private final AlertStreamService streamService;

    public AlertStreamController(AlertStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(value = "/alerts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return streamService.subscribe();
    }
}
