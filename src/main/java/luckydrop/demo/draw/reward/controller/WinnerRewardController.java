package luckydrop.demo.draw.reward.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import luckydrop.demo.common.member.CustomUserPrincipal;
import luckydrop.demo.draw.reward.dto.MyRewardResponse;
import luckydrop.demo.draw.reward.dto.RewardDeliveryRequest;
import luckydrop.demo.draw.reward.service.WinnerRewardService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class WinnerRewardController {

    private final WinnerRewardService winnerRewardService;

    @PostMapping(value = "/host/draws/{drawId}/winners/{winnerId}/reward", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> deliver(
            @PathVariable Long drawId,
            @PathVariable Long winnerId,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @ModelAttribute RewardDeliveryRequest request) {
        winnerRewardService.deliver(drawId, winnerId, principal.getUser().getId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/draws/{drawId}/my-reward")
    public ResponseEntity<MyRewardResponse> getMyReward(
            @PathVariable Long drawId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return winnerRewardService.getMyReward(drawId, principal.getUser().getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/draws/{drawId}/winners/{winnerId}/reward/confirm")
    public ResponseEntity<Void> confirm(
            @PathVariable Long drawId,
            @PathVariable Long winnerId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        winnerRewardService.confirm(drawId, winnerId, principal.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rewards/{rewardId}/image")
    public ResponseEntity<org.springframework.core.io.Resource> getImage(
            @PathVariable Long rewardId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        WinnerRewardService.ImageResource image = winnerRewardService.getImage(rewardId, principal.getUser().getId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.resource());
    }
}
