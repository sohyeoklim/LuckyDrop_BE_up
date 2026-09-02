package luckydrop.demo.draw.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import luckydrop.demo.draw.entity.Draw;
import luckydrop.demo.draw.enums.DrawStatus;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class DrawDetailResponse {

    private Long hostUserId; //호스트 본인 맞는지 판단용

    private Long drawId;
    private String hostNickname;
    private String title;
    private String description;
    private Integer ticketCostEntry;
    private Integer winnerCount;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private DrawStatus status;

    @JsonProperty("isBookmarked")
    private boolean isBookmarked;
    private long participantCount;
    private long totalEntryCount;
    private long bookmarkCount;

    private String productName;
    private String brand;
    private String productDescription;
    private Integer price;
    private List<String> images;

    private Integer myTicketBalance;
    private boolean endAtChanged;

    @JsonProperty("isEntered")
    private boolean isEntered;
    private Long entryCount;
    private DrawVerificationResponse verification;

    public static DrawDetailResponse from(
            Draw draw,
            String hostNickname,
            boolean isBookmarked,
            long bookmarkCount,
            long participantCount,
            long totalEntryCount,
            Integer myTicketBalance,
            boolean isEntered,
            Long entryCount
    ) {
        return DrawDetailResponse.builder()
                .hostUserId(draw.getUserId())
                .hostNickname(hostNickname)
                .drawId(draw.getId())
                .title(draw.getTitle())
                .description(draw.getDescription())
                .ticketCostEntry(draw.getTicketCostEntry())
                .winnerCount(draw.getWinnerCount())
                .startAt(draw.getStartAt())
                .endAt(draw.getEndAt())
                .status(draw.getStatus())
                .isBookmarked(isBookmarked)
                .bookmarkCount(bookmarkCount)
                .isEntered(isEntered)
                .entryCount(entryCount)
                .verification(DrawVerificationResponse.from(draw))
                .participantCount(participantCount)
                .totalEntryCount(totalEntryCount)
                .myTicketBalance(myTicketBalance)
                .productName(draw.getInventory().getName())
                .brand(draw.getInventory().getBrand())
                .productDescription(draw.getInventory().getDescription())
                .price(draw.getInventory().getRetailPrice())
                .images(
                        draw.getInventory().getImages()
                                .stream()
                                .map(img -> img.getImageUrl())
                                .toList()
                )
                .endAtChanged(draw.isEndAtChanged())
                .build();
    }

}
