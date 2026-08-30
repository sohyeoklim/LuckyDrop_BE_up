package luckydrop.demo.notification;

import luckydrop.demo.realtime.event.NotificationCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class NotificationSseService {

    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private final ConcurrentMap<Long, Set<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emittersByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
        } catch (IOException exception) {
            remove(userId, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publishAfterCommit(Notification notification) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(notification);
                }
            });
            return;
        }
        publish(notification);
    }

    private void publish(Notification notification) {
        Set<SseEmitter> emitters = emittersByUser.get(notification.getUserId());
        if (emitters == null) return;

        NotificationResponse response = NotificationResponse.from(notification);
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("notification").data(response));
            } catch (IOException | IllegalStateException exception) {
                remove(notification.getUserId(), emitter);
                emitter.complete();
            }
        });
    }

    private void remove(Long userId, SseEmitter emitter) {
        emittersByUser.computeIfPresent(userId, (ignored, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    public void sendToLocalUserEmitters(NotificationCreatedEvent event) {
        Set<SseEmitter> emitters = emittersByUser.get(event.userId());

        if (emitters == null) {
            return;
        }

        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(event));
            } catch (IOException | IllegalStateException exception) {
                remove(event.userId(), emitter);
                emitter.complete();
            }
        });
    }
}
