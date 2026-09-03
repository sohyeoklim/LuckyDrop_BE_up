ALTER TABLE winner_reward
    ADD COLUMN delivered_by_user_id BIGINT NULL;

-- Existing rewards predate the audit field. Their opener is the event host.
UPDATE winner_reward wr
JOIN draw_winner dw ON dw.id = wr.winner_id
JOIN draw d ON d.id = dw.draw_id
SET wr.delivered_by_user_id = d.user_id
WHERE (wr.delivered_by_user_id IS NULL OR wr.delivered_by_user_id = 0)
  AND wr.id > 0;

ALTER TABLE winner_reward
    MODIFY COLUMN delivered_by_user_id BIGINT NOT NULL;
