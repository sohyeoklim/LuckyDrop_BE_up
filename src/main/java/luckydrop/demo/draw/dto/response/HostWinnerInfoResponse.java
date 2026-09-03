package luckydrop.demo.draw.dto.response;

import luckydrop.demo.draw.reward.enums.RewardDeliveryType;

import java.time.LocalDateTime;

public record HostWinnerInfoResponse(
        Long winnerId,
        Long winnerUserId,
        String name,
        String nickname,
        String phone,
        String address,
        luckydrop.demo.draw.enums.FulfillmentStatus fulfillmentStatus,
        String fulfillmentNote,
        String deliveryCarrier,
        String trackingNumber,
        String rewardTitle,
        RewardDeliveryType rewardDeliveryType,
        LocalDateTime rewardDeliveredAt
) {
}
