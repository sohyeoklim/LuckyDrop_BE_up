package luckydrop.demo.draw.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class DrawCreateRequest {

    //드로우 정보
    @NotBlank(message = "제목을 입력해주세요.")
    private final String title;

    @NotBlank(message = "설명을 입력해주세요.")
    private final String description;

    @NotNull(message = "응모 비용을 입력해주세요.")
    @Min(value = 1, message = "응모 비용은 1 이상이어야 합니다.")
    private final Integer ticketCostEntry;

    @NotNull(message = "당첨자 수를 입력해주세요.")
    @Min(value = 1, message = "당첨자 수는 1명 이상이어야 합니다.")
    private final Integer winnerCount;

    @NotNull(message = "응모 시작 시간을 입력해주세요.")
    @FutureOrPresent(message = "응모 시작 시간은 현재 이후여야 합니다.")
    private final LocalDateTime startAt;

    @NotNull(message = "응모 종료 시간을 입력해주세요.")
    private final LocalDateTime endAt;

    //상품 정보
    @Valid
    @NotNull(message = "상품 정보를 입력해주세요.")
    private final Product product;

    @Getter
    @Builder
    public static class Product {

        @NotBlank(message = "상품명을 입력해주세요.")
        private final String name;

        @NotBlank(message = "브랜드명을 입력해주세요.")
        private final String brand;

        @NotBlank(message = "상품 설명을 입력해주세요.")
        private final String description;

        @NotNull(message = "상품 가격을 입력해주세요.")
        @Min(value = 0, message = "상품 가격은 0 이상이어야 합니다.")
        private final Integer retailPrice;

        @NotNull(message = "배송 가능 여부를 입력해주세요.")
        private final Boolean shippable;

        @NotNull(message = "이미지를 1장 이상 등록해주세요.")
        @Size(min = 1, message = "이미지를 1장 이상 등록해주세요.")
        @Valid
        private final List<Image> images;

    }

    @Getter
    @Builder
    public static class Image{

        @NotBlank(message = "이미지 URL은 비어 있을 수 없습니다.")
        private final String imageUrl;

        //프론트가 안보내면 서버에서 index 이미지로 자동 부여 가능
        private final Integer sortOrder;
    }

    @AssertTrue(message = "endAt은 startAt 이후여야 합니다.")
    public boolean isEndAfterStart() {
        if (startAt == null || endAt == null) return true;
        return endAt.isAfter(startAt);
    }
}
