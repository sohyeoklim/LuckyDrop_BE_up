package luckydrop.demo.draw.repository;

import luckydrop.demo.draw.entity.Draw;
import luckydrop.demo.draw.enums.DrawStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface DrawQueryIdRepository extends JpaRepository<Draw, Long> {

    // 1) LATEST
    @Query("""
        select d.id
        from Draw d
        where (:tab = 'ALL' and d.status <> :cancel)
           or (:tab = 'OPEN' and d.status in (:draft, :active, :drawing))
           or (:tab = 'UPCOMING' and d.status = :draft and d.startAt > :now)
           or (:tab = 'ONGOING' and (d.status = :active or d.status = :drawing) and d.startAt <= :now and d.endAt > :now)
           or (:tab = 'CLOSED' and (d.status = :close or d.endAt <= :now))
        order by d.createdAt desc
    """)
    Page<Long> findIdsByLatest(
            @Param("tab") String tab,
            @Param("now") LocalDateTime now,
            @Param("draft") DrawStatus draft,
            @Param("active") DrawStatus active,
            @Param("drawing") DrawStatus drawing,
            @Param("close") DrawStatus close,
            @Param("cancel") DrawStatus cancel,
            Pageable pageable
    );

    // 2) STARTED_DESC
    @Query("""
        select d.id
        from Draw d
        where (:tab = 'ALL' and d.status <> :cancel)
           or (:tab = 'OPEN' and d.status in (:draft, :active, :drawing))
           or (:tab = 'UPCOMING' and d.status = :draft and d.startAt > :now)
           or (:tab = 'ONGOING' and (d.status = :active or d.status = :drawing) and d.startAt <= :now and d.endAt > :now)
           or (:tab = 'CLOSED' and (d.status = :close or d.endAt <= :now))
        order by d.startAt desc, d.createdAt desc
    """)
    Page<Long> findIdsByStartedDesc(
            @Param("tab") String tab,
            @Param("now") LocalDateTime now,
            @Param("draft") DrawStatus draft,
            @Param("active") DrawStatus active,
            @Param("drawing") DrawStatus drawing,
            @Param("close") DrawStatus close,
            @Param("cancel") DrawStatus cancel,
            Pageable pageable
    );

    // 3) ENDING_SOON
    @Query("""
        select d.id
        from Draw d
        where (:tab = 'ALL' and d.status <> :cancel)
           or (:tab = 'OPEN' and d.status in (:draft, :active, :drawing))
           or (:tab = 'UPCOMING' and d.status = :draft and d.startAt > :now)
           or (:tab = 'ONGOING' and (d.status = :active or d.status = :drawing) and d.startAt <= :now and d.endAt > :now)
           or (:tab = 'CLOSED' and (d.status = :close or d.endAt <= :now))
        order by d.endAt asc, d.startAt desc
    """)
    Page<Long> findIdsByEndingSoon(
            @Param("tab") String tab,
            @Param("now") LocalDateTime now,
            @Param("draft") DrawStatus draft,
            @Param("active") DrawStatus active,
            @Param("drawing") DrawStatus drawing,
            @Param("close") DrawStatus close,
            @Param("cancel") DrawStatus cancel,
            Pageable pageable
    );

    // 4) ENDED_DESC
    @Query("""
        select d.id
        from Draw d
        where (:tab = 'ALL' and d.status <> :cancel)
           or (:tab = 'OPEN' and d.status in (:draft, :active, :drawing))
           or (:tab = 'UPCOMING' and d.status = :draft and d.startAt > :now)
           or (:tab = 'ONGOING' and (d.status = :active or d.status = :drawing) and d.startAt <= :now and d.endAt > :now)
           or (:tab = 'CLOSED' and (d.status = :close or d.endAt <= :now))
        order by d.endAt desc, d.createdAt desc
    """)
    Page<Long> findIdsByEndedDesc(
            @Param("tab") String tab,
            @Param("now") LocalDateTime now,
            @Param("draft") DrawStatus draft,
            @Param("active") DrawStatus active,
            @Param("drawing") DrawStatus drawing,
            @Param("close") DrawStatus close,
            @Param("cancel") DrawStatus cancel,
            Pageable pageable
    );

    // 5) BOOKMARK
    @Query("""
        select d.id
        from Draw d
        where (:tab = 'ALL' and d.status <> :cancel)
           or (:tab = 'OPEN' and d.status in (:draft, :active, :drawing))
           or (:tab = 'UPCOMING' and d.status = :draft and d.startAt > :now)
           or (:tab = 'ONGOING' and (d.status = :active or d.status = :drawing) and d.startAt <= :now and d.endAt > :now)
           or (:tab = 'CLOSED' and (d.status = :close or d.endAt <= :now))
        order by
            (select count(b) from luckydrop.demo.draw.bookmark.entity.DrawBookmark b where b.id.drawId = d.id) desc,
            d.createdAt desc
    """)
    Page<Long> findIdsByBookmark(
            @Param("tab") String tab,
            @Param("now") LocalDateTime now,
            @Param("draft") DrawStatus draft,
            @Param("active") DrawStatus active,
            @Param("drawing") DrawStatus drawing,
            @Param("close") DrawStatus close,
            @Param("cancel") DrawStatus cancel,
            Pageable pageable
    );

    // 6) PARTICIPANT
    @Query("""
        select d.id
        from Draw d
        where (:tab = 'ALL' and d.status <> :cancel)
           or (:tab = 'OPEN' and d.status in (:draft, :active, :drawing))
           or (:tab = 'UPCOMING' and d.status = :draft and d.startAt > :now)
           or (:tab = 'ONGOING' and (d.status = :active or d.status = :drawing) and d.startAt <= :now and d.endAt > :now)
           or (:tab = 'CLOSED' and (d.status = :close or d.endAt <= :now))
        order by
            (select count(s) from DrawEntrySummary s where s.drawId = d.id) desc,
            d.createdAt desc
    """)
    Page<Long> findIdsByParticipant(
            @Param("tab") String tab,
            @Param("now") LocalDateTime now,
            @Param("draft") DrawStatus draft,
            @Param("active") DrawStatus active,
            @Param("drawing") DrawStatus drawing,
            @Param("close") DrawStatus close,
            @Param("cancel") DrawStatus cancel,
            Pageable pageable
    );

    // 1순위: participantCount desc, tie endAt asc -> createdAt desc (ONGOING 대상으로 의미있음)
    @Query("""
        select d.id
        from Draw d
        where (d.status = :active or d.status = :drawing)
          and d.startAt <= :now
          and d.endAt > :now
        order by
          (select count(s) from DrawEntrySummary s where s.drawId = d.id) desc,
          d.endAt asc,
          d.createdAt desc
    """)
    Page<Long> findHot1PopularOngoingIds(@Param("active") DrawStatus active,
                                         @Param("drawing") DrawStatus drawing,
                                         @Param("now") LocalDateTime now,
                                         Pageable pageable);


    // 2순위 fallback: UPCOMING 중 bookmarkCount desc, tie startAt asc -> createdAt desc
    @Query("""
        select d.id
        from Draw d
        where d.status = :draft
          and d.startAt > :now
        order by
          (select count(b) from luckydrop.demo.draw.bookmark.entity.DrawBookmark b
            where b.id.drawId = d.id) desc,
          d.startAt asc,
          d.createdAt desc
    """)
    Page<Long> findHot2UpcomingBookmarkIds(@Param("draft") DrawStatus draft,
                                           @Param("now") LocalDateTime now,
                                           Pageable pageable);

    // 3순위: 가장 최신 응모 시작한 드로우 (2시간마다 갱신)
    // 구현은 “ONGOING startAt desc”로 1개 뽑고, 캐시는 스케줄러에서 하면 됨.
    @Query("""
        select d.id
        from Draw d
        where (d.status = :active or d.status = :drawing)
          and d.startAt <= :now
          and d.endAt > :now
        order by d.startAt desc, d.createdAt desc
    """)
    Page<Long> findHot3RecentStartedIds(@Param("active") DrawStatus active,
                                        @Param("drawing") DrawStatus drawing,
                                        @Param("now") LocalDateTime now,
                                        Pageable pageable);
}
