package luckydrop.demo.draw.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import luckydrop.demo.draw.enums.FulfillmentStatus;

public record FulfillmentUpdateRequest(
        @NotNull FulfillmentStatus status,
        String note,
        @NotBlank(message = "택배사를 선택해주세요.") String deliveryCarrier,
        @NotBlank(message = "송장번호를 입력해주세요.") String trackingNumber
) {
}
