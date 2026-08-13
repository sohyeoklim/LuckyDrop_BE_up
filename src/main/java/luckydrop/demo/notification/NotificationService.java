package luckydrop.demo.notification;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.draw.entity.Draw;
import luckydrop.demo.draw.repository.DrawRepository;
import luckydrop.demo.draw.repository.DrawWinnerRepository;
import luckydrop.demo.entry.repository.DrawEntrySummaryRepository;
import luckydrop.demo.draw.entity.DrawWinner;
import luckydrop.demo.draw.enums.FulfillmentStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSseService notificationSseService;
    private final DrawRepository drawRepository;
    private final DrawEntrySummaryRepository entrySummaryRepository;
    private final DrawWinnerRepository drawWinnerRepository;

    @Transactional
    public void notifyDrawingStarted(Long drawId) {
        Draw draw = getDraw(drawId);
        List<Long> participantIds = participantIds(drawId);
        saveAndPublish(participantIds.stream()
                .map(userId -> Notification.builder()
                        .userId(userId)
                        .type(NotificationType.DRAWING_STARTED)
                        .title("추첨이 시작되었어요")
                        .message("응모한 '" + draw.getTitle() + "'의 추첨이 시작되었어요.")
                        .drawId(drawId)
                        .build())
                .toList());
    }

    @Transactional
    public void notifyDrawFinished(Long drawId) {
        Draw draw = getDraw(drawId);
        Set<Long> winnerIds = drawWinnerRepository.findByDrawId(drawId).stream()
                .map(winner -> winner.getUserId())
                .collect(Collectors.toSet());

        saveAndPublish(participantIds(drawId).stream()
                .map(userId -> winnerIds.contains(userId)
                        ? Notification.builder()
                        .userId(userId)
                        .type(NotificationType.DRAW_WON)
                        .title("축하합니다! 당첨되었어요")
                        .message("'" + draw.getTitle() + "' 추첨이 종료되었고, 당첨자로 선정되었어요.")
                        .drawId(drawId)
                        .build()
                        : Notification.builder()
                        .userId(userId)
                        .type(NotificationType.DRAW_NOT_WON)
                        .title("추첨 결과가 나왔어요")
                        .message("'" + draw.getTitle() + "' 추첨이 종료되었어요. 결과를 확인해보세요.")
                        .drawId(drawId)
                        .build())
                .toList());
    }

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long userId, int size) {
        List<NotificationResponse> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, size))
                .map(NotificationResponse::from)
                .getContent();
        return new NotificationListResponse(notifications, notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("notification not found"));
        notification.markRead();
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 100))
                .forEach(Notification::markRead);
    }

    @Transactional
    public void notifyFulfillmentUpdated(Draw draw, DrawWinner winner) {
        String message = winner.getFulfillmentStatus() == FulfillmentStatus.COMPLETED
                ? "'" + draw.getTitle() + "'의 당첨 처리가 완료되었어요."
                : "'" + draw.getTitle() + "'의 당첨 처리가 진행 중이에요.";
        Notification notification = notificationRepository.save(Notification.builder()
                .userId(winner.getUserId()).type(NotificationType.FULFILLMENT_UPDATED)
                .title("당첨 처리 상태가 변경되었어요").message(message).drawId(draw.getId()).build());
        notificationSseService.publishAfterCommit(notification);
    }

    private Draw getDraw(Long drawId) {
        return drawRepository.findById(drawId)
                .orElseThrow(() -> new IllegalArgumentException("draw not found: " + drawId));
    }

    private List<Long> participantIds(Long drawId) {
        return entrySummaryRepository.findRefundTargets(drawId).stream()
                .map(DrawEntrySummaryRepository.RefundTarget::getUserId)
                .toList();
    }

    private void saveAndPublish(List<Notification> notifications) {
        if (notifications.isEmpty()) return;
        notificationRepository.saveAll(notifications)
                .forEach(notificationSseService::publishAfterCommit);
    }
}
