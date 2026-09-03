package luckydrop.demo.entry.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MyEntryResponse {
    private Long id;           // summary.id or entry.id
    private Long drawId;
    private String drawTitle;
    private String drawImage;
    private String entryDate;  // formatted
    private Long ticketUsed;
    private Long entryCount;
    private String status;     // OPEN, LOCKED, DRAWING, DONE, WON, LOST
    private String resultDate; // null 가능
    private Long isWinner;  // DrawWinner 존재 여부
    private Boolean shippable;
}
