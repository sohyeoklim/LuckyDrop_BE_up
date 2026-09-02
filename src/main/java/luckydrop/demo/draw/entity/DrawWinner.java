package luckydrop.demo.draw.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.draw.enums.FulfillmentStatus;

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

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Builder
    public DrawWinner(Long drawId, Long userId) {
        this.drawId = drawId;
        this.userId = userId;
    }

    public void updateFulfillment(FulfillmentStatus status, String note, String trackingNumber) {
        this.fulfillmentStatus = status;
        this.fulfillmentNote = note;
        this.trackingNumber = trackingNumber;
        this.fulfilledAt = status == FulfillmentStatus.COMPLETED ? LocalDateTime.now() : null;
    }

    public void initializeDelivery(User user, boolean shippable, LocalDateTime now) {
        if (!shippable) return;
        this.deliveryPhone = user.getPhone();
        this.deliveryAddress = user.getAddress();
        this.addressDeadlineAt = now.plusDays(3);
        if (deliveryPhone == null || deliveryPhone.isBlank() || deliveryAddress == null || deliveryAddress.isBlank()) {
            this.fulfillmentStatus = FulfillmentStatus.ADDRESS_REQUIRED;
        } else {
            this.fulfillmentStatus = FulfillmentStatus.ADDRESS_SUBMITTED;
            this.addressSubmittedAt = now;
        }
    }

    public void expireAddressSubmission() {
        if (fulfillmentStatus == FulfillmentStatus.ADDRESS_REQUIRED) {
            fulfillmentStatus = FulfillmentStatus.EXPIRED;
        }
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
