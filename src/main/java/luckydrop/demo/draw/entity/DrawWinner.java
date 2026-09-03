package luckydrop.demo.draw.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.draw.enums.FulfillmentStatus;
import luckydrop.demo.draw.reward.entity.WinnerReward;

import java.time.LocalDateTime;

@Entity
@Table(name = "draw_winner",
uniqueConstraints = {
        @UniqueConstraint(name = "up_draw_winner_draw_user", columnNames = {"drawId", "userId"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawWinner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draw_id", nullable = false)
    private Long drawId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 관계 명시용
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "draw_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_draw_winner_draw")
    )
    private Draw draw;

    // 관계 명시용
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_draw_winner_user")
    )
    private User user;

    @OneToOne(mappedBy = "winner", fetch = FetchType.LAZY)
    private WinnerReward reward;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 20)
    private FulfillmentStatus fulfillmentStatus = FulfillmentStatus.PENDING;

    @Column(name = "fulfillment_note", length = 500)
    private String fulfillmentNote;

    @Column(name = "fulfilled_at")
    private LocalDateTime fulfilledAt;

    @Column(name = "delivery_phone", length = 30)
    private String deliveryPhone;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    @Column(name = "address_deadline_at")
    private LocalDateTime addressDeadlineAt;

    @Column(name = "address_submitted_at")
    private LocalDateTime addressSubmittedAt;

    @Column(name = "reward_delivery_deadline_at")
    private LocalDateTime rewardDeliveryDeadlineAt;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "delivery_carrier", length = 50)
    private String deliveryCarrier;

    @Builder
    public DrawWinner(Long drawId, Long userId) {
        this.drawId = drawId;
        this.userId = userId;
    }

    public void updateFulfillment(FulfillmentStatus status, String note, String deliveryCarrier, String trackingNumber) {
        this.fulfillmentStatus = status;
        this.fulfillmentNote = note;
        this.deliveryCarrier = deliveryCarrier;
        this.trackingNumber = trackingNumber;
        this.fulfilledAt = status == FulfillmentStatus.COMPLETED ? LocalDateTime.now() : null;
    }

    public void initializeShipping(User user, LocalDateTime now) {
        // 프로필 정보는 배송지 입력 화면의 초기값으로만 사용한다.
        // 당첨자가 직접 확인·제출하기 전까지는 배송지 제출 상태가 아니다.
        this.deliveryPhone = user.getPhone();
        this.deliveryAddress = user.getAddress();
        this.addressDeadlineAt = now.plusDays(3);
        this.addressSubmittedAt = null;
        this.fulfillmentStatus = FulfillmentStatus.ADDRESS_REQUIRED;
    }

    public void initializeRewardDelivery(LocalDateTime now) {
        this.rewardDeliveryDeadlineAt = now.plusDays(5);
        this.fulfillmentStatus = FulfillmentStatus.REWARD_PENDING;
    }

    public void expireAddressSubmission() {
        if (fulfillmentStatus == FulfillmentStatus.ADDRESS_REQUIRED) {
            fulfillmentStatus = FulfillmentStatus.EXPIRED;
        }
    }

    public void expireRewardDelivery() {
        if (fulfillmentStatus == FulfillmentStatus.REWARD_PENDING) {
            fulfillmentStatus = FulfillmentStatus.EXPIRED;
        }
    }

    public void markRewardDelivered() {
        if (fulfillmentStatus != FulfillmentStatus.REWARD_PENDING) {
            throw new IllegalStateException("전달 대기 중인 비배송 보상만 전달할 수 있습니다.");
        }
        this.fulfillmentStatus = FulfillmentStatus.REWARD_DELIVERED;
    }

    public void completeReward() {
        if (fulfillmentStatus != FulfillmentStatus.REWARD_DELIVERED) {
            throw new IllegalStateException("전달된 보상만 수령 완료할 수 있습니다.");
        }
        this.fulfillmentStatus = FulfillmentStatus.COMPLETED;
        this.fulfilledAt = LocalDateTime.now();
    }

    public void submitDeliveryAddress(String phone, String address) {
        if (fulfillmentStatus != FulfillmentStatus.ADDRESS_REQUIRED || addressDeadlineAt == null
                || !LocalDateTime.now().isBefore(addressDeadlineAt)) {
            throw new IllegalStateException("배송 정보는 제출 기한 내에 한 번만 제출할 수 있습니다.");
        }
        this.deliveryPhone = phone;
        this.deliveryAddress = address;
        this.addressSubmittedAt = LocalDateTime.now();
        this.fulfillmentStatus = FulfillmentStatus.ADDRESS_SUBMITTED;
    }
}
