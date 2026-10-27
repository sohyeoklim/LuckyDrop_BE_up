package luckydrop.demo.mission.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MissionResponse {
    private String code;
    private String title;
    private String description;
    private String type;
    private int rewardTickets;
    private int progress;
    private int target;
    private boolean completed;
    private boolean claimable;
    private boolean adVerificationRequired;
}
