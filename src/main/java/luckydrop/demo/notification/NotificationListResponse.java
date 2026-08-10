package luckydrop.demo.notification;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> content,
        long unreadCount
) {
}
