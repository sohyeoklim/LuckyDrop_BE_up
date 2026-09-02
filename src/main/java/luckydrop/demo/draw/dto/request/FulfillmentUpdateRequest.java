package luckydrop.demo.draw.dto.request;

import jakarta.validation.constraints.NotNull;
import luckydrop.demo.draw.enums.FulfillmentStatus;

public record FulfillmentUpdateRequest(
        @NotNull FulfillmentStatus status,
        String note,
        String trackingNumber
) {
}
