package luckydrop.demo.draw.reward.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import luckydrop.demo.draw.entity.Draw;
import luckydrop.demo.draw.entity.DrawWinner;
import luckydrop.demo.draw.enums.FulfillmentStatus;
import luckydrop.demo.draw.repository.DrawRepository;
import luckydrop.demo.draw.repository.DrawWinnerRepository;
import luckydrop.demo.draw.reward.dto.MyRewardResponse;
import luckydrop.demo.draw.reward.dto.RewardDeliveryRequest;
import luckydrop.demo.draw.reward.entity.WinnerReward;
import luckydrop.demo.draw.reward.enums.RewardDeliveryType;
import luckydrop.demo.draw.reward.repository.WinnerRewardRepository;
import luckydrop.demo.notification.NotificationService;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WinnerRewardService {

    private final DrawRepository drawRepository;
    private final DrawWinnerRepository drawWinnerRepository;
    private final WinnerRewardRepository winnerRewardRepository;
    private final PrivateRewardImageService privateRewardImageService;
    private final NotificationService notificationService;

    @Transactional
    public void deliver(Long drawId, Long winnerId, Long hostUserId, RewardDeliveryRequest request) {
        Draw draw = getHostDraw(drawId, hostUserId);
        assertNonShippable(draw);
        DrawWinner winner = getWinner(drawId, winnerId);
        if (winnerRewardRepository.existsByWinner_Id(winnerId)) {
            throw new IllegalStateException("보상은 한 번만 전달할 수 있습니다.");
        }

        WinnerReward reward;
        if (request.deliveryType() == RewardDeliveryType.IMAGE) {
            PrivateRewardImageService.StoredImage image = privateRewardImageService.store(request.image());
            reward = WinnerReward.createImage(winner, hostUserId, request.title(), image.filename(), image.contentType());
        } else {
            if (request.content() == null || request.content().isBlank()) {
                throw new IllegalArgumentException("보상 내용을 입력해주세요.");
            }
            reward = WinnerReward.createText(winner, hostUserId, request.deliveryType(), request.title(), request.content().trim());
        }

        winnerRewardRepository.save(reward);
        winner.markRewardDelivered();
        log.info("Reward delivery audit: rewardId={}, drawId={}, winnerId={}, deliveredByUserId={}, deliveryType={}, deliveredAt={}",
                reward.getId(), drawId, winnerId, hostUserId, reward.getDeliveryType(), reward.getDeliveredAt());
        notificationService.notifyRewardDelivered(draw, winner);
    }

    @Transactional(readOnly = true)
    public Optional<MyRewardResponse> getMyReward(Long drawId, Long requesterUserId) {
        Draw draw = drawRepository.findById(drawId).orElseThrow(() -> new IllegalArgumentException("드로우를 찾을 수 없습니다."));
        assertNonShippable(draw);
        return drawWinnerRepository.findByDrawIdAndUserId(drawId, requesterUserId)
                .flatMap(winner -> winnerRewardRepository.findByWinner_Id(winner.getId())
                        .map(reward -> MyRewardResponse.from(reward, winner.getFulfillmentStatus())));
    }

    @Transactional
    public void confirm(Long drawId, Long winnerId, Long requesterUserId) {
        DrawWinner winner = getWinnerForUser(drawId, requesterUserId);
        if (!winner.getId().equals(winnerId)) throw new AccessDeniedException("당첨자만 보상 수령을 완료할 수 있습니다.");
        Draw draw = drawRepository.findById(drawId).orElseThrow(() -> new IllegalArgumentException("드로우를 찾을 수 없습니다."));
        assertNonShippable(draw);
        WinnerReward reward = winnerRewardRepository.findByWinner_Id(winnerId)
                .orElseThrow(() -> new IllegalStateException("전달된 보상이 없습니다."));
        winner.completeReward();
        reward.confirm();
    }

    @Transactional(readOnly = true)
    public ImageResource getImage(Long rewardId, Long requesterUserId) {
        WinnerReward reward = winnerRewardRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalArgumentException("보상을 찾을 수 없습니다."));
        if (reward.getDeliveryType() != RewardDeliveryType.IMAGE) {
            throw new IllegalArgumentException("이미지형 보상이 아닙니다.");
        }
        DrawWinner winner = reward.getWinner();
        Draw draw = drawRepository.findById(winner.getDrawId())
                .orElseThrow(() -> new IllegalArgumentException("드로우를 찾을 수 없습니다."));
        if (!winner.getUserId().equals(requesterUserId) && !draw.getUserId().equals(requesterUserId)) {
            throw new AccessDeniedException("해당 보상 이미지를 볼 권한이 없습니다.");
        }
        return new ImageResource(privateRewardImageService.load(reward.getImageFilename()), reward.getImageContentType());
    }

    private Draw getHostDraw(Long drawId, Long hostUserId) {
        Draw draw = drawRepository.findById(drawId).orElseThrow(() -> new IllegalArgumentException("드로우를 찾을 수 없습니다."));
        if (!draw.getUserId().equals(hostUserId)) throw new AccessDeniedException("드로우 개설자만 보상을 전달할 수 있습니다.");
        return draw;
    }

    private DrawWinner getWinner(Long drawId, Long winnerId) {
        return drawWinnerRepository.findById(winnerId)
                .filter(winner -> winner.getDrawId().equals(drawId))
                .orElseThrow(() -> new IllegalArgumentException("당첨 정보를 찾을 수 없습니다."));
    }

    private DrawWinner getWinnerForUser(Long drawId, Long userId) {
        return drawWinnerRepository.findByDrawIdAndUserId(drawId, userId)
                .orElseThrow(() -> new AccessDeniedException("당첨자만 보상을 조회할 수 있습니다."));
    }

    private void assertNonShippable(Draw draw) {
        if (draw.getInventory().isShippable()) throw new IllegalStateException("배송 상품에는 보상 전달 기능을 사용할 수 없습니다.");
    }

    public record ImageResource(Resource resource, String contentType) {
    }
}
