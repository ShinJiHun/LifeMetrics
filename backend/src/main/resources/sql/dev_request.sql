-- 2026-11-13: 개발 요청함 (DevRequestController / deploy/dev-request-worker.py)
-- 앱에서 요청 → 서버 워커가 Claude Code 로 작업 → dev 배포 → 관리자가 승인하면 master 머지·운영 배포.
-- 상태: QUEUED → RUNNING → DEV_READY | ANSWERED(코드 변경 없이 답만) | FAILED
--       DEV_READY → APPROVED → PROMOTING → DONE | FAILED
--       DEV_READY|ANSWERED|FAILED → REJECTED,  QUEUED → CANCELLED,  이어서 보내면 앞 메시지(DEV_READY|ANSWERED|FAILED)는 SUPERSEDED
-- 화면은 채팅: parent_id 로 이어진 행들이 한 대화. 같은 브랜치·Claude 세션(--resume)을 이어서 쓴다.
CREATE TABLE IF NOT EXISTS `riding_db`.`dev_request` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `parent_id`    BIGINT        NULL COMMENT '추가 요청이면 직전 요청',
    `title`        VARCHAR(200)  NOT NULL,
    `body`         TEXT          NOT NULL,
    `status`       VARCHAR(20)   NOT NULL DEFAULT 'QUEUED',
    `branch`       VARCHAR(100)  NULL COMMENT 'devreq/<최초 요청 id>',
    `commit_sha`   VARCHAR(40)   NULL,
    `session_id`   VARCHAR(64)   NULL COMMENT 'Claude 세션 — 추가 요청 시 --resume',
    `summary`      MEDIUMTEXT    NULL COMMENT 'Claude 의 작업 보고',
    `diff_stat`    TEXT          NULL,
    `log_tail`     MEDIUMTEXT    NULL COMMENT '진행 로그(최근 부분)',
    `error`        TEXT          NULL,
    `cost_usd`     DECIMAL(8,4)  NULL,
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `started_at`   DATETIME      NULL,
    `finished_at`  DATETIME      NULL,
    `approved_at`  DATETIME      NULL,
    `promoted_at`  DATETIME      NULL,
    `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '워커 heartbeat — 오래 멈춘 작업 판별',
    INDEX `idx_dev_request_status` (`status`)
);
