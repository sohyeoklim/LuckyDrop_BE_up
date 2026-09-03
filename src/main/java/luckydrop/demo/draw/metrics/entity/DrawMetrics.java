package luckydrop.demo.draw.metrics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import luckydrop.demo.common.BaseEntity;
import luckydrop.demo.draw.entity.Draw;

/**
 * 드로우 목록 정렬과 표시용 집계값의 기준 데이터.
 *
 * <p>각 draw에 정확히 한 행을 두며, 카운터 변경은 이후 쓰기 경로에서 원자적으로 처리한다.</p>
 */
@Entity
@Table(name = "draw_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawMetrics extends BaseEntity {

    @Id
    @Column(name = "draw_id", nullable = false)
    private Long drawId;

    @Column(name = "bookmark_count", nullable = false)
    private long bookmarkCount;

    @Column(name = "participant_count", nullable = false)
    private long participantCount;

    @Column(name = "entry_count", nullable = false)
    private long entryCount;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "draw_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_draw_metrics_draw")
    )
    private Draw draw;

    public static DrawMetrics empty(Long drawId) {
        DrawMetrics metrics = new DrawMetrics();
        metrics.drawId = drawId;
        return metrics;
    }
}
