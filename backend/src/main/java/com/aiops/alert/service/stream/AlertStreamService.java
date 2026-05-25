package com.aiops.alert.service.stream;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 推送中心：广播告警事件、AI 摘要进度等。
 */
@Slf4j
@Service
public class AlertStreamService {

    private final Map<Long, SseEmitter> clients = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public SseEmitter subscribe() {
        Long id = seq.incrementAndGet();
        SseEmitter emitter = new SseEmitter(0L); // 不超时
        emitter.onCompletion(() -> clients.remove(id));
        emitter.onTimeout(() -> clients.remove(id));
        emitter.onError(e -> clients.remove(id));
        clients.put(id, emitter);
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("clientId", id)));
        } catch (IOException ignore) {
        }
        log.info("SSE client connected: {} (total {})", id, clients.size());
        return emitter;
    }

    public void broadcast(String event, Object payload) {
        if (clients.isEmpty()) return;
        for (Map.Entry<Long, SseEmitter> entry : clients.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().name(event).data(payload));
            } catch (Exception e) {
                clients.remove(entry.getKey());
            }
        }
    }

    public int clientCount() {
        return clients.size();
    }
}
