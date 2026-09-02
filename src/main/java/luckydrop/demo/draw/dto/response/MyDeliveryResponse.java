package luckydrop.demo.draw.dto.response;

import luckydrop.demo.draw.entity.DrawWinner;
import luckydrop.demo.draw.enums.FulfillmentStatus;

import java.time.LocalDateTime;

public record MyDeliveryResponse(Long winnerId, String phone, String address,
                                 LocalDateTime addressDeadlineAt, FulfillmentStatus fulfillmentStatus,
                                 String trackingNumber) {
    public static MyDeliveryResponse from(DrawWinner winner) {
        return new MyDeliveryResponse(winner.getId(), winner.getDeliveryPhone(), winner.getDeliveryAddress(),
                winner.getAddressDeadlineAt(), winner.getFulfillmentStatus(), winner.getTrackingNumber());
    }
}
