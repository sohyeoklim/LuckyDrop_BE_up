package luckydrop.demo.realtime.event;

import luckydrop.demo.draw.enums.DrawStatus;

import java.time.LocalDateTime;

public record DrawStatusChangedEvent(
        Long drawId,
        DrawStatus status,
        LocalDateTime occurredAt
) {
}