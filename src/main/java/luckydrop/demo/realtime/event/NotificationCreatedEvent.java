package luckydrop.demo.realtime.event;

import luckydrop.demo.notification.NotificationType;

import java.time.LocalDateTime;

public record NotificationCreatedEvent(
        Long notificationId,
        Long userId,
        NotificationType type,
        String title,
        String content,
        LocalDateTime createdAt
) {
}
