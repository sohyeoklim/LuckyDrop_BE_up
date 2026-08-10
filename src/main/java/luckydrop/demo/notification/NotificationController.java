package luckydrop.demo.notification;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.common.member.CustomUserPrincipal;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseService notificationSseService;

    @GetMapping
    public NotificationListResponse getNotifications(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(defaultValue = "20") int size
    ) {
        return notificationService.getNotifications(principal.getUser().getId(), Math.min(Math.max(size, 1), 100));
    }

    @PatchMapping("/{notificationId}/read")
    public void markRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        notificationService.markRead(notificationId, principal.getUser().getId());
    }

    @PatchMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal CustomUserPrincipal principal) {
        notificationService.markAllRead(principal.getUser().getId());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return notificationSseService.subscribe(principal.getUser().getId());
    }
}
