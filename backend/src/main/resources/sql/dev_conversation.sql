-- 2026-11-13: 개발 요청함 — 상시 대화(대화 시작/종료). 대화마다 워커가 Claude 프로세스를 하나 띄워 두고
-- 메시지를 바로 넣는다. 대화 중에는 코드만 고치고(커밋), [dev 배포]/[대화 종료] 때 한 번에 빌드·개발 서버 배포.
-- 여러 번 실행해도 안전하다.

-- 메시지 종류: MESSAGE(사용자 메시지 → Claude 답) | DEPLOY(대화 브랜치를 빌드해 개발 서버 배포)
ALTER TABLE `riding_db`.`dev_request` ADD COLUMN IF NOT EXISTS `kind` VARCHAR(10) NOT NULL DEFAULT 'MESSAGE' AFTER `parent_id`;
-- 대화의 첫 메시지 id(= 브랜치 devreq/<root_id>)
ALTER TABLE `riding_db`.`dev_request` ADD COLUMN IF NOT EXISTS `root_id` BIGINT NULL AFTER `kind`;
CREATE INDEX IF NOT EXISTS `idx_dev_request_root` ON `riding_db`.`dev_request` (`root_id`);

-- 기존 행 채우기(대화 사슬을 따라 몇 번 반복). 이미 채운 행은 건드리지 않는다.
UPDATE `riding_db`.`dev_request` SET `root_id` = `id` WHERE `parent_id` IS NULL AND `root_id` IS NULL;
UPDATE `riding_db`.`dev_request` c JOIN `riding_db`.`dev_request` p ON c.`parent_id` = p.`id`
   SET c.`root_id` = p.`root_id` WHERE c.`root_id` IS NULL AND p.`root_id` IS NOT NULL;
UPDATE `riding_db`.`dev_request` c JOIN `riding_db`.`dev_request` p ON c.`parent_id` = p.`id`
   SET c.`root_id` = p.`root_id` WHERE c.`root_id` IS NULL AND p.`root_id` IS NOT NULL;
UPDATE `riding_db`.`dev_request` c JOIN `riding_db`.`dev_request` p ON c.`parent_id` = p.`id`
   SET c.`root_id` = p.`root_id` WHERE c.`root_id` IS NULL AND p.`root_id` IS NOT NULL;
UPDATE `riding_db`.`dev_request` c JOIN `riding_db`.`dev_request` p ON c.`parent_id` = p.`id`
   SET c.`root_id` = p.`root_id` WHERE c.`root_id` IS NULL AND p.`root_id` IS NOT NULL;
UPDATE `riding_db`.`dev_request` c JOIN `riding_db`.`dev_request` p ON c.`parent_id` = p.`id`
   SET c.`root_id` = p.`root_id` WHERE c.`root_id` IS NULL AND p.`root_id` IS NOT NULL;

-- 대화 상태: OPEN(시작 요청, 워커가 프로세스 띄우는 중) → LIVE(프로세스 대기 중) → CLOSED(종료·자동 종료)
CREATE TABLE IF NOT EXISTS `riding_db`.`dev_conversation` (
    `root_id`        BIGINT       PRIMARY KEY COMMENT 'dev_request.root_id',
    `state`          VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    `opened_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_active_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `closed_at`      DATETIME     NULL,
    `close_reason`   VARCHAR(200) NULL,
    INDEX `idx_dev_conversation_state` (`state`)
);
