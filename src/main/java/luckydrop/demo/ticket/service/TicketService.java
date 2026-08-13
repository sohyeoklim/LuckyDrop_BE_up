package luckydrop.demo.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import luckydrop.demo.ticket.dto.request.TicketAdjustReqDto;
import luckydrop.demo.ticket.dto.request.TicketEarnReqDto;
import luckydrop.demo.ticket.dto.request.TicketUseReqDto;
import luckydrop.demo.ticket.dto.response.LedgerItemResDto;
import luckydrop.demo.ticket.dto.response.TicketTransactionResDto;
import luckydrop.demo.ticket.dto.response.WalletResDto;
import luckydrop.demo.ticket.entity.TicketLedger;
import luckydrop.demo.ticket.entity.TicketWallet;
import luckydrop.demo.ticket.enums.TicketHistoryType;
import luckydrop.demo.ticket.repository.TicketLedgerRepository;
import luckydrop.demo.ticket.repository.TicketWalletRepository;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TicketService {

    private final TicketWalletRepository ticketWalletRepository;
    private final TicketLedgerRepository ticketLedgerRepository;
    private final UserRepository userRepository;

    private TicketWallet getWallet(Long userId) {
        return ticketWalletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("wallet not found for userId: " + userId));
    }

    // 잔액 조회
    public WalletResDto getBalance(Long userId) {
        TicketWallet wallet = getWallet(userId);
        return WalletResDto.builder()
                .userId(userId)
                .balance(wallet.getBalance())
                .build();
    }

    // 내역 조회
    public List<LedgerItemResDto> getLedger(Long userId) {
        List<TicketLedger> ledgers =
                ticketLedgerRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

        return ledgers.stream()
                .map(l -> LedgerItemResDto.builder()
                        .id(l.getId())
                        .type(l.getType())
                        .amount(l.getAmount())
                        .reason(l.getReason())
                        .refType(l.getRefType())
                        .refId(l.getRefId())
                        .idempotencyKey(l.getIdempotencyKey())
                        .createdAt(l.getCreatedAt())
                        .build()
                )
                .toList();
    }

    public Page<LedgerItemResDto> getLedger(Long userId, Pageable pageable) {
        return ticketLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(l -> LedgerItemResDto.builder()
                        .id(l.getId())
                        .type(l.getType())
                        .amount(l.getAmount())
                        .reason(l.getReason())
                        .refType(l.getRefType())
                        .refId(l.getRefId())
                        .idempotencyKey(l.getIdempotencyKey())
                        .createdAt(l.getCreatedAt())
                        .build());
    }

    @Transactional
    public TicketTransactionResDto earnTickets(TicketEarnReqDto request) {
        // 멱등성 체크
        if (ticketLedgerRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        TicketWallet wallet = ticketWalletRepository.findByUserIdWithLock(request.getUserId())
                .orElseGet(() -> {
                    TicketWallet newWallet = TicketWallet.builder()
                            .user(user)
                            .balance(0)
                            .build();
                    return ticketWalletRepository.save(newWallet);
                });

        // 잔액 증가
        int newBalance = wallet.getBalance() + request.getAmount();
        TicketWallet updatedWallet = TicketWallet.builder()
                .id(wallet.getId())
                .user(wallet.getUser())
                .balance(newBalance)
                .build();
        ticketWalletRepository.save(updatedWallet);

        // 내역 기록
        TicketLedger ledger = TicketLedger.builder()
                .user(user)
                .type(TicketHistoryType.EARN)
                .amount(request.getAmount())
                .reason(request.getReason())
                .refType(request.getRefType())
                .refId(request.getRefId())
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        ticketLedgerRepository.save(ledger);

        log.info("티켓 적립 완료 - userId: {}, amount: {}, balance: {}",
                request.getUserId(), request.getAmount(), newBalance);

        return TicketTransactionResDto.builder()
                .success(true)
                .userId(request.getUserId())
                .transactionType("EARN")
                .amount(request.getAmount())
                .previousBalance(wallet.getBalance())
                .currentBalance(newBalance)
                .ledgerId(ledger.getId())
                .build();
    }

    @Transactional
    public TicketTransactionResDto useTickets(TicketUseReqDto request) {
        // 멱등성 체크
        if (ticketLedgerRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        TicketWallet wallet = ticketWalletRepository.findByUserIdWithLock(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));

        int previousBalance = wallet.getBalance();

        int updatedRows = ticketWalletRepository.decreaseBalance(request.getUserId(), request.getAmount());
        if (updatedRows == 0) {
            throw new IllegalStateException("티켓이 부족합니다.");
        }

        TicketWallet updatedWallet = ticketWalletRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
        int currentBalance = updatedWallet.getBalance();

        TicketLedger ledger = TicketLedger.builder()
                .user(user)
                .type(TicketHistoryType.USE)
                .amount(-request.getAmount())
                .reason(request.getReason())
                .refType(request.getRefType())
                .refId(request.getRefId())
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        ticketLedgerRepository.save(ledger);

        return TicketTransactionResDto.builder()
                .success(true)
                .userId(request.getUserId())
                .transactionType("USE")
                .amount(request.getAmount())
                .previousBalance(previousBalance)
                .currentBalance(currentBalance)
                .ledgerId(ledger.getId())
                .build();
    }

    @Transactional
    public TicketTransactionResDto adjustTickets(TicketAdjustReqDto request) {
        // 멱등성 체크
        if (ticketLedgerRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        TicketWallet wallet = ticketWalletRepository.findByUserIdWithLock(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));

        int previousBalance = wallet.getBalance();
        int newBalance = previousBalance + request.getAmount();

        // 음수 방지
        if (newBalance < 0) {
            throw new IllegalStateException("조정 후 잔액이 음수가 될 수 없습니다.");
        }

        TicketWallet updatedWallet = TicketWallet.builder()
                .id(wallet.getId())
                .user(wallet.getUser())
                .balance(newBalance)
                .build();
        ticketWalletRepository.save(updatedWallet);

        // 내역 기록
        TicketLedger ledger = TicketLedger.builder()
                .user(user)
                .type(TicketHistoryType.ADJUST)
                .amount(request.getAmount())
                .reason(request.getReason())
                .refType("ADMIN")
                .refId(request.getAdminId())
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        ticketLedgerRepository.save(ledger);

        log.info("티켓 조정 완료 - userId: {}, amount: {}, balance: {}",
                request.getUserId(), request.getAmount(), newBalance);

        return TicketTransactionResDto.builder()
                .success(true)
                .userId(request.getUserId())
                .transactionType("ADJUST")
                .amount(request.getAmount())
                .previousBalance(previousBalance)
                .currentBalance(newBalance)
                .ledgerId(ledger.getId())
                .build();
    }

    @Transactional
    public void refundTickets(Long userId, Long amount) {
        if (userId == null) throw new IllegalArgumentException("userId is null");
        if (amount == null || amount <= 0) return;

        int delta = Math.toIntExact(amount); //overflow 안전 ??

        TicketWallet wallet = ticketWalletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("티켓 지갑이 없습니다. userId=" + userId));

        wallet.addBalanceByRefund(delta);
    }
}
