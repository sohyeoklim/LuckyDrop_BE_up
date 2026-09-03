package luckydrop.demo.entry.service;

import lombok.extern.slf4j.Slf4j;
import luckydrop.demo.draw.enums.DrawStatus;
import luckydrop.demo.entry.dto.request.MyEntryListRequest;
import luckydrop.demo.entry.dto.response.MyEntryResponse;
import luckydrop.demo.entry.dto.response.MyEntryStatsResponse;
import luckydrop.demo.ticket.dto.request.TicketUseReqDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import luckydrop.demo.draw.entity.Draw;
import luckydrop.demo.draw.metrics.repository.DrawMetricsRepository;
import lombok.RequiredArgsConstructor;
import luckydrop.demo.entry.repository.DrawEntrySummaryRepository;
import luckydrop.demo.draw.repository.DrawRepository;
import luckydrop.demo.entry.dto.response.DrawEntryResponse;
import luckydrop.demo.ticket.service.TicketService;
import luckydrop.demo.mission.service.DrawMissionClaimService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrawEntryService {

    private final DrawRepository drawRepository;
    private final DrawEntrySummaryRepository entrySummaryRepository;
    private final TicketService ticketService;
    private final DrawMissionClaimService drawMissionClaimService;
    private final DrawMetricsRepository drawMetricsRepository;

    @Transactional
    public DrawEntryResponse enter(Long drawId, Long userId, int count, String idempotencyKey) {
        log.info("enterDraw: user={}, draw={}, count={}, idempotencyKey={}",
                userId, drawId, count, idempotencyKey);

        Draw draw = drawRepository.findByIdForUpdate(drawId)
                .orElseThrow(() -> new IllegalArgumentException("draw not found: " + drawId));

        validateEnterable(draw, userId, LocalDateTime.now());

        int ticketPerEntry = draw.getTicketCostEntry();
        long totalCostLong = Math.multiplyExact(ticketPerEntry, (long) count);
        if (totalCostLong > Integer.MAX_VALUE) {
            throw new IllegalStateException("ticket cost too large");
        }
        int totalCost = (int) totalCostLong;

        // 1. 티켓 차감
        ticketService.useTickets(TicketUseReqDto.builder()
                .userId(userId)
                .amount(totalCost)
                .reason("DRAW_ENTRY")
                .refType("DRAW")
                .refId(drawId)
                .idempotencyKey(idempotencyKey)
                .build());

        // 2. 응모 횟수 증가
        boolean isNewParticipant = upsertEntrySummary(drawId, userId, count);
        drawMetricsRepository.applyDeltas(drawId, 0, isNewParticipant ? 1 : 0, count);
        drawMissionClaimService.rewardEntryMissions(userId);

        long currentEntryCount = getCurrentEntryCount(drawId, userId);
        long spentTicketsTotal = Math.multiplyExact(currentEntryCount, (long) ticketPerEntry);

        log.info("응모 완료: count={}, tickets={}, total={}", count, totalCost, spentTicketsTotal);

        return DrawEntryResponse.builder()
                .drawId(drawId)
                .userId(userId)
                .spentTicketsAdded(totalCost)
                .spentTicketsTotal(spentTicketsTotal)
                .entryTimesAdded(count)
                .build();
    }

    private boolean upsertEntrySummary(Long drawId, Long userId, int entryCountToAdd) {
        log.info("entry summary upsert: draw={}, user={}, addCount={}",
                drawId, userId, entryCountToAdd);

        int affectedRows = entrySummaryRepository.upsertIncrease(drawId, userId, entryCountToAdd);

        if (affectedRows <= 0) {
            throw new IllegalStateException("failed to upsert draw entry summary");
        }

        // MySQL ON DUPLICATE KEY UPDATE: 새 행 INSERT는 1, 기존 행 UPDATE는 2를 반환한다.
        return affectedRows == 1;
    }

    private long getCurrentEntryCount(Long drawId, Long userId) {
        Long totalCount = entrySummaryRepository.findEntryCount(drawId, userId);
        return totalCount == null ? 0L : totalCount;
    }

    private void validateEnterable(Draw draw, Long userId, LocalDateTime now) {
        if (draw.getUserId().equals(userId)) {
            throw new IllegalStateException("host cannot enter own draw");
        }
        if (draw.getStatus() != DrawStatus.ACTIVE) {
            throw new IllegalStateException("draw not active. status=" + draw.getStatus());
        }
        if (now.isBefore(draw.getStartAt())) {
            throw new IllegalStateException("draw not started yet");
        }
        if (!now.isBefore(draw.getEndAt())) {
            throw new IllegalStateException("draw already ended");
        }
    }

    public Page<MyEntryResponse> getMyEntries(Long userId, MyEntryListRequest req) {
        log.info("getMyEntries - userId: {}, search: {}, status: {}, page: {}/{}",
                userId, req.getSearch(), req.getStatus(), req.getPage(), req.getSize());

        Page<MyEntryResponse> entries = entrySummaryRepository.findMyEntries(
                userId, req.getSearch(), req.getStatus(), req.getFromDate(),
                req.getToDate(), PageRequest.of(req.getPage(), req.getSize()));

        log.info("결과: total={}, size={}", entries.getTotalElements(), entries.getContent().size());
        return entries;
    }

    public MyEntryStatsResponse getMyEntryStats(Long userId) {
        log.info("getMyEntryStats - userId: {}", userId);

        Long total = entrySummaryRepository.countTotalByUserId(userId);
        Long inProgress = entrySummaryRepository.countInProgressByUserId(userId);
        Long won = entrySummaryRepository.countWonByUserId(userId);
        Long tickets = entrySummaryRepository.sumTicketUsedByUserId(userId);

        log.info("RAW: total={}, inProgress={}, won={}, tickets={}", total, inProgress, won, tickets);

        MyEntryStatsResponse stats = new MyEntryStatsResponse();
        stats.setTotal(total);
        stats.setInProgress(inProgress);
        stats.setWon(won);
        stats.setTotalTicketsUsed(tickets);
        return stats;
    }
}
