ALTER TABLE draw_winner
    ADD COLUMN reward_delivery_deadline_at DATETIME NULL;

CREATE TABLE winner_reward (
    id BIGINT NOT NULL AUTO_INCREMENT,
    winner_id BIGINT NOT NULL,
    delivered_by_user_id BIGINT NOT NULL,
    delivery_type VARCHAR(20) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content TEXT NULL,
    image_filename VARCHAR(255) NULL,
    image_content_type VARCHAR(100) NULL,
    delivered_at DATETIME NOT NULL,
    confirmed_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_winner_reward_winner UNIQUE (winner_id),
    CONSTRAINT fk_winner_reward_winner
        FOREIGN KEY (winner_id) REFERENCES draw_winner (id)
);
