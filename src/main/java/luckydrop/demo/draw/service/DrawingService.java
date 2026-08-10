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
import luckydrop.demo.entry.repository.DrawEntrySummaryRepository;
import luckydrop.demo.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DrawingService {

    private final DrawRepository drawRepository;
    private final DrawWinnerRepository drawWinnerRepository;
    private final DrawEntrySummaryRepository drawEntrySummaryRepository;
    private final NotificationService notificationService;


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

        List<DrawEntrySummary.ParticipantWeight> candidates = drawEntrySummaryRepository.findWeights(drawId);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // winnerCount는 Draw에서 읽어야 하나까 draw 조회 1번 필요
        Draw draw = drawRepository.findByIdForUpdate(drawId)
                .orElseThrow(() -> new BusinessException("드로우가 존재하지 않습니다."));

        if (drawWinnerRepository.existsByDrawId(drawId)) {
            throw new BusinessException("이미 추첨이 완료된 드로우입니다.");
        }

        int winnerCount = draw.getWinnerCount();
        if (winnerCount <= 0) {
            throw new BusinessException("당첨자 수가 올바르지 않습니다.");
        }

        int k  = Math.min(winnerCount, candidates.size());

        List<Long> winnerUserIds = pickWeightedWinners(candidates, k);

        List<DrawWinner> winners = new ArrayList<>();
        for (Long userId : winnerUserIds) {
            winners.add(DrawWinner.builder()
                    .drawId(drawId)
                    .userId(userId)
                    .build());
        }
        drawWinnerRepository.saveAll(winners);

        notificationService.notifyDrawFinished(drawId);
        return winners;
    }

    // 당첨자 추첨 로직
    private List<Long> pickWeightedWinners(List<DrawEntrySummary.ParticipantWeight> candidates, int k) {

        List<Scored> scored = new ArrayList<>(candidates.size());

        for (DrawEntrySummary.ParticipantWeight c : candidates) {
            long w = c.getEntryCount();
            if (w <= 0) continue;

            double u = Math.max(Math.random(), 1e-12);
            double key = -Math.log(u) / (double) w;

            scored.add(new Scored(c.getUserId(), key));
        }

        scored.sort(Comparator.comparingDouble(Scored::key));

        return scored.stream()
                .limit(k)
                .map(Scored::userId)
                .toList();
    }

    private record Scored(Long userId, double key) {}

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
