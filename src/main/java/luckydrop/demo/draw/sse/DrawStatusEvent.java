package luckydrop.demo.draw.sse;

import luckydrop.demo.draw.enums.DrawStatus;

import java.time.LocalDateTime;

public record DrawStatusEvent(
        Long drawId,
        DrawStatus status,
        LocalDateTime changedAt
) {
}
