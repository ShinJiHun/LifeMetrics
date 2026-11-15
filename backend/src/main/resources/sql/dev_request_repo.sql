-- 2026-11-13: 개발 요청함 — 대화마다 작업 대상 저장소를 고른다(lifemetrics | data_pipeline). 여러 번 실행해도 안전하다.
-- 대화의 첫 메시지에서 정하고 이어지는 메시지·배포 행이 물려받는다.
ALTER TABLE `riding_db`.`dev_request` ADD COLUMN IF NOT EXISTS `repo` VARCHAR(30) NOT NULL DEFAULT 'lifemetrics' AFTER `root_id`;
