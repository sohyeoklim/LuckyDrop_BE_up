package luckydrop.demo.draw.sse;

import lombok.extern.slf4j.Slf4j;
import luckydrop.demo.draw.enums.DrawStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DrawSseService {

    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
        } catch (IOException exception) {
            emitters.remove(emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publishStatusChanged(Long drawId, DrawStatus status) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendStatusChanged(drawId, status);
                }
            });
            return;
        }

        sendStatusChanged(drawId, status);
    }

    private void sendStatusChanged(Long drawId, DrawStatus status) {
        DrawStatusEvent event = new DrawStatusEvent(drawId, status, LocalDateTime.now());
        emitters.forEach(emitter -> send(emitter, event));
    }

    private void send(SseEmitter emitter, DrawStatusEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name("draw-status-changed")
                    .data(event));
        } catch (IOException | IllegalStateException exception) {
            emitters.remove(emitter);
            emitter.complete();
        }
    }
}
