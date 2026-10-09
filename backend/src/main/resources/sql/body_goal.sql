-- 체성분 목표 (BodyGoal): 설정 시점의 시작값과 목표값을 고정해 둔다.
-- 인바디 기록지의 목표 체중은 측정할 때마다 바뀌므로 최신 기록을 그대로 쓰지 않고 여기 스냅샷으로 남긴다.
-- Schema: riding_db / Table: body_goal
-- ddl-auto=none 이고 Flyway 도 쓰지 않으므로 운영 DB 에 수동으로 적용한다(멱등: 여러 번 실행해도 안전).

CREATE TABLE IF NOT EXISTS `riding_db`.`body_goal` (
    `id`                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`             BIGINT        NOT NULL,
    `status`              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ACHIEVED / ABANDONED',
    `source`              VARCHAR(20)   NOT NULL COMMENT 'INBODY(기록지 조절값) / FORMULA(계산) / MANUAL(직접 수정)',
    `base_record_id`      BIGINT        NULL COMMENT '목표 산출에 쓴 user_body_record.id',
    `start_date`          DATE          NOT NULL,
    `start_weight`        DECIMAL(5, 1) NULL,
    `start_fat_mass`      DECIMAL(5, 1) NULL,
    `start_muscle_mass`   DECIMAL(5, 1) NULL COMMENT '골격근량',
    `target_weight`       DECIMAL(5, 1) NULL,
    `target_fat_mass`     DECIMAL(5, 1) NULL,
    `target_muscle_mass`  DECIMAL(5, 1) NULL,
    `target_date`         DATE          NULL,
    `notes`               TEXT          NULL,
    `created_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_body_goal_user_status` (`user_id`, `status`)
);
