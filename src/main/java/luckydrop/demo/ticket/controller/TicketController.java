package luckydrop.demo.ticket.controller;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.common.member.CustomUserPrincipal;
import luckydrop.demo.ticket.dto.request.TicketAdjustReqDto;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ticket")
public class TicketController {


    private final TicketService ticketService;

    // 지갑 잔액 조회
    @GetMapping("/wallet/me")
    public ResponseEntity<WalletResDto> getBalance(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        WalletResDto response = ticketService.getBalance(principal.getUser().getId());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    // 내역 조회
    @GetMapping("/ledger/me")
    public ResponseEntity<Page<LedgerItemResDto>> getLedger(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page must be >= 0 and size must be >= 1");
        }
        Page<LedgerItemResDto> response = ticketService.getLedger(
                principal.getUser().getId(), PageRequest.of(page, Math.min(size, 100)));
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
