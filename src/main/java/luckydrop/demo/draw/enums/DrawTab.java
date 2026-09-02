package luckydrop.demo.draw.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "드로우 조회 탭")
public enum DrawTab {

    @Schema(description = "전체 목록")
    ALL,

    @Schema(description = "대기중 및 진행중")
    OPEN,

    @Schema(description = "대기중 (DRAFT && startAt > now)")
    UPCOMING,

    @Schema(description = "진행중 (ACTIVE, DRAWING)")
    ONGOING,

    @Schema(description = "종료 (CLOSE 또는 종료시간 지난 것)")
    CLOSED
}
