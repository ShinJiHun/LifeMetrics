-- 2026-11-12: 인간 페르소나 — 핸들러 핸들 1건씩 기록 (HandlerTripService)
-- 캡처를 Claude 로 읽어 출발지·도착지 주소 → 카카오 좌표 → 카카오 길찾기(priority=TIME, 가장 빠른 길) polyline.
-- 실제 주행 GPS 가 아닌 추정 경로. 캡처 원본은 handler.settlement.path/trips/{id}.{ext}
CREATE TABLE IF NOT EXISTS `riding_db`.`handler_trip` (
    `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`            BIGINT        NOT NULL,
    `trip_date`          DATE          NOT NULL,
    `start_time`         DATETIME      NULL,
    `end_time`           DATETIME      NULL,
    `service_type`       VARCHAR(50)   NULL COMMENT '부름 배달 / 회수 / 존 이동 … 화면 표현 그대로',
    `car_model`          VARCHAR(50)   NULL,
    `car_number`         VARCHAR(20)   NULL,
    `fare`               INT           NULL COMMENT '이 건 운행료(원)',
    `screen_distance_km` DOUBLE        NULL COMMENT '화면에 나온 거리',
    `duration_min`       INT           NULL COMMENT '화면에 나온 소요 시간',
    `origin_name`        VARCHAR(100)  NULL,
    `origin_address`     VARCHAR(200)  NULL,
    `origin_lat`         DOUBLE        NULL,
    `origin_lon`         DOUBLE        NULL,
    `dest_name`          VARCHAR(100)  NULL,
    `dest_address`       VARCHAR(200)  NULL,
    `dest_lat`           DOUBLE        NULL,
    `dest_lon`           DOUBLE        NULL,
    `route_source`       VARCHAR(10)   NULL COMMENT 'kakao / osrm / straight',
    `route_distance_m`   INT           NULL,
    `route_duration_s`   INT           NULL COMMENT '저장 시점 교통 기준 예상 소요',
    `polyline`           MEDIUMTEXT    NULL COMMENT 'encoded polyline (precision 5)',
    `image_file`         VARCHAR(100)  NULL,
    `memo`               VARCHAR(500)  NULL,
    `raw_text`           TEXT          NULL COMMENT 'Claude 판독 원문',
    `created_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_handler_trip_user_date` (`user_id`, `trip_date`),
    INDEX `idx_handler_trip_start` (`start_time`)
);

-- 2026-11-12: 화면의 핸들 코드(예: J0DJFH) — 중복 판단 키
ALTER TABLE `riding_db`.`handler_trip`
    ADD COLUMN IF NOT EXISTS `handle_code` VARCHAR(20) NULL COMMENT '핸들 코드 (화면 제목 아래)' AFTER `user_id`,
    ADD INDEX IF NOT EXISTS `idx_handler_trip_code` (`handle_code`);
