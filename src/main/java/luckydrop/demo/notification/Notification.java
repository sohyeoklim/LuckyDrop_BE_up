package luckydrop.demo.notification;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import luckydrop.demo.common.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notification_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_notification_user_read", columnList = "user_id, read_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "draw_id")
    private Long drawId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    public Notification(Long userId, NotificationType type, String title, String message, Long drawId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.drawId = drawId;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }
}
