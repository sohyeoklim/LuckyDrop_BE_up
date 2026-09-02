package luckydrop.demo.draw.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.*;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.common.member.CustomUserPrincipal;
import luckydrop.demo.draw.dto.response.*;
import luckydrop.demo.draw.enums.DrawSort;
import luckydrop.demo.draw.enums.DrawTab;
import luckydrop.demo.draw.service.DrawQueryService;
import luckydrop.demo.draw.service.DrawingService;
import luckydrop.demo.draw.dto.request.FulfillmentUpdateRequest;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Draw Query", description = "드로우 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DrawQueryController {

    private final DrawQueryService drawQueryService;
    private final DrawingService drawingService;

    @Operation(
            summary = "드로우 목록 조회",
            description = "탭(ALL, ONGOING) 과 정렬 기준에 따라 드로우 목록을 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DrawCardResponse.class)))
    })
    @GetMapping("/draws")
    public ResponseEntity<PageResponse<DrawCardResponse>> getDraws(
            @AuthenticationPrincipal CustomUserPrincipal principal,

            @Parameter(description = "탭 (ALL, UPCOMING, ONGOING, CLOSED)", example = "ALL")
            @RequestParam(defaultValue = "ALL") DrawTab tab,

            @Parameter(description = "정렬 기준", example = "LATEST")
            @RequestParam(required = false) DrawSort sort,

            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = (principal == null) ? null : principal.getUser().getId();
        Page<DrawCardResponse> result = drawQueryService.getDraws(userId, tab, sort, page, size);

        return ResponseEntity.ok(
            new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
            )
        );
    }



    @Operation(
            summary = "드로우 상세 조회",
            description = "특정 드로우의 상세 정보를 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DrawDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 드로우")
    })
    @GetMapping("/draws/{id}")
    public ResponseEntity<DrawDetailResponse> getDrawDetail(
            @Parameter(description = "드로우 ID", example = "1")
            @PathVariable("id") Long drawId,

            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        Long userId = (principal == null) ? null : principal.getUser().getId();
        return ResponseEntity.ok(drawQueryService.getDrawDetail(drawId, userId));
    }



    @Operation(
            summary = "핫 배너 조회",
            description = "현재 기준으로 가장 인기 있는 드로우를 조회한다. (참여자 수, 북마크 기준 등 정책 적용)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = HotBannerResponse.class)))
    })
    @GetMapping("/draws/hot-banner")
    public ResponseEntity<HotBannerResponse> getHotBanner(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        Long userId = (principal == null) ? null : principal.getUser().getId();
        return ResponseEntity.ok(drawQueryService.getHotBanner(userId));
    }



    @Operation(
            summary = "당첨자 조회",
            description = "드로우의 당첨자 정보를 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DrawWinnerResponse.class))),
            @ApiResponse(responseCode = "404", description = "당첨 정보 없음")
    })
    @GetMapping("/draws/{drawId}/winners")
    public ResponseEntity<DrawWinnerResponse> getWinner(
            @Parameter(description = "드로우 ID", example = "1")
            @PathVariable Long drawId) {
        return ResponseEntity.ok(drawingService.getWinner(drawId));
    }



    @Operation(
            summary = "호스트용 당첨자 상세 조회",
            description = "드로우 생성자가 당첨자의 상세 정보(주소, 번호 등)를 조회한다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = HostWinnerInfoResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @GetMapping("/host/draws/{drawId}/winners")
    public ResponseEntity<List<HostWinnerInfoResponse>> getHostWinnerInfo(
            @Parameter(description = "드로우 ID", example = "1")
            @PathVariable Long drawId,

            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        List<HostWinnerInfoResponse> response = drawQueryService.getHostWinnerInfo(drawId, principal.getUser().getId());

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/host/draws/{drawId}/winners/{winnerId}/fulfillment")
    public ResponseEntity<Void> updateFulfillment(@PathVariable Long drawId, @PathVariable Long winnerId,
            @AuthenticationPrincipal CustomUserPrincipal principal, @Valid @RequestBody FulfillmentUpdateRequest request) {
        drawQueryService.updateFulfillment(drawId, winnerId, principal.getUser().getId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/draws/{drawId}/winners/{winnerId}/delivery-address")
    public ResponseEntity<Void> submitDeliveryAddress(@PathVariable Long drawId, @PathVariable Long winnerId,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody luckydrop.demo.draw.dto.request.DeliveryAddressRequest request) {
        drawQueryService.submitDeliveryAddress(drawId, winnerId, principal.getUser().getId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/draws/{drawId}/my-delivery")
    public ResponseEntity<luckydrop.demo.draw.dto.response.MyDeliveryResponse> getMyDelivery(
            @PathVariable Long drawId, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return drawQueryService.getMyDelivery(drawId, principal.getUser().getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/host/draws")
    public ResponseEntity<Page<HostDrawResponse>> getHostDraws(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (page < 0 || size < 1) throw new IllegalArgumentException("page must be >= 0 and size must be >= 1");
        return ResponseEntity.ok(drawQueryService.getHostDraws(principal.getUser().getId(),
                org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/draws/stats")
    public ResponseEntity<DrawStatsResponse> getStats() {
        return ResponseEntity.ok(drawQueryService.getStats());
    }
}
