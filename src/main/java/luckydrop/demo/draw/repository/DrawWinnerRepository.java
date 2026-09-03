package luckydrop.demo.draw.repository;

import luckydrop.demo.draw.dto.response.DrawWinnerResponse;
import luckydrop.demo.draw.dto.response.HostWinnerInfoResponse;
import luckydrop.demo.draw.entity.DrawWinner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface DrawWinnerRepository  extends JpaRepository<DrawWinner, Long> {

    boolean existsByDrawId(Long drawId); // 드로우 추첨 완료 여부

    boolean existsByDrawIdAndUserId(Long drawId, Long userId); // 현재 유저 당첨 여부 확인

    //유저별 당첨 조회
    List<DrawWinner> findByUserId(Long userId);

    List<DrawWinner> findByDrawId(Long drawId);

    Optional<DrawWinner> findByDrawIdAndUserId(Long drawId, Long userId);
    long countByDrawIdAndFulfillmentStatus(Long drawId, luckydrop.demo.draw.enums.FulfillmentStatus status);

    List<DrawWinner> findByFulfillmentStatusAndAddressDeadlineAtBefore(
            luckydrop.demo.draw.enums.FulfillmentStatus status, LocalDateTime now);

    List<DrawWinner> findByFulfillmentStatusAndRewardDeliveryDeadlineAtBefore(
            luckydrop.demo.draw.enums.FulfillmentStatus status, LocalDateTime now);

    // 특정 드로우 당첨자 응모한 티켓수, 닉네임 가져오기
    @Query("""
        select new luckydrop.demo.draw.dto.response.DrawWinnerResponse$WinnerItem(
                u.nickname,
                des.entryCount
            )
            from DrawWinner dw
            join User u on u.id = dw.userId
            join DrawEntrySummary des on des.drawId = dw.drawId and des.userId = dw.userId
            where dw.drawId = :drawId
""")
    List<DrawWinnerResponse.WinnerItem> findWinners(@Param("drawId") Long drawId);

    @Query("""
        select new luckydrop.demo.draw.dto.response.HostWinnerInfoResponse(
        dw.id,
        u.id,
        u.name,
        u.nickname,
        dw.deliveryPhone,
        dw.deliveryAddress,
        dw.fulfillmentStatus,
        dw.fulfillmentNote,
        dw.deliveryCarrier,
        dw.trackingNumber,
        wr.title,
        wr.deliveryType,
        wr.deliveredAt
        )
        from DrawWinner dw
        join dw.user u
        left join dw.reward wr
        where dw.drawId = :drawId
        order by dw.id asc
""")
    List<HostWinnerInfoResponse> findHostWinnerInfoByDrawId(@Param("drawId") Long drawId);
}
