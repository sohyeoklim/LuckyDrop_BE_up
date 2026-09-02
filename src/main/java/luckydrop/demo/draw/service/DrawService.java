package luckydrop.demo.draw.service;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.draw.bookmark.service.DrawBookmarkService;
import luckydrop.demo.draw.dto.request.DrawCreateRequest;
import luckydrop.demo.draw.dto.request.DrawUpdateRequest;
import luckydrop.demo.draw.dto.response.DrawDetailResponse;
import luckydrop.demo.draw.entity.Draw;
import luckydrop.demo.draw.verification.DrawVerification;
import luckydrop.demo.draw.enums.DrawStatus;
import luckydrop.demo.draw.inventory.entity.Inventory;
import luckydrop.demo.draw.inventory.entity.InventoryImage;
import luckydrop.demo.entry.repository.DrawEntrySummaryRepository;
import luckydrop.demo.draw.repository.DrawRepository;
import luckydrop.demo.draw.inventory.repository.InventoryImageRepository;
import luckydrop.demo.draw.inventory.repository.InventoryRepository;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import luckydrop.demo.common.exception.BusinessException;
import luckydrop.demo.common.exception.CustomValidationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DrawService {

    private final DrawRepository drawRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryImageRepository inventoryImageRepository;
    private final DrawEntrySummaryRepository drawEntrySummaryRepository;
    private final DrawBookmarkService drawBookmarkService;
    private final UserRepository userRepository;

    @Transactional
    public DrawDetailResponse updateDraw(Long drawId, Long requesterUserId, DrawUpdateRequest request) {
        Draw draw = drawRepository.findById(drawId)
                .orElseThrow(() ->  new BusinessException("드로우가 존재하지 않습니다."));

        // 1. status == ACTIVE 응모 시작한 드로우는 수정x
        if (draw.getStatus() == DrawStatus.ACTIVE) {
            throw new BusinessException("응모가 시작된 드로우는 수정할 수 없습니다.");
        }

        // 2. now < startAt 현재 시간이 시작 시간을 지났을 경우 수정x
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(draw.getStartAt())) {
            throw new BusinessException("응모 시작 시간이 지난 드로우는 수정할 수 없습니다.");
        }

        // 3. 응모 0명인지 아닌지(entry_summary 기준)
        long totalEntries = drawEntrySummaryRepository.countParticipants(drawId);
        if (totalEntries > 0) {
            throw new BusinessException("응모자가 발생한 드로우는 수정할 수 없습니다.");
        }

        if (!draw.getUserId().equals(requesterUserId)) {
            throw new BusinessException("해당 드로우를 생성한 HOST만 수정할 수 있습니다.");
        }

        // 4. description 수정
        if (request.hasDescription()) {
            draw.changeDescription(request.getDescription());
        }

        // 5. endAt 수정 (최대 1회 + 시간 연장만 가능)
        if (request.hasEndAt()) {
            if (draw.isEndAtChanged()) {
                throw new BusinessException("종료 시간은 최대 1회만 수정할 수 있습니다.");
            }

            LocalDateTime newEndAt = request.getEndAt();

            if (!newEndAt.isAfter(draw.getStartAt())) {
                throw new BusinessException("종료 시간은 시작 시간 이후여야 합니다.");
            }

            if (!newEndAt.isAfter(draw.getEndAt())) {
                throw new BusinessException("종료 시간은 기존 종료 시간보다 뒤로만 연장할 수 있습니다.");
            }

            draw.changeEndAt(newEndAt);
            draw.markEndAtChanged();
        }

        boolean isBookmarked = drawBookmarkService.isBookmarked(requesterUserId, drawId);
        long bookmarkCount = drawBookmarkService.getBookmarkCount(drawId);

        User hostUser = userRepository.findById(draw.getUserId())
                .orElseThrow();

        return DrawDetailResponse.from(draw, hostUser.getNickname(), isBookmarked, bookmarkCount, totalEntries, 0L, 0, false, 0L);
    }


    @Transactional
    public Long createDraw(Long userId, DrawCreateRequest req) {

        validateCreateRequest(req);

        // Inventory 생성/저장 (드로우 생성 시 상품 정보도 같이 저장)
        DrawCreateRequest.Product p = req.getProduct();

        Inventory inventory = Inventory.builder()
                .name(p.getName())
                .brand(p.getBrand())
                .description(p.getDescription())
                .retailPrice(p.getRetailPrice())
                .shippable(p.getShippable())
                .build();

        inventoryRepository.save(inventory);

        // inventoryImages 생성/저장 (sortOrder 없으면 자동부여)
        List<InventoryImage> images = new ArrayList<>();
        int autoOrder = 1;

        for (DrawCreateRequest.Image img : p.getImages()) {
            Integer sortOrder = (img.getSortOrder() != null) ? img.getSortOrder() : autoOrder++;

            InventoryImage image = InventoryImage.builder()
                    .inventory(inventory)
                    .imageUrl(img.getImageUrl())
                    .sortOrder(sortOrder)
                    .build();

            images.add(image);
            inventory.getImages().add(image);
        }
        inventoryImageRepository.saveAll(images);

        //Draw 생성/저장 (inventory 1개당 draw 1개 강제는 DB UNIQUE 가 최종 보루)
        Draw draw = Draw.builder()
                .userId(userId)
                .inventory(inventory)
                .title(req.getTitle())
                .description(req.getDescription())
                .ticketCostEntry(req.getTicketCostEntry())
                .winnerCount(req.getWinnerCount())
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .status(DrawStatus.DRAFT)
                .build();

        DrawVerification.Proof proof = DrawVerification.newProof();
        draw.initializeVerification(
                proof.seed(),
                proof.hash(),
                DrawVerification.ALGORITHM_VERSION,
                LocalDateTime.now()
        );

        //더블 클릭해서 중복 드로우가 될 것을 방지
        try {
            drawRepository.save(draw);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("이미 이 상품으로 생성된 드로우가 있어요");
        }

        return draw.getId();
    }

    private void validateCounts(
            Integer ticketCostEntry,
            Integer winnerCount,
            Map<String, String> errors) {

        if (ticketCostEntry == null) {
            errors.put("ticketCostEntry", "필요 티켓 수를 입력해주세요");
        } else if (ticketCostEntry < 1) {
            errors.put("ticketCostEntry", "필요 티켓 수는 1장 이상이어야 합니다.");
        }

        if (winnerCount == null) {
            errors.put("winnerCount", "당첨자 수를 입력해주세요");
        } else if (winnerCount < 1) {
            errors.put("winnerCount", "당첨자 수는 1명 이상이어야 합니다.");
        }
    }

    private void validateCreateRequest(DrawCreateRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();

        validateDrawTime(req.getStartAt(), req.getEndAt(), errors);
        validateCounts(req.getTicketCostEntry(), req.getWinnerCount(), errors);
        validateImages(req, errors);

        if (!errors.isEmpty()) {
            throw new CustomValidationException(errors);
        }
    }


    private void validateImages(DrawCreateRequest req, Map<String, String> errors) {

        if (req.getProduct() == null) {
            errors.put("product", "상품 정보를 입력해주세요.");
            return;
        }

        if (req.getProduct().getImages() == null || req.getProduct().getImages().isEmpty()) {
            errors.put("images", "상품 이미지는 최소 1개 등록해주세요.");
            return;
        }

        for (DrawCreateRequest.Image img : req.getProduct().getImages()) {
            if (img.getImageUrl() == null || img.getImageUrl().isBlank()) {
                errors.put("images", "이미지 URL은 비어 있을 수 없습니다.");
                return;
            }
        }
    }

    private void validateDrawTime(
            LocalDateTime startAt,
            LocalDateTime endAt,
            Map<String, String> errors
    ) {
        LocalDateTime now = LocalDateTime.now();

        if (startAt == null) {
            errors.put("startAt", "응모 시작 시간을 입력해주세요.");
        } else if (!startAt.isAfter(now)) {
            errors.put("startAt", "응모 시작 시간은 현재 이후여야 합니다.");
        }

        if (endAt == null) {
            errors.put("endAt", "응모 종료 시간을 입력해주세요.");
        } else if (startAt != null && !endAt.isAfter(startAt)) {
            errors.put("endAt", "응모 종료 시간은 시작 시간보다 이후여야 합니다.");
        }
    }



}
