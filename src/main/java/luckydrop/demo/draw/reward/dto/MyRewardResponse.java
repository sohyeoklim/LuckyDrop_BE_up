package luckydrop.demo.draw.reward.dto;

import luckydrop.demo.draw.enums.FulfillmentStatus;
import luckydrop.demo.draw.reward.entity.WinnerReward;
import luckydrop.demo.draw.reward.enums.RewardDeliveryType;

import java.time.LocalDateTime;

public record MyRewardResponse(
        Long winnerId,
        Long rewardId,
        RewardDeliveryType deliveryType,
        String title,
        String content,
        boolean imageAvailable,
        LocalDateTime deliveredAt,
        LocalDateTime confirmedAt,
        FulfillmentStatus fulfillmentStatus
) {
    public static MyRewardResponse from(WinnerReward reward, FulfillmentStatus fulfillmentStatus) {
        return new MyRewardResponse(reward.getWinner().getId(), reward.getId(), reward.getDeliveryType(), reward.getTitle(), reward.getContent(),
                reward.getImageFilename() != null, reward.getDeliveredAt(), reward.getConfirmedAt(), fulfillmentStatus);
    }
}
