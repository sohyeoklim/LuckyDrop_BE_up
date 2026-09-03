package luckydrop.demo.draw.bookmark.repository;

import luckydrop.demo.draw.bookmark.entity.DrawBookmark;
import luckydrop.demo.draw.bookmark.entity.DrawBookmarkId;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import java.util.List;

public interface DrawBookmarkRepository extends JpaRepository<DrawBookmark, DrawBookmarkId> {

    //단건 체크
    boolean existsByIdUserIdAndIdDrawId(Long userId, Long drawId);

    /** 복합 PK 충돌 시 0을 반환하는 멱등 삽입. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO draw_bookmark (user_id, draw_id, created_at, updated_at)
            VALUES (:userId, :drawId, NOW(6), NOW(6))
            """, nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("drawId") Long drawId);

    //찜 취소
    long deleteByIdUserIdAndIdDrawId(Long userId, Long drawId);

    long countByIdDrawId(Long drawId);


    @Query("""
        select b.id.drawId as drawId, count(b) as cnt
        from DrawBookmark b
        where b.id.drawId in :drawIds
        group by b.id.drawId
""")
    List<DrawBookmarkCountView> countByDrawIds(@Param("drawIds") List<Long> drawIds);


    @Query("""
        select count(b)
        from DrawBookmark b
        where b.id.drawId = :drawId
    """)
    long countBookmarks(@Param("drawId") Long drawId);


    //내가 북마크한 드로우 목록
    @Query("""
            select b.id.drawId
            from DrawBookmark b
            where b.id.userId = :userId
                and b.id.drawId in :drawIds
            """)
    List<Long> findBookmarkedDrawIds(@Param("userId") Long userId,
                                     @Param("drawIds") List<Long> drawIds);


    @Query(
            value = """
            select
                d.id as drawId,
                d.title as title,
                d.status as status,
                d.start_at as startAt,
                d.end_at as endAt,
                
                (select count(*) from draw_bookmark b2 where b2.draw_id = d.id) as bookmarkCount,
                (select count(*) from draw_entry_summary s where s.draw_id = d.id) as participants,
                
                (
                    select ii.image_url
                    from inventory_image ii
                    where ii.inventory_id = d.inventory_id
                    order by ii.sort_order asc, ii.id asc
                    limit 1
                ) as thumbnailUrl,
                
                b.created_at as bookmarkedAt
                from draw_bookmark b
                join draw d on d.id = b.draw_id
                where b.user_id = :userId
                order by b.created_at desc
                """,
            countQuery = """
                    select count(*)
                    from draw_bookmark b
                    where b.user_id = :userId
                    """,
            nativeQuery = true
    )
    Page<BookmarkedDrawSummaryView> findMyBookmarkedDraws(@Param("userId") Long userId, Pageable pageable);
}
