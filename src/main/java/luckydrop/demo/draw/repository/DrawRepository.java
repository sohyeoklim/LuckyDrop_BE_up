package luckydrop.demo.draw.repository;

import jakarta.persistence.LockModeType;
import luckydrop.demo.draw.dto.response.AdminDrawSummaryResponse;
import luckydrop.demo.draw.entity.Draw;
import luckydrop.demo.draw.enums.DrawStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DrawRepository extends JpaRepository<Draw, Long> {

    boolean existsByInventoryId(Long inventoryId); // inventory_id UNIQUE 체크용

    Page<Draw> findAllByStatusNot(DrawStatus status, Pageable pageable);
    List<Draw> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<Draw> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Draw> findByIdAndStatusNot(Long id, DrawStatus status);

    // 드로우 목록 집계용
    @Query("""
            select count(d)
            from Draw d
            where d.status <> luckydrop.demo.draw.enums.DrawStatus.CANCEL
            """)
    long countVisibleDraws();

    // 드로우 목록 집계용
    @Query("""
         select count(d)
         from Draw d
         where d.status in :statuses
    """)
    long countByStatuses(@Param("statuses") List<DrawStatus> statuses);


    @Query("""
        select d
        from Draw d
        where d.id in :ids
    """)
    List<Draw> findAllByIdIn(@Param("ids") List<Long> ids);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select d
        from Draw d
        where d.id = :drawId
    """)
    Optional<Draw> findByIdForUpdate(@Param("drawId") Long drawId);

    @Modifying
    @Query("""
        update Draw d
        set d.status = 'ACTIVE'
        where d.status = 'DRAFT'
          and d.startAt <= :now
    """)
    int updateDraftToActive(@Param("now")LocalDateTime now);

    @Query("""
        select d.id from Draw d
        where d.status = 'DRAFT'
          and d.startAt <= :now
    """)
    List<Long> findDraftIdsReadyToActivate(@Param("now") LocalDateTime now);

    @Modifying
    @Query("""
        update Draw d
        set d.status = 'DRAWING'
        where d.status = 'ACTIVE'
            and d.endAt <= :now
    """)
    int updateActiveToDrawing(@Param("now") LocalDateTime now);

    @Query("""
        select d.id from Draw d
        where d.status = 'ACTIVE'
          and d.endAt <= :now
    """)
    List<Long> findActiveIdsReadyForDrawing(@Param("now") LocalDateTime now);

    @Modifying
    @Query("""
        update Draw d
        set d.status = 'CLOSE'
        where d.id = :drawId
            and d.status = 'DRAWING'
    """)
    int updateDrawingToClosed(@Param("drawId") Long drawId);

    @Query("""
    select d from Draw d
    where d.status = :status
        and d.endAt <= :now
""")
    List<Draw> findReadyForDrawing(@Param("status") DrawStatus status,
                                   @Param("now") LocalDateTime now);

    @Query("""
            select new luckydrop.demo.draw.dto.response.AdminDrawSummaryResponse(
                    d.id,
                    d.title,
                    u.nickname,
                    d.status,
                    count(des.userId)
                )
                from Draw d
                join User u on d.userId = u.id
                left join DrawEntrySummary des on d.id = des.drawId
                where d.status <> :cancelStatus
                group by d.id, d.title, u.nickname, d.status
            """)
    List<AdminDrawSummaryResponse> findAdminForceCancelDraws(
            @Param("cancelStatus") DrawStatus status
    );
}
