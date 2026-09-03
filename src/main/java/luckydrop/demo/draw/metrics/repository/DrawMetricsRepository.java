package luckydrop.demo.draw.metrics.repository;

import luckydrop.demo.draw.metrics.entity.DrawMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DrawMetricsRepository extends JpaRepository<DrawMetrics, Long> {

    /**
     * 드로우별 카운터를 하나의 SQL 문으로 생성 또는 증감한다.
     * 동일 draw에 대한 동시 요청도 DB가 행 잠금 아래에서 직렬화하므로 증가분이 유실되지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO draw_metrics (
                draw_id, bookmark_count, participant_count, entry_count, created_at, updated_at
            ) VALUES (
                :drawId, :bookmarkDelta, :participantDelta, :entryDelta, NOW(6), NOW(6)
            )
            ON DUPLICATE KEY UPDATE
                bookmark_count = bookmark_count + :bookmarkDelta,
                participant_count = participant_count + :participantDelta,
                entry_count = entry_count + :entryDelta,
                updated_at = NOW(6)
            """, nativeQuery = true)
    int applyDeltas(@Param("drawId") Long drawId,
                    @Param("bookmarkDelta") long bookmarkDelta,
                    @Param("participantDelta") long participantDelta,
                    @Param("entryDelta") long entryDelta);
}
