package luckydrop.demo.draw.reward.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import luckydrop.demo.draw.reward.enums.RewardDeliveryType;
import org.springframework.web.multipart.MultipartFile;

public record RewardDeliveryRequest(
        @NotNull RewardDeliveryType deliveryType,
        @NotBlank String title,
        String content,
        MultipartFile image
) {
}
