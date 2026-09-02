package luckydrop.demo.draw.dto.response;

public record HostWinnerInfoResponse(
        Long winnerId,
        Long winnerUserId,
        String name,
        String nickname,
        String phone,
        String address,
        luckydrop.demo.draw.enums.FulfillmentStatus fulfillmentStatus,
        String fulfillmentNote,
        String trackingNumber
) {
}
