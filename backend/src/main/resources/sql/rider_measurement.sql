-- 라이더 신체 치수 (RiderMeasurement): 피팅에 쓰는 몸 치수·발·신발 정보. 측정일별로 쌓아 변화를 본다.
-- Schema: riding_db / Table: rider_measurement
-- ddl-auto=none 이고 Flyway 도 쓰지 않으므로 운영 DB 에 수동으로 적용한다(멱등: 여러 번 실행해도 안전).

CREATE TABLE IF NOT EXISTS `riding_db`.`rider_measurement` (
    `id`                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`               BIGINT        NOT NULL DEFAULT 1,
    `measure_date`          DATE          NOT NULL COMMENT '측정일',
    `measured_by`           VARCHAR(100)  NULL COMMENT '측정한 곳 (피팅샵 / 직접)',
    `height_cm`             DECIMAL(5, 1) NULL COMMENT '키',
    `inseam_mm`             DECIMAL(6, 1) NULL COMMENT '인심 (바닥 ~ 사타구니)',
    `torso_mm`              DECIMAL(6, 1) NULL COMMENT '몸통 길이 (사타구니 ~ 흉골 상단)',
    `arm_mm`                DECIMAL(6, 1) NULL COMMENT '팔 길이 (어깨 끝 ~ 손목)',
    `shoulder_width_mm`     DECIMAL(6, 1) NULL COMMENT '어깨 폭 (견봉 사이)',
    `sit_bone_width_mm`     DECIMAL(5, 1) NULL COMMENT '좌골 폭',
    `foot_length_left_mm`   DECIMAL(5, 1) NULL COMMENT '왼발 길이',
    `foot_length_right_mm`  DECIMAL(5, 1) NULL COMMENT '오른발 길이',
    `foot_width_left_mm`    DECIMAL(5, 1) NULL COMMENT '왼발 볼 너비',
    `foot_width_right_mm`   DECIMAL(5, 1) NULL COMMENT '오른발 볼 너비',
    `arch_type`             VARCHAR(20)   NULL COMMENT '아치: LOW | MEDIUM | HIGH',
    `shoe_size_mm`          DECIMAL(5, 1) NULL COMMENT '신발 사이즈 (mm, 한국 기준)',
    `shoe_size_eu`          DECIMAL(4, 1) NULL COMMENT '신발 사이즈 (EU)',
    `shoe_model`            VARCHAR(100)  NULL COMMENT '클릿 슈즈 모델',
    `insole`                VARCHAR(100)  NULL COMMENT '깔창',
    `flexibility`           VARCHAR(200)  NULL COMMENT '유연성 (예: 전굴 손바닥 닿음)',
    `notes`                 TEXT          NULL,
    `created_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_rider_measurement_user_date` (`user_id`, `measure_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='라이더 신체 치수 (피팅용)';
