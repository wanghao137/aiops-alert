package com.aiops.alert.service.stream;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 推送中心：广播告警事件、AI 摘要进度等。
 *
 * 设计要点：
 * - SseEmitter 超时设为 30 分钟，比典型反向代理的 60s 长但不无限挂；
 * - 每 20 秒推一条心跳事件，让中间代理 / 浏览器 / 客户端知道连接活着；
 * - 任何 send 异常都把 emitter 移除，让前端自动重连。
 */
@Slf4j
@Service
public class AlertStreamService {

    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<Long, SseEmitter> clients = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public SseEmitter subscribe() {
        Long id = seq.incrementAndGet();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitter.onCompletion(() -> remove(id, "complete"));
        emitter.onTimeout(() -> remove(id, "timeout"));
        emitter.onError(e -> remove(id, "error: " + e.getMessage()));
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
                remove(entry.getKey(), "broadcast failed");
            }
        }
    }

    /** 心跳：每 20s 给所有客户端发一条 ping，避免代理掐断空闲连接 */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        if (clients.isEmpty()) return;
        long ts = Instant.now().toEpochMilli();
        for (Map.Entry<Long, SseEmitter> entry : clients.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().name("ping").data(Map.of("ts", ts)));
            } catch (Exception e) {
                remove(entry.getKey(), "ping failed");
            }
        }
    }

    public int clientCount() {
        return clients.size();
    }

    private void remove(Long id, String reason) {
        if (clients.remove(id) != null) {
            log.debug("SSE client {} removed: {} (remaining {})", id, reason, clients.size());
        }
    }
}
