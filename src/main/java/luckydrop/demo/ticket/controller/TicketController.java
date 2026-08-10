package luckydrop.demo.ticket.controller;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.common.member.CustomUserPrincipal;
import luckydrop.demo.ticket.dto.request.TicketAdjustReqDto;
import luckydrop.demo.ticket.dto.request.TicketEarnReqDto;
import luckydrop.demo.ticket.dto.request.TicketUseReqDto;
import luckydrop.demo.ticket.dto.response.LedgerItemResDto;
import luckydrop.demo.ticket.dto.response.TicketTransactionResDto;
import luckydrop.demo.ticket.dto.response.WalletResDto;
import luckydrop.demo.ticket.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ticket")
public class TicketController {


    private final TicketService ticketService;

    // 지갑 잔액 조회
    @GetMapping("/wallet/{userId}")
    public ResponseEntity<WalletResDto> getBalance(@PathVariable Long userId) {
        WalletResDto response = ticketService.getBalance(userId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    // 내역 조회
    @GetMapping("/ledger/{userId}")
    public ResponseEntity<Page<LedgerItemResDto>> getLedger(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page must be >= 0 and size must be >= 1");
        }
        Page<LedgerItemResDto> response = ticketService.getLedger(userId, PageRequest.of(page, Math.min(size, 100)));
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    // 티켓 적립
    @PostMapping("/earn")
    public ResponseEntity<TicketTransactionResDto> earnTickets(
            @RequestBody TicketEarnReqDto request) {
        TicketTransactionResDto response = ticketService.earnTickets(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    // 티켓 차감 - 응모 등
    @PostMapping("/use")
    public ResponseEntity<TicketTransactionResDto> useTickets(
            @RequestBody TicketUseReqDto request) {
        TicketTransactionResDto response = ticketService.useTickets(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    // 관리자용 - 수동 조정 (보상, 보정 등)
    @PostMapping("/adjust")
    public ResponseEntity<TicketTransactionResDto> adjustTickets(
            @RequestBody TicketAdjustReqDto request) {
        TicketTransactionResDto response = ticketService.adjustTickets(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
