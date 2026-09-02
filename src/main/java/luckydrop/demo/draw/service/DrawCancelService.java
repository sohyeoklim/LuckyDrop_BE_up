package luckydrop.demo.draw.service;

import luckydrop.demo.draw.dto.response.AdminDrawSummaryResponse;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import luckydrop.demo.draw.entity.Draw;
import luckydrop.demo.draw.entity.DrawRefundJob;
import luckydrop.demo.draw.enums.DrawStatus;
import luckydrop.demo.draw.repository.DrawRefundJobRepository;
import luckydrop.demo.draw.repository.DrawRepository;
import luckydrop.demo.entry.repository.DrawEntrySummaryRepository;
import luckydrop.demo.ticket.entity.TicketLedger;
import luckydrop.demo.ticket.entity.TicketLedgerFactory;
import luckydrop.demo.ticket.repository.TicketLedgerRepository;
import luckydrop.demo.ticket.service.TicketService;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DrawCancelService {

    private final DrawRepository drawRepository;
    private final DrawRefundJobRepository drawRefundJobRepository;
    private final DrawEntrySummaryRepository drawEntrySummaryRepository;

    private final TicketService ticketService;
    private final TicketLedgerRepository ticketLedgerRepository;
    private final UserRepository userRepository;

    @Transactional
    public void cancelByHost(Long drawId, Long requesterUserId) {

        Draw draw = drawRepository.findByIdForUpdate(drawId)
                .orElseThrow(() -> new IllegalArgumentException("드로우가 존재하지 않습니다. id=" + drawId));

        // host 만 삭제 가능
        if (draw.getUserId() == null || !draw.getUserId().equals(requesterUserId)) {
            throw new AccessDeniedException("host만 드로우를 삭제할 수 있습니다.");
        }

        long participantCount = drawEntrySummaryRepository.countParticipants(drawId);

        boolean canDelete =
                draw.getStatus() == DrawStatus.DRAFT
                || (draw.getStatus() == DrawStatus.ACTIVE && participantCount == 0);

        if (!canDelete) {
            throw new IllegalArgumentException("이미 응모자가 발생한 드로우거나 시작 전의 드로우가 아닙니다.");
        }

        draw.cancel();
    }

    @Transactional
    public void cancelByAdmin(Long drawId, String reasonCode, String reasonDetail) {
        Draw draw = drawRepository.findByIdForUpdate(drawId)
                .orElseThrow(() -> new IllegalArgumentException("드로우가 존재하지 않습니다."));

        if (draw.getStatus() == DrawStatus.CANCEL) {
            throw new IllegalArgumentException("이미 취소된 드로우입니다.");
        }
        if (draw.getStatus() == DrawStatus.CLOSE) {
            throw new IllegalArgumentException("종료된 드로우는 취소할 수 없습니다.");
        }
        if (draw.getStatus() == DrawStatus.DRAWING) {
            throw new IllegalArgumentException("추첨 중인 드로우는 취소할 수 없습니다.");
        }

        draw.cancel();

        DrawRefundJob job = drawRefundJobRepository.findByDrawIdForUpdate(drawId)
                .orElseGet(() -> drawRefundJobRepository.save(
                        DrawRefundJob.create(draw, reasonCode, reasonDetail)
                ));

        if (job.isRefunded()) {
            return;
        }

        var targets = drawEntrySummaryRepository.findRefundTargets(drawId);
        LocalDateTime now = LocalDateTime.now();

        for (var t : targets) {
            Long userId = t.getUserId();
            long entryCount = t.getEntryCount();

            if (entryCount <= 0) continue;

            long refundAmount = Math.multiplyExact(
                    entryCount,
                    draw.getTicketCostEntry().longValue()
            );

            ticketService.refundTickets(userId, refundAmount);

            User userRef = userRepository.getReferenceById(userId);
            int amt = Math.toIntExact(refundAmount);
            TicketLedger ledger = TicketLedgerFactory.refundByDrawForceCancel(userRef, drawId, amt, reasonCode);
            ticketLedgerRepository.save(ledger);
        }

        job.markRefunded(now);
    }

    @Transactional(readOnly = true)
    public List<AdminDrawSummaryResponse> getForceCancelDraws() {
        return drawRepository.findAdminForceCancelDraws(DrawStatus.CANCEL);
    }
}
