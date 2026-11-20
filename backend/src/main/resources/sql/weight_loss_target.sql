-- 유산소 추천용 월 감량 목표 (2026-11-15)
-- 사용자가 유산소 화면에서 직접 입력하는 "한 달에 몇 kg 뺄지". 하루 적자 목표 = kg × 7700 / 30.
CREATE TABLE IF NOT EXISTS weight_loss_target (
    user_id         BIGINT       NOT NULL PRIMARY KEY,
    monthly_loss_kg DECIMAL(3,1) NOT NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
