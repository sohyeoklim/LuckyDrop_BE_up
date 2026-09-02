package luckydrop.demo.draw.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeliveryAddressRequest(@NotBlank String phone, @NotBlank String address) {
}
