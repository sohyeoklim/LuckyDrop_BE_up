package luckydrop.demo.draw.bookmark.service;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.draw.bookmark.entity.DrawBookmark;
import luckydrop.demo.draw.bookmark.repository.DrawBookmarkCountView;
import luckydrop.demo.draw.bookmark.repository.DrawBookmarkRepository;
import luckydrop.demo.draw.entity.Draw;
import luckydrop.demo.draw.repository.DrawRepository;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.user.repository.UserRepository;
import luckydrop.demo.mission.service.DrawMissionClaimService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class DrawBookmarkService {

    private final DrawBookmarkRepository drawBookmarkRepository;
    private final DrawRepository drawRepository;
    private final UserRepository userRepository;
    private final DrawMissionClaimService drawMissionClaimService;

    //찜하기
    public void bookmark(Long userId, Long drawId) {
        if (!drawRepository.existsById(drawId)) {
            throw new IllegalArgumentException("존재하지 않는 드로우입니다.");
        }

        //멱등성
        if (drawBookmarkRepository.existsByIdUserIdAndIdDrawId(userId, drawId)) {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다."));

        Draw draw = drawRepository.findById(drawId)
                .orElseThrow(() -> new IllegalArgumentException("드로우가 존재하지 않습니다."));

        DrawBookmark bookmark = DrawBookmark.of(user, draw);
        drawBookmarkRepository.save(bookmark);
        drawMissionClaimService.rewardFirstBookmark(userId);
    }

    //찜 취소
    public void unBookmark(Long userId, Long drawId) {
        drawBookmarkRepository.deleteByIdUserIdAndIdDrawId(userId, drawId);
    }

    //상세 조회시 단건 체크
    @Transactional(readOnly = true)
    public boolean isBookmarked(Long userId, Long drawId) {
        return drawBookmarkRepository.existsByIdUserIdAndIdDrawId(userId, drawId);
    }

    //드로우 목록 조회 N + 1 방지용
    public Set<Long> findBookmarkedDrawIds(Long userId, List<Long> drawIds) {

        if (userId == null || drawIds == null || drawIds.isEmpty()) {
            return Collections.emptySet();
        }

        List<Long> bookmarkedIds =
                drawBookmarkRepository.findBookmarkedDrawIds(userId, drawIds);

        return new HashSet<>(bookmarkedIds);
    }

    public Map<Long, Long> findBookmarkCountMap(List<Long> drawIds) {
        if (drawIds.isEmpty()) return Map.of();

        List<DrawBookmarkCountView> rows = drawBookmarkRepository.countByDrawIds(drawIds);
        Map<Long, Long> map = new HashMap<>();
        for (DrawBookmarkCountView r : rows) {
            map.put(r.getDrawId(), r.getCnt() == null ? 0L : r.getCnt());
        }

        return map;
    }

    // 상세 단건 count
    public long getBookmarkCount(Long drawId) {
        return drawBookmarkRepository.countByIdDrawId(drawId);
    }
}
