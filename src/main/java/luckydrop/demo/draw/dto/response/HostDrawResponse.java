package luckydrop.demo.draw.dto.response;

import luckydrop.demo.draw.enums.DrawStatus;
import java.time.LocalDateTime;

public record HostDrawResponse(Long drawId, String title, DrawStatus status, LocalDateTime endAt) {
}
