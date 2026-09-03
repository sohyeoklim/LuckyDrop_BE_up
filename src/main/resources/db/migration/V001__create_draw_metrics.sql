-- Existing production databases must run this once before switching reads to draw_metrics.
CREATE TABLE IF NOT EXISTS draw_metrics (
    draw_id BIGINT NOT NULL,
    bookmark_count BIGINT NOT NULL DEFAULT 0,
    participant_count BIGINT NOT NULL DEFAULT 0,
    entry_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (draw_id),
    CONSTRAINT fk_draw_metrics_draw
        FOREIGN KEY (draw_id) REFERENCES draw (id)
);

-- One metrics row per existing draw. Re-running this statement is safe.
INSERT INTO draw_metrics (
    draw_id,
    bookmark_count,
    participant_count,
    entry_count,
    created_at,
    updated_at
)
SELECT
    metrics.draw_id,
    metrics.bookmark_count,
    metrics.participant_count,
    metrics.entry_count,
    metrics.created_at,
    metrics.updated_at
FROM (
    SELECT
        d.id AS draw_id,
        COALESCE(b.bookmark_count, 0) AS bookmark_count,
        COALESCE(e.participant_count, 0) AS participant_count,
        COALESCE(e.entry_count, 0) AS entry_count,
        NOW(6) AS created_at,
        NOW(6) AS updated_at
    FROM draw d
    LEFT JOIN (
        SELECT draw_id, COUNT(*) AS bookmark_count
        FROM draw_bookmark
        GROUP BY draw_id
    ) b ON b.draw_id = d.id
    LEFT JOIN (
        SELECT draw_id, COUNT(*) AS participant_count, SUM(entry_count) AS entry_count
        FROM draw_entry_summary
        GROUP BY draw_id
    ) e ON e.draw_id = d.id
) AS metrics
ON DUPLICATE KEY UPDATE
    bookmark_count = metrics.bookmark_count,
    participant_count = metrics.participant_count,
    entry_count = metrics.entry_count,
    updated_at = metrics.updated_at;
