package luckydrop.demo.draw;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import luckydrop.demo.draw.enums.DrawStatus;
import luckydrop.demo.draw.repository.DrawRepository;
import luckydrop.demo.notification.NotificationService;
import luckydrop.demo.realtime.RedisChannels;
import luckydrop.demo.realtime.RedisEventPublisher;
import luckydrop.demo.realtime.event.DrawStatusChangedEvent;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DrawStatusScheduler {

    private final DrawRepository drawRepository;
    private final NotificationService notificationService;
    private final RedisEventPublisher redisEventPublisher;

    @Scheduled(fixedDelay = 10_000)
    @SchedulerLock(
            name = "drawStatusScheduler",
            lockAtMostFor = "PT30S",
            lockAtLeastFor = "PT10S"
    )
    @Transactional
    public void updateDrawStatus() {
        LocalDateTime now = LocalDateTime.now();

        List<Long> draftIds = drawRepository.findDraftIdsReadyToActivate(now);
        List<Long> activeIds = drawRepository.findActiveIdsReadyForDrawing(now);

        int activated = drawRepository.updateDraftToActive(now);
        int drawing = drawRepository.updateActiveToDrawing(now);

        if (activated > 0) {
            draftIds.forEach(drawId ->
                    publishStatusChangedAfterCommit(drawId, DrawStatus.ACTIVE)
            );
        }

        if (drawing > 0) {
            activeIds.forEach(drawId -> {
                publishStatusChangedAfterCommit(drawId, DrawStatus.DRAWING);
                notificationService.notifyDrawingStarted(drawId);
            });
        }

        if (activated > 0 || drawing > 0) {
            log.info("[DrawScheduler] DRAFT -> ACTIVE: {}, ACTIVE -> DRAWING: {}",
                    activated, drawing);
        }
    }

    private void publishStatusChangedAfterCommit(Long drawId, DrawStatus status) {
        DrawStatusChangedEvent event = new DrawStatusChangedEvent(
                drawId,
                status,
                LocalDateTime.now()
        );

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        redisEventPublisher.publish(
                                RedisChannels.DRAW_STATUS,
                                event
                        );
                    }
                }
        );
    }
}