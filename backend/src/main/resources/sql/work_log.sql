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

-- 업로드한 일일·주간보고 JSON 원본 (2026-11-16). 형식이 정해지기 전이라 원본 그대로 보관하고,
-- 나중에 이걸 읽어 포트폴리오 데이터셋으로 정리한다.
CREATE TABLE IF NOT EXISTS `work_log_upload` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `file_name`   VARCHAR(255) NOT NULL,
    `content`     LONGTEXT     NOT NULL,  -- JSON 원문
    `size_bytes`  INT          NOT NULL,
    `uploaded_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_work_log_upload_at` (`uploaded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 업로드한 JSON 을 보고서 단위로 풀어 저장 (2026-11-16). 업로드 파일은 누적 원본이라 매번 전체가 올라오므로
-- (kind, report_date) 기준으로 덮어쓴다. 일일보고 report_date = 보고일, 주간보고 = 기간 시작일(period_end = 종료일).
-- 원문 항목(raw_json)도 같이 보관 — 포트폴리오에 옮길 때는 보안 정보를 일반화한 뒤 승인받아 반영한다.
CREATE TABLE IF NOT EXISTS `work_log_report` (
    `kind`         VARCHAR(10)  NOT NULL,  -- daily | weekly
    `report_date`  DATE         NOT NULL,
    `period_end`   DATE         DEFAULT NULL,
    `category`     VARCHAR(255) DEFAULT NULL,
    `title`        VARCHAR(255) DEFAULT NULL,
    `author`       VARCHAR(100) DEFAULT NULL,
    `summary_json` LONGTEXT     DEFAULT NULL,  -- [{task, content, status}]
    `plan_json`    LONGTEXT     DEFAULT NULL,  -- 주간: [{task, content}]
    `details_json` LONGTEXT     DEFAULT NULL,  -- [{heading, items[]}]
    `raw_json`     LONGTEXT     NOT NULL,
    `upload_id`    BIGINT       DEFAULT NULL,  -- 마지막으로 반영한 업로드
    `created_at`   DATETIME     NOT NULL,
    `updated_at`   DATETIME     NOT NULL,
    PRIMARY KEY (`kind`, `report_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
