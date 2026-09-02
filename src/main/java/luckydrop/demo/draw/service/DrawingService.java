package luckydrop.demo.draw.service;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.common.exception.BusinessException;
import luckydrop.demo.draw.dto.response.DrawWinnerResponse;
import luckydrop.demo.draw.entity.Draw;
import luckydrop.demo.draw.entity.DrawEntrySummary;
import luckydrop.demo.draw.entity.DrawWinner;
import luckydrop.demo.draw.enums.DrawStatus;
import luckydrop.demo.draw.repository.DrawRepository;
import luckydrop.demo.draw.repository.DrawWinnerRepository;
import luckydrop.demo.draw.verification.DrawVerification;
import luckydrop.demo.entry.repository.DrawEntrySummaryRepository;
import luckydrop.demo.notification.NotificationService;
import luckydrop.demo.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DrawingService {

    private final DrawRepository drawRepository;
    private final DrawWinnerRepository drawWinnerRepository;
    private final DrawEntrySummaryRepository drawEntrySummaryRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;


    // 특정 드로우 전체 당첨자 조회
    @Transactional(readOnly = true)
    public DrawWinnerResponse getWinner(Long drawId) {

        Draw draw = drawRepository.findById(drawId)
                .orElseThrow(() -> new BusinessException("드로우가 존재하지 않습니다."));

        if (draw.getStatus() != DrawStatus.CLOSE) {
            throw new BusinessException("아직 추첨 결과가 공개되지 않았습니다.");
        }

        List<DrawWinnerResponse.WinnerItem> winners =
                drawWinnerRepository.findWinners(drawId)
                        .stream()
                        .map(w -> DrawWinnerResponse.WinnerItem.builder()
                                .nickname(maskNickname(w.getNickname()))
                                .usedTicketCount(w.getUsedTicketCount())
                                .build()
                        )
                        .toList();

        return DrawWinnerResponse.builder()
                .drawId(drawId)
                .winners(winners)
                .build();
    }

    //추첨 실행 + 당첨자 저장
    @Transactional
    public List<DrawWinner> drawingWinner(Long drawId) {

        // DRAWING이면 CLOSED로 바꾼다"를 원자적으로 실행
        int updated = drawRepository.updateDrawingToClosed(drawId);
        if (updated == 0) {
            throw new BusinessException("추첨을 진행할 수 없습니다. (상태가 DARWING이 아니거나 이미 처리됨)");
        }

        // 상태 선점 후 현재 드로우와, userId 기준으로 정렬된 참여자 스냅샷을 고정한다.
        Draw draw = drawRepository.findByIdForUpdate(drawId)
                .orElseThrow(() -> new BusinessException("드로우가 존재하지 않습니다."));

        if (drawWinnerRepository.existsByDrawId(drawId)) {
            throw new BusinessException("이미 추첨이 완료된 드로우입니다.");
        }

        if (draw.getServerSeed() == null) {
            // 기존 드로우는 생성 시점의 커밋이 없으므로 UI에서 LEGACY로 표시한다.
            DrawVerification.Proof legacyProof = DrawVerification.newProof();
            draw.initializeLegacyDrawingSeed(
                    legacyProof.seed(), legacyProof.hash(), DrawVerification.ALGORITHM_VERSION);
        }

        List<DrawEntrySummary.ParticipantWeight> candidates = drawEntrySummaryRepository.findWeights(drawId);
        LocalDateTime drawnAt = LocalDateTime.now();
        draw.completeVerification(snapshotHash(candidates), drawnAt);

        if (candidates.isEmpty()) {
            notificationService.notifyDrawFinished(drawId);
            return List.of();
        }

        int winnerCount = draw.getWinnerCount();
        if (winnerCount <= 0) {
            throw new BusinessException("당첨자 수가 올바르지 않습니다.");
        }

        int k  = Math.min(winnerCount, candidates.size());

        List<Long> winnerUserIds = pickWeightedWinners(draw, candidates, k);

        List<DrawWinner> winners = new ArrayList<>();
        for (Long userId : winnerUserIds) {
            DrawWinner winner = DrawWinner.builder()
                    .drawId(drawId)
                    .userId(userId)
                    .build();
            winner.initializeDelivery(
                    userRepository.getReferenceById(userId),
                    draw.getInventory().isShippable(),
                    drawnAt
            );
            winners.add(winner);
        }
        drawWinnerRepository.saveAll(winners);

        notificationService.notifyDrawFinished(drawId);
        return winners;
    }

    // 당첨자 추첨 로직
    private List<Long> pickWeightedWinners(Draw draw, List<DrawEntrySummary.ParticipantWeight> candidates, int k) {

        List<Scored> scored = new ArrayList<>(candidates.size());

        for (DrawEntrySummary.ParticipantWeight c : candidates) {
            long w = c.getEntryCount();
            if (w <= 0) continue;

            double u = DrawVerification.unitInterval(draw.getServerSeed(), draw.getId(), c.getUserId());
            double key = -Math.log(u) / (double) w;

            scored.add(new Scored(c.getUserId(), key));
        }

        scored.sort(Comparator.comparingDouble(Scored::key).thenComparing(Scored::userId));

        return scored.stream()
                .limit(k)
                .map(Scored::userId)
                .toList();
    }

    private record Scored(Long userId, double key) {}

    private String snapshotHash(List<DrawEntrySummary.ParticipantWeight> candidates) {
        StringBuilder snapshot = new StringBuilder();
        for (DrawEntrySummary.ParticipantWeight candidate : candidates) {
            snapshot.append(candidate.getUserId())
                    .append(':')
                    .append(candidate.getEntryCount())
                    .append('\n');
        }
        return DrawVerification.sha256Hex(snapshot.toString());
    }

    private String maskNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "";
        }

        int length = nickname.length();

        if (length == 1) {
            return "*";
        }

        if (length == 2) {
            return nickname.charAt(0) + "*";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(nickname.charAt(0));

        for (int i = 1; i < length - 1; i++) {
            sb.append("*");
        }

        sb.append(nickname.charAt(length - 1));
        return sb.toString();
    }
}
