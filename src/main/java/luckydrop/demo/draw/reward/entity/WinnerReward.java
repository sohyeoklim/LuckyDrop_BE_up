package luckydrop.demo.draw.reward.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import luckydrop.demo.draw.entity.DrawWinner;
import luckydrop.demo.draw.reward.enums.RewardDeliveryType;

import java.time.LocalDateTime;

@Entity
@Table(name = "winner_reward", uniqueConstraints = {
        @UniqueConstraint(name = "uq_winner_reward_winner", columnNames = "winner_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WinnerReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "winner_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_winner_reward_winner")
    )
    private DrawWinner winner;

    @Column(name = "delivered_by_user_id", nullable = false)
    private Long deliveredByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 20)
    private RewardDeliveryType deliveryType;

    @Column(nullable = false, length = 120)
    private String title;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_filename", length = 255)
    private String imageFilename;

    @Column(name = "image_content_type", length = 100)
    private String imageContentType;

    @Column(name = "delivered_at", nullable = false)
    private LocalDateTime deliveredAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    private WinnerReward(DrawWinner winner, Long deliveredByUserId, RewardDeliveryType deliveryType, String title, String content,
                         String imageFilename, String imageContentType) {
        this.winner = winner;
        this.deliveredByUserId = deliveredByUserId;
        this.deliveryType = deliveryType;
        this.title = title;
        this.content = content;
        this.imageFilename = imageFilename;
        this.imageContentType = imageContentType;
        this.deliveredAt = LocalDateTime.now();
    }

    public static WinnerReward createText(DrawWinner winner, Long deliveredByUserId, RewardDeliveryType deliveryType, String title, String content) {
        return new WinnerReward(winner, deliveredByUserId, deliveryType, title, content, null, null);
    }

    public static WinnerReward createImage(DrawWinner winner, Long deliveredByUserId, String title, String imageFilename, String imageContentType) {
        return new WinnerReward(winner, deliveredByUserId, RewardDeliveryType.IMAGE, title, null, imageFilename, imageContentType);
    }

    public void confirm() {
        if (confirmedAt == null) {
            confirmedAt = LocalDateTime.now();
        }
    }
}
