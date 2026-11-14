-- 랜도너스 공식 참가 기록 (RandonneuringResult): 브레베·퍼머넌트·플레쉬 참가 이력과 완주/DNF.
-- activity_core 는 GPS 기록이 있는 라이딩만 담으므로, 경력(완주·DNF 이력)은 이 테이블이 기준이다.
-- Schema: riding_db / Table: randonneuring_result
-- ddl-auto=none 이고 Flyway 도 쓰지 않으므로 운영 DB 에 수동으로 적용한다(멱등: 여러 번 실행해도 안전).
-- 개인 기록 데이터는 sql/local/ (gitignore) 에 따로 둔다.

CREATE TABLE IF NOT EXISTS `riding_db`.`randonneuring_result` (
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`          BIGINT       NOT NULL DEFAULT 1,
    `event_date`       DATE         NOT NULL COMMENT '출발일',
    `organizer`        VARCHAR(30)  NULL COMMENT '주최 (예: KORA)',
    `event_type`       VARCHAR(20)  NOT NULL COMMENT 'BREVET | PERMANENT | FLECHE | POPULAIRE',
    `event_name`       VARCHAR(200) NOT NULL COMMENT '코스명 (공식 기록 표기)',
    `permanent_no`     VARCHAR(20)  NULL COMMENT '퍼머넌트 번호 (예: PT-193)',
    `distance_km`      INT          NULL COMMENT '공식 코스 거리 (DNF 도 코스 거리)',
    `status`           VARCHAR(10)  NOT NULL COMMENT 'FINISHED | DNF',
    `activity_core_id` BIGINT       NULL COMMENT '연결된 라이딩 기록 (GPS 가 있을 때)',
    `notes`            TEXT         NULL,
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uq_rando_result` (`user_id`, `event_date`, `event_name`),
    INDEX `idx_rando_result_user_date` (`user_id`, `event_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='랜도너스 공식 참가 기록';

-- 2026-11-14: KORA 기록 API 원본 항목 추가 (완주 시간, 원본 id, 인증번호)
ALTER TABLE `riding_db`.`randonneuring_result`
    ADD COLUMN IF NOT EXISTS `riding_time_sec` INT         NULL COMMENT '완주 시간(초). DNF 는 null' AFTER `status`,
    ADD COLUMN IF NOT EXISTS `source_id`       VARCHAR(64) NULL COMMENT '원본 id (kora_source_id 또는 ebr:<entry_id>)' AFTER `activity_core_id`,
    ADD COLUMN IF NOT EXISTS `homologation_no` VARCHAR(30) NULL COMMENT '인증번호' AFTER `source_id`;
ALTER TABLE `riding_db`.`randonneuring_result`
    ADD UNIQUE INDEX IF NOT EXISTS `uq_rando_result_source` (`source_id`);
