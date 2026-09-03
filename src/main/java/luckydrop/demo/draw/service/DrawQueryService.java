package luckydrop.demo.draw.service;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.draw.bookmark.repository.DrawBookmarkRepository;
import luckydrop.demo.draw.bookmark.service.DrawBookmarkService;
import luckydrop.demo.draw.dto.response.*;
import luckydrop.demo.draw.entity.Draw;
import luckydrop.demo.draw.entity.DrawWinner;
import luckydrop.demo.draw.entity.DrawEntrySummary;
import luckydrop.demo.draw.enums.DrawSort;
import luckydrop.demo.draw.dto.request.FulfillmentUpdateRequest;
import luckydrop.demo.notification.NotificationService;
import luckydrop.demo.draw.enums.DrawStatus;
import luckydrop.demo.draw.enums.DrawTab;
import luckydrop.demo.draw.inventory.entity.InventoryImage;
import luckydrop.demo.draw.inventory.repository.InventoryImageRepository;
import luckydrop.demo.draw.repository.DrawQueryIdRepository;
import luckydrop.demo.draw.repository.DrawRepository;
import luckydrop.demo.draw.repository.DrawWinnerRepository;
import luckydrop.demo.entry.repository.DrawEntrySummaryRepository;
import luckydrop.demo.ticket.entity.TicketWallet;
import luckydrop.demo.ticket.repository.TicketWalletRepository;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.user.repository.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DrawQueryService {

    private final DrawBookmarkService drawBookmarkService;
    private final DrawRepository drawRepository;
    private final UserRepository userRepository;
    private final DrawWinnerRepository drawWinnerRepository;
    private final NotificationService notificationService;

    private final DrawBookmarkRepository drawBookmarkRepository;
    private final InventoryImageRepository inventoryImageRepository;
    private final DrawQueryIdRepository drawQueryIdRepository;
    private final DrawEntrySummaryRepository drawEntrySummaryRepository;
    private final TicketWalletRepository ticketWalletRepository;


    // 컨트롤러용 wrapper (page,size -> Pageable)
    public Page<DrawCardResponse> getDraws(Long userId, DrawTab tab, DrawSort sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return getDraws(userId, tab, sort, pageable);
    }


    public Page<DrawCardResponse> getDraws(Long userId, DrawTab tab, DrawSort sortOrNull, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();

        DrawSort sort = resolveDefaultSort(tab, sortOrNull);
        if (tab == DrawTab.CLOSED) sort = DrawSort.ENDED_DESC; // CLOSED 고정

        Page<Long> idPage = findIdsByPolicy(tab, sort, now, pageable);
        List<Long> drawIds = idPage.getContent();

        if (drawIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
        }

        // draw 엔티티 배치 조회 + 순서 복원
        List<Draw> draws = drawRepository.findAllByIdIn(drawIds);
        Map<Long, Draw> drawMap = draws.stream()
                .collect(Collectors.toMap(Draw::getId, Function.identity()));

        List<Draw> orderedDraws = drawIds.stream()
                .map(drawMap::get)
                .filter(Objects::nonNull)
                .toList();

        // isBookmarked + bookmarkCount (기존 서비스 그대로 사용)
        Set<Long> bookmarkedIds = (userId == null)
                ? Collections.emptySet()
                : drawBookmarkService.findBookmarkedDrawIds(userId, drawIds);

        var bookmarkCountMap = drawBookmarkService.findBookmarkCountMap(drawIds);

        // participantCount 배치
        Map<Long, Long> participantCountMap = drawEntrySummaryRepository.countParticipantsByDrawIds(drawIds).stream()
                .collect(Collectors.toMap(
                        DrawEntrySummaryRepository.DrawCountRow::getDrawId,
                        DrawEntrySummaryRepository.DrawCountRow::getCnt
                ));

        List<DrawCardResponse> content = orderedDraws.stream()
                .map(draw -> {
                    Long drawId = draw.getId();
                    boolean isBookmarked = bookmarkedIds.contains(drawId);
                    long bookmarkCount = bookmarkCountMap.getOrDefault(drawId, 0L);
                    long participantCount = participantCountMap.getOrDefault(drawId, 0L);

                    return DrawCardResponse.from(draw, isBookmarked, bookmarkCount, participantCount);
                })
                .toList();

        return new PageImpl<>(content, pageable, idPage.getTotalElements());
    }


    public DrawDetailResponse getDrawDetail(Long drawId, Long userId) {

        Draw draw = drawRepository.findByIdAndStatusNot(drawId, DrawStatus.CANCEL)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 드로우입니다."));

        boolean isBookmarked = false;
        Integer myTicketBalance = 0;

        if (userId != null) {
            isBookmarked = drawBookmarkService.isBookmarked(userId, drawId);

            myTicketBalance = ticketWalletRepository.findByUserId(userId)
                    .map(TicketWallet::getBalance)
                    .orElse(0);
        }

        long bookmarkCount = drawBookmarkService.getBookmarkCount(drawId);
        long participantCount = drawEntrySummaryRepository.countParticipants(drawId);
        long totalEntryCount = drawEntrySummaryRepository.sumEntryCountByDrawId(drawId);

        Long entryCount = 0L;
        boolean isEntered = false;

        if (userId != null) {
            Optional<DrawEntrySummary> summary =
                    drawEntrySummaryRepository.findByDrawIdAndUserId(drawId, userId);

            if (summary.isPresent()) {
                isEntered = true;
                entryCount = summary.get().getEntryCount();
            }
        }

        User host = userRepository.findById(draw.getUserId())
                .orElseThrow();

        return DrawDetailResponse.from(
                draw,
                host.getNickname(),
                isBookmarked,
                bookmarkCount,
                participantCount,
                totalEntryCount,
                myTicketBalance,
                isEntered,
                entryCount);
    }

    public DrawStatsResponse getStats() {
        long totalDrawCount = drawRepository.countVisibleDraws();
        long activeDrawCount = drawRepository.countByStatuses(
                List.of(DrawStatus.ACTIVE, DrawStatus.DRAWING)
        );
        long totalEntryCount = drawEntrySummaryRepository.sumEntryCountExcludingCanceledDraws();

        return new DrawStatsResponse(
                totalDrawCount,
                activeDrawCount,
                totalEntryCount
        );
    }

    public HotBannerResponse getHotBanner(Long userId) {

        LocalDateTime now = LocalDateTime.now();

        Long drawId = null;
        String reason = "EMPTY";

        // 1순위 인기 진행중
        Page<Long> p1 = drawQueryIdRepository.findHot1PopularOngoingIds(
                DrawStatus.ACTIVE, DrawStatus.DRAWING, now, PageRequest.of(0, 1));

        if (!p1.isEmpty()) {
            drawId = p1.getContent().get(0);
            reason = "POPULAR";
        }

        // 2순위 오픈 예정 북마크
        if (drawId == null) {
            Page<Long> p2 = drawQueryIdRepository.findHot2UpcomingBookmarkIds(
                    DrawStatus.DRAFT, now, PageRequest.of(0, 1));

            if (!p2.isEmpty()) {
                drawId = p2.getContent().get(0);
                reason = "UPCOMING_BOOKMARK";
            }
        }

        // 3순위 최근 시작
        if (drawId == null) {
            Page<Long> p3 = drawQueryIdRepository.findHot3RecentStartedIds(
                    DrawStatus.ACTIVE, DrawStatus.DRAWING, now, PageRequest.of(0, 1));

            if (!p3.isEmpty()) {
                drawId = p3.getContent().get(0);
                reason = "RECENT_STARTED";
            }
        }

        if (drawId == null) {
            return HotBannerResponse.builder()
                    .reason("EMPTY")
                    .build();
        }

        // 실제 Draw 조회
        Draw draw = drawRepository.findById(drawId)
                .orElseThrow(() -> new IllegalArgumentException("draw not found"));

        // 이미지 조회
        List<String> images = inventoryImageRepository
                .findByInventoryIdOrderBySortOrderAsc(draw.getInventory().getId())
                .stream()
                .map(InventoryImage::getImageUrl)
                .toList();

        // 북마크 여부
        boolean isBookmarked = drawBookmarkRepository
                .existsByIdUserIdAndIdDrawId(userId, drawId);

        return HotBannerResponse.builder()
                .drawId(drawId)
                .reason(reason)

                .title(draw.getTitle())
                .productName(draw.getInventory().getName())

                .images(images)
                .isBookmarked(isBookmarked)

                .ticketCostEntry(draw.getTicketCostEntry())
                .startAt(draw.getStartAt())
                .endAt(draw.getEndAt())

                .build();
    }

    @Transactional(readOnly = true)
    public List<HostWinnerInfoResponse> getHostWinnerInfo(Long drawId, Long requesterUserId) {
        Draw draw = drawRepository.findById(drawId)
                .orElseThrow(() -> new IllegalArgumentException("드로우가 존재하지 않습니다. id=" + drawId));

        if (draw.getUserId() == null || !draw.getUserId().equals(requesterUserId)) {
            throw new AccessDeniedException("해당 드로우의 호스트만 당첨자 정보를 조회할 수 있습니다.");
        }

        return drawWinnerRepository.findHostWinnerInfoByDrawId(drawId);
    }

    @Transactional
    public void updateFulfillment(Long drawId, Long winnerId, Long requesterUserId, FulfillmentUpdateRequest request) {
        Draw draw = drawRepository.findById(drawId).orElseThrow(() -> new IllegalArgumentException("draw not found"));
        if (!draw.getUserId().equals(requesterUserId)) throw new AccessDeniedException("host only");
        DrawWinner winner = drawWinnerRepository.findById(winnerId)
                .filter(item -> item.getDrawId().equals(drawId))
                .orElseThrow(() -> new IllegalArgumentException("winner not found"));
        if (winner.getFulfillmentStatus() != luckydrop.demo.draw.enums.FulfillmentStatus.ADDRESS_SUBMITTED
                && winner.getFulfillmentStatus() != luckydrop.demo.draw.enums.FulfillmentStatus.PROCESSING) {
            throw new IllegalStateException("당첨자가 배송 정보를 제출한 뒤에만 처리할 수 있습니다.");
        }
        if (request.status() != luckydrop.demo.draw.enums.FulfillmentStatus.PROCESSING
                && request.status() != luckydrop.demo.draw.enums.FulfillmentStatus.COMPLETED) {
            throw new IllegalArgumentException("배송 처리는 처리 중 또는 처리 완료로만 변경할 수 있습니다.");
        }
        winner.updateFulfillment(request.status(), request.note(), request.deliveryCarrier(), request.trackingNumber());
        notificationService.notifyFulfillmentUpdated(draw, winner);
    }

    @Transactional
    public void submitDeliveryAddress(Long drawId, Long winnerId, Long requesterUserId,
                                      luckydrop.demo.draw.dto.request.DeliveryAddressRequest request) {
        DrawWinner winner = drawWinnerRepository.findById(winnerId)
                .filter(item -> item.getDrawId().equals(drawId) && item.getUserId().equals(requesterUserId))
                .orElseThrow(() -> new AccessDeniedException("당첨자만 배송 정보를 입력할 수 있습니다."));
        winner.submitDeliveryAddress(request.phone(), request.address());
    }

    @Transactional(readOnly = true)
    public java.util.Optional<luckydrop.demo.draw.dto.response.MyDeliveryResponse> getMyDelivery(Long drawId, Long requesterUserId) {
        return drawWinnerRepository.findByDrawIdAndUserId(drawId, requesterUserId)
                .map(luckydrop.demo.draw.dto.response.MyDeliveryResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<HostDrawResponse> getHostDraws(Long userId, Pageable pageable) {
        return drawRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(draw -> new HostDrawResponse(draw.getId(), draw.getTitle(), draw.getStatus(), draw.getEndAt(),
                        draw.getInventory().isShippable(),
                        drawWinnerRepository.countByDrawIdAndFulfillmentStatus(draw.getId(), luckydrop.demo.draw.enums.FulfillmentStatus.ADDRESS_SUBMITTED) > 0))
                ;
    }

    private DrawSort resolveDefaultSort(DrawTab tab, DrawSort sortOrNull) {
        if (sortOrNull != null) {
            return sortOrNull;
        }

        return switch (tab) {
            case ALL -> DrawSort.LATEST;
            case OPEN -> DrawSort.LATEST;
            case UPCOMING -> DrawSort.LATEST;
            case ONGOING -> DrawSort.ENDING_SOON;
            case CLOSED -> DrawSort.ENDED_DESC;
        };
    }

    private Page<Long> findIdsByPolicy(DrawTab tab, DrawSort sort, LocalDateTime now, Pageable pageable) {
        String tabName = tab.name();

        return switch (sort) {
            case LATEST -> drawQueryIdRepository.findIdsByLatest(
                    tabName, now,
                    DrawStatus.DRAFT, DrawStatus.ACTIVE, DrawStatus.DRAWING,
                    DrawStatus.CLOSE, DrawStatus.CANCEL,
                    pageable
            );

            case STARTED_DESC -> drawQueryIdRepository.findIdsByStartedDesc(
                    tabName, now,
                    DrawStatus.DRAFT, DrawStatus.ACTIVE, DrawStatus.DRAWING,
                    DrawStatus.CLOSE, DrawStatus.CANCEL,
                    pageable
            );

            case ENDING_SOON -> drawQueryIdRepository.findIdsByEndingSoon(
                    tabName, now,
                    DrawStatus.DRAFT, DrawStatus.ACTIVE, DrawStatus.DRAWING,
                    DrawStatus.CLOSE, DrawStatus.CANCEL,
                    pageable
            );

            case ENDED_DESC -> drawQueryIdRepository.findIdsByEndedDesc(
                    tabName, now,
                    DrawStatus.DRAFT, DrawStatus.ACTIVE, DrawStatus.DRAWING,
                    DrawStatus.CLOSE, DrawStatus.CANCEL,
                    pageable
            );

            case BOOKMARK -> drawQueryIdRepository.findIdsByBookmark(
                    tabName, now,
                    DrawStatus.DRAFT, DrawStatus.ACTIVE, DrawStatus.DRAWING,
                    DrawStatus.CLOSE, DrawStatus.CANCEL,
                    pageable
            );

            case PARTICIPANT -> drawQueryIdRepository.findIdsByParticipant(
                    tabName, now,
                    DrawStatus.DRAFT, DrawStatus.ACTIVE, DrawStatus.DRAWING,
                    DrawStatus.CLOSE, DrawStatus.CANCEL,
                    pageable
            );
        };
    }
}
