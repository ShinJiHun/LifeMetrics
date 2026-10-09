-- 자전거 피팅 기록 (BikeFit): 피팅 날짜부터 적용되는 세팅 값
-- Schema: riding_db / Table: bike_fit
-- ddl-auto=none 이고 Flyway 도 쓰지 않으므로 운영 DB 에 수동으로 적용한다(멱등: 여러 번 실행해도 안전).

CREATE TABLE IF NOT EXISTS `riding_db`.`bike_fit` (
    `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    `bike_id`            BIGINT        NOT NULL,
    `fit_date`           DATE          NOT NULL COMMENT '피팅 날짜 (이 날부터 적용)',
    `fitter`             VARCHAR(100)  NULL COMMENT '피터 / 샵',
    `saddle_height_mm`   DECIMAL(6, 1) NULL COMMENT '안장 높이 (BB 중심 ~ 안장 상단)',
    `saddle_setback_mm`  DECIMAL(6, 1) NULL COMMENT '안장 셋백 (BB 수직선 ~ 안장 코)',
    `saddle_tilt_deg`    DECIMAL(4, 1) NULL COMMENT '안장 각도 (+ 코 들림)',
    `saddle_model`       VARCHAR(100)  NULL,
    `reach_mm`           DECIMAL(6, 1) NULL COMMENT '안장 코 ~ 핸들바 중심',
    `drop_mm`            DECIMAL(6, 1) NULL COMMENT '안장 상단 ~ 핸들바 상단 높이차',
    `stem_length_mm`     DECIMAL(5, 1) NULL,
    `stem_angle_deg`     DECIMAL(4, 1) NULL,
    `spacer_mm`          DECIMAL(5, 1) NULL COMMENT '스템 아래 스페이서 합계',
    `handlebar_width_mm` DECIMAL(5, 1) NULL,
    `crank_length_mm`    DECIMAL(4, 1) NULL,
    `cleat_left`         VARCHAR(200)  NULL COMMENT '왼쪽 클리트 위치/각도',
    `cleat_right`        VARCHAR(200)  NULL COMMENT '오른쪽 클리트 위치/각도',
    `notes`              TEXT          NULL,
    `created_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_bike_fit_bike_date` (`bike_id`, `fit_date`)
);
