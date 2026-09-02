package luckydrop.demo.draw.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import luckydrop.demo.common.BaseEntity;
import luckydrop.demo.draw.enums.DrawStatus;
import luckydrop.demo.draw.inventory.entity.Inventory;
import luckydrop.demo.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "draw",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_draw_inventory", columnNames = "inventory_id")
        },
        indexes = {
                @Index(name = "idx_draw_status_start_at", columnList = "status, start_at"),
                @Index(name = "idx_draw_status_end_at", columnList = "status, end_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Draw extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    //상품 정보
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    //드로우 이름
    @Column(length = 120, nullable = false)
    private String title;

    //드로우 설명
    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "winner_count", nullable = false)
    private Integer winnerCount;

    @Column(name = "ticket_cost_entry", nullable = false)
    private Integer ticketCostEntry;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private DrawStatus status;

    @Column(name = "end_at_changed", nullable = false)
    private boolean endAtChanged;

    @Column(name = "server_seed", length = 128)
    private String serverSeed;

    @Column(name = "server_seed_hash", length = 64)
    private String serverSeedHash;

    @Column(name = "verification_algorithm", length = 64)
    private String verificationAlgorithm;

    @Column(name = "verification_committed_at")
    private LocalDateTime verificationCommittedAt;

    @Column(name = "participant_snapshot_hash", length = 64)
    private String participantSnapshotHash;

    @Column(name = "drawn_at")
    private LocalDateTime drawnAt;


    //private LocalDateTime createdAt;

    @Builder
    public Draw(
                Long userId,
                Inventory inventory,
                String title,
                String description,
                Integer winnerCount,
                Integer ticketCostEntry,
                LocalDateTime startAt,
                LocalDateTime endAt,
                DrawStatus status) {
        this.userId = userId;
        this.inventory =  inventory;
        this.title = title;
        this.description = description;
        this.winnerCount = winnerCount;
        this.ticketCostEntry = ticketCostEntry;
        this.startAt = startAt;
        this.endAt = endAt;

        this.status = (status != null) ? status : DrawStatus.DRAFT;
        this.endAtChanged = false;
    }

    // ==== 도메인 로직 ====

    //현재 시각 기준 진행중인지
    public boolean inActive(LocalDateTime now) {
        return status == DrawStatus.ACTIVE
                && !now.isBefore(startAt)
                && now.isBefore(endAt);
    }

    // 드로우 종료
    public void close() {
        status = DrawStatus.CLOSE;
    }

    //드로우 취소
    public void cancel() {
        if (this.status == DrawStatus.CANCEL) return;

        if (this.status == DrawStatus.DRAWING || this.status == DrawStatus.CLOSE) {
            throw new IllegalStateException("해당 드로우는 삭제할 수 없습니다.");
        }
        this.status = DrawStatus.CANCEL;
    }

    public void changeDescription(String description) {
        this.description = description;
    }

    public void changeEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public void markEndAtChanged() {
        this.endAtChanged = true;
    }

    public void initializeVerification(String seed, String seedHash, String algorithm, LocalDateTime committedAt) {
        this.serverSeed = seed;
        this.serverSeedHash = seedHash;
        this.verificationAlgorithm = algorithm;
        this.verificationCommittedAt = committedAt;
    }

    public void initializeLegacyDrawingSeed(String seed, String seedHash, String algorithm) {
        this.serverSeed = seed;
        this.serverSeedHash = seedHash;
        this.verificationAlgorithm = algorithm;
    }

    public void completeVerification(String snapshotHash, LocalDateTime completedAt) {
        this.participantSnapshotHash = snapshotHash;
        this.drawnAt = completedAt;
    }

    public boolean hasVerificationCommitment() {
        return verificationCommittedAt != null && serverSeed != null && serverSeedHash != null;
    }
}
