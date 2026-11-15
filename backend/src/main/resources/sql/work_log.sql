-- 업무 일지 (2026-11-15) — journal_db
-- 채팅으로 오늘 한 일을 적으면 AI 가 금일 실적 / 명일 계획 / 이슈로 정리하고, 주간보고는 일일 기록을 모아 만든다.
-- 관리자 전용(조회 포함). 회사 보안 정보(고객정보·IP·계정·내부 시스템명 등)는 넣지 않는다.

CREATE TABLE IF NOT EXISTS `work_log_entry` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `log_date`      DATE         NOT NULL,
    `company`       VARCHAR(100) DEFAULT NULL,
    `today_done`    TEXT         DEFAULT NULL,  -- 금일 실적 (줄마다 한 항목)
    `tomorrow_plan` TEXT         DEFAULT NULL,  -- 명일 계획
    `issues`        TEXT         DEFAULT NULL,  -- 이슈 및 해결
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_work_log_date` (`log_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 날짜별 대화 (다시 열면 이어서 대화)
CREATE TABLE IF NOT EXISTS `work_log_message` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `log_date`   DATE        NOT NULL,
    `role`       VARCHAR(16) NOT NULL,  -- user | assistant
    `content`    TEXT        NOT NULL,
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_work_log_message_date` (`log_date`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 주간보고 (월요일 기준)
CREATE TABLE IF NOT EXISTS `work_log_weekly` (
    `week_start`   DATE     NOT NULL,
    `done_summary` TEXT     DEFAULT NULL,  -- 주간 실적
    `next_plan`    TEXT     DEFAULT NULL,  -- 차주 계획
    `issues`       TEXT     DEFAULT NULL,  -- 이슈 및 해결
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`week_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
