-- MySQL 8.0.28 이하에서는 ADD COLUMN IF NOT EXISTS를 지원하지 않습니다.
-- 이 컬럼이 아직 없을 때에만 한 번 실행하세요.
ALTER TABLE draw_winner
    ADD COLUMN delivery_carrier VARCHAR(50) NULL;
