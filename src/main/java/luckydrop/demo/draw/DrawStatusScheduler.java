package luckydrop.demo.draw;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import luckydrop.demo.draw.repository.DrawRepository;
import luckydrop.demo.draw.enums.DrawStatus;
import luckydrop.demo.draw.sse.DrawSseService;
import luckydrop.demo.notification.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DrawStatusScheduler {

    private final DrawRepository drawRepository;
    private final DrawSseService drawSseService;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void updateDrawStatus()
    {
        LocalDateTime now = LocalDateTime.now();

        List<Long> draftIds = drawRepository.findDraftIdsReadyToActivate(now);
        List<Long> activeIds = drawRepository.findActiveIdsReadyForDrawing(now);

        int activated = drawRepository.updateDraftToActive(now);
        int drawing = drawRepository.updateActiveToDrawing(now);

        if (activated > 0) {
            draftIds.forEach(drawId -> drawSseService.publishStatusChanged(drawId, DrawStatus.ACTIVE));
        }
        if (drawing > 0) {
            activeIds.forEach(drawId -> {
                drawSseService.publishStatusChanged(drawId, DrawStatus.DRAWING);
                notificationService.notifyDrawingStarted(drawId);
            });
        }

        if (activated > 0 || drawing > 0) {
            log.info("[DrawScheduler] DRAFT -> ACTIVE: {}, ACTIVE -> DRAWING: {}", activated, drawing);
        }

        log.info("[DrawScheduler tick] now={}", now);
        log.info("[DrawScheduler result] DRAFT->ACTIVE={}, ACTIVE->DRAWING={}", activated, drawing);

    }
}
