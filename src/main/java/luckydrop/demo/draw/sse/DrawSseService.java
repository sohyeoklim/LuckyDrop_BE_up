package luckydrop.demo.draw.sse;

import lombok.extern.slf4j.Slf4j;
import luckydrop.demo.draw.enums.DrawStatus;
import luckydrop.demo.realtime.event.DrawStatusChangedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

    //Redis 에서 이벤트를 받은 뒤, 현재 서버에 연결된 사용자에게만 전송
    public void sendToLocalEmitters(DrawStatusChangedEvent event) {
        emitters.forEach(emitter -> send(emitter, event));
    }

    private void send(SseEmitter emitter, DrawStatusChangedEvent event) {
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
