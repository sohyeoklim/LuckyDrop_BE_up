package luckydrop.demo.entry.repository;

import luckydrop.demo.draw.entity.DrawEntrySummary;
import luckydrop.demo.draw.entity.DrawEntrySummaryId;
import luckydrop.demo.entry.dto.response.MyEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DrawEntrySummaryRepository extends JpaRepository<DrawEntrySummary, DrawEntrySummaryId> {

    interface DrawCountRow {
        Long getDrawId();
        Long getCnt();
    }

    interface RefundTarget {
        Long getUserId();
        long getEntryCount(); // 이미 사용한 티켓 수
    }

    Optional<DrawEntrySummary> findByDrawIdAndUserId(Long drawId, Long userId);

    @Query("""
                select s.userId as userId, s.entryCount as entryCount
                from DrawEntrySummary s
                where s.drawId = :drawId
            """)
    List<RefundTarget> findRefundTargets(@Param("drawId") Long drawId);

    @Query("""
                    select s.drawId as drawId, count(s.userId) as cnt
                    from DrawEntrySummary s
                    where s.drawId in :drawIds
                    group by s.drawId
            """)
    List<DrawCountRow> countParticipantsByDrawIds(@Param("drawIds") List<Long> drawIds);

    @Query("""
                 select count(s)
                 from DrawEntrySummary s
                 where s.drawId = :drawId
            """)
    long countParticipants(@Param("drawId") Long drawId);

    @Query("""
            select coalesce(sum(s.entryCount), 0)
            from DrawEntrySummary s
            where s.drawId = :drawId
            """)
    long sumEntryCountByDrawId(@Param("drawId") Long drawId);

    @Query("""
                    select d.userId as userId, d.entryCount as entryCount
                    from DrawEntrySummary d
                    where d.drawId = :drawId and d.entryCount > 0
                    order by d.userId asc
            """)
    List<DrawEntrySummary.ParticipantWeight> findWeights(@Param("drawId") Long drawId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO draw_entry_summary (draw_id, user_id, entry_count, created_at, updated_at)
            VALUES (:drawId, :userId, :count, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                entry_count = entry_count + VALUES(entry_count),
                updated_at = NOW()
            """, nativeQuery = true)
    int upsertIncrease(@Param("drawId") Long drawId,
                       @Param("userId") Long userId,
                       @Param("count") int count);

    @Query(value = """
            SELECT entry_count
            FROM draw_entry_summary
            WHERE draw_id = :drawId AND user_id = :userId
            """, nativeQuery = true)
    Long findEntryCount(@Param("drawId") Long drawId,
                        @Param("userId") Long userId);


    @Query(value = """
    select coalesce(sum(des.entry_count), 0)
    from draw_entry_summary des
    join draw d on d.id = des.draw_id
    where d.status <> 'CANCEL'
""", nativeQuery = true)
    long sumEntryCountExcludingCanceledDraws();


    // ===== MyEntries 기능 추가 =====
    @Query(value = """
    SELECT 
        CAST(d.id AS SIGNED) as id,
        CAST(des.draw_id AS SIGNED) as drawId,
        d.title as drawTitle,
        COALESCE(ii.image_url) as drawImage,
        DATE_FORMAT(des.updated_at, '%Y-%m-%d %H:%i') as entryDate,
        CAST(COALESCE(SUM(tl.amount), 0) AS SIGNED) as ticketUsed,
        CAST(COALESCE(des.entry_count, 1) AS SIGNED) as entryCount,
        d.status as status,  -- ✅ DrawStatus 그대로 반환
        DATE_FORMAT(d.end_at, '%Y-%m-%d %H:%i') as resultDate,
        CAST(CASE WHEN dw.id IS NOT NULL THEN 1 ELSE 0 END AS SIGNED) as isWinner
    FROM draw_entry_summary des
    JOIN draw d ON des.draw_id = d.id
    LEFT JOIN inventory i ON d.inventory_id = i.id
    LEFT JOIN inventory_image ii ON i.id = ii.inventory_id AND ii.sort_order = 0
    LEFT JOIN draw_winner dw ON d.id = dw.draw_id AND des.user_id = dw.user_id
    LEFT JOIN ticket_ledger tl ON tl.user_id = des.user_id 
        AND tl.type = 'USE'
        AND tl.ref_type = 'DRAW' 
        AND tl.ref_id = des.draw_id
    WHERE des.user_id = :userId
      AND (:search IS NULL OR LOWER(d.title) LIKE LOWER(CONCAT('%', :search, '%')))
      AND (:status IS NULL OR d.status = :status)  -- ✅ DrawStatus 직접 비교
      AND (:fromDate IS NULL OR des.created_at >= :fromDate)
      AND (:toDate IS NULL OR des.created_at <= :toDate)
    GROUP BY d.id, des.draw_id, des.user_id, d.title, ii.image_url, 
             des.updated_at, des.entry_count, d.status, dw.id, d.end_at
    ORDER BY des.updated_at DESC
    """,
            countQuery = """
        SELECT COUNT(DISTINCT des.draw_id)
        FROM draw_entry_summary des
        JOIN draw d ON des.draw_id = d.id
        WHERE des.user_id = :userId
          AND (:search IS NULL OR LOWER(d.title) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR d.status = :status)  -- ✅ DrawStatus 직접 비교
          AND (:fromDate IS NULL OR des.created_at >= :fromDate)
          AND (:toDate IS NULL OR des.created_at <= :toDate)
        """,
            nativeQuery = true)
    Page<MyEntryResponse> findMyEntries(
            @Param("userId") Long userId,
            @Param("search") String search,
            @Param("status") String status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );

    // 통계용 COUNT 쿼리들 - COUNT(*) 사용
    @Query(value = "SELECT COUNT(*) FROM draw_entry_summary WHERE user_id = :userId", nativeQuery = true)
    Long countTotalByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT COUNT(*) 
            FROM draw_entry_summary des 
            JOIN draw d ON des.draw_id = d.id 
            WHERE des.user_id = :userId AND d.status = 'ACTIVE'
            """, nativeQuery = true)
    Long countInProgressByUserId(@Param("userId") Long userId);

    @Query(value = """
        SELECT COUNT(DISTINCT dw.draw_id)
        FROM draw_winner dw
        JOIN draw_entry_summary des ON dw.draw_id = des.draw_id
        WHERE dw.user_id = :userId
        """, nativeQuery = true)
    Long countWonByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT COALESCE(SUM(tl.amount), 0)
            FROM ticket_ledger tl
            JOIN draw_entry_summary des ON tl.ref_id = des.draw_id
            WHERE tl.user_id = :userId 
              AND tl.type = 'USE'
              AND tl.ref_type = 'DRAW'
            """, nativeQuery = true)
    Long sumTicketUsedByUserId(@Param("userId") Long userId);  // TicketHistoryType -> String

    boolean existsByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end); // 오늘 당일 드로우 참여확인
    boolean existsByUserIdAndUpdatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    boolean existsByUserId(Long userId); // 첫 드로우 참여확인
}
