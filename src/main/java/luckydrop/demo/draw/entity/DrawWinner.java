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

    @Builder
    public DrawWinner(Long drawId, Long userId) {
        this.drawId = drawId;
        this.userId = userId;
    }

    public void updateFulfillment(FulfillmentStatus status, String note) {
        this.fulfillmentStatus = status;
        this.fulfillmentNote = note;
        this.fulfilledAt = status == FulfillmentStatus.COMPLETED ? LocalDateTime.now() : null;
    }
}
