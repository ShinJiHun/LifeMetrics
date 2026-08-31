-- whoami 카드/히어로 집계값 DB 이전 마이그레이션 (journal_db, ddl-auto=none)
--   mysql -h 127.0.0.1 -P 3307 -u tho881 -p journal_db < migrate_profile_stats.sql
-- 모든 문장은 재실행해도 안전하다.

USE `journal_db`;

-- 1) 중복 소개 섹션 정리 (profile.sql 시드를 두 번 돌려서 생긴 중복 행 제거, subtitle별 최소 id만 유지)
DELETE FROM `profile_intro_section`
WHERE `id` NOT IN (
    SELECT `keep_id` FROM (
        SELECT MIN(`id`) AS `keep_id` FROM `profile_intro_section` GROUP BY `subtitle`
    ) AS `keep`
);

-- 2) 새 컬럼 추가
ALTER TABLE `developer_profile` ADD COLUMN IF NOT EXISTS `side_project` VARCHAR(100) DEFAULT NULL AFTER `contact_blurb`;
ALTER TABLE `developer_profile` ADD COLUMN IF NOT EXISTS `availability` VARCHAR(100) DEFAULT NULL AFTER `side_project`;
ALTER TABLE `developer_profile` ADD COLUMN IF NOT EXISTS `open_to_work` TINYINT(1)   DEFAULT 1    AFTER `availability`;
ALTER TABLE `career_company`    ADD COLUMN IF NOT EXISTS `short_name`   VARCHAR(100) DEFAULT NULL AFTER `company_name`;
ALTER TABLE `career_company`    ADD COLUMN IF NOT EXISTS `start_date`   DATE         DEFAULT NULL AFTER `period_label`;
ALTER TABLE `career_company`    ADD COLUMN IF NOT EXISTS `end_date`     DATE         DEFAULT NULL AFTER `start_date`;

-- 3) 값 백필
UPDATE `developer_profile` SET `side_project` = 'LifeMetrics' WHERE `side_project` IS NULL;
UPDATE `developer_profile` SET `availability` = '이직 준비 중' WHERE `availability` IS NULL;
UPDATE `developer_profile` SET `open_to_work` = 1 WHERE `open_to_work` IS NULL;

UPDATE `career_company` SET `short_name` = 'TNS Soft'  WHERE `id` = 1 AND `short_name` IS NULL;
UPDATE `career_company` SET `short_name` = '싸이버원'   WHERE `id` = 2 AND `short_name` IS NULL;
UPDATE `career_company` SET `short_name` = 'KMParking' WHERE `id` = 3 AND `short_name` IS NULL;
UPDATE `career_company` SET `short_name` = '소만사'     WHERE `id` = 4 AND `short_name` IS NULL;
UPDATE `career_company` SET `short_name` = '미디어젠'   WHERE `id` = 5 AND `short_name` IS NULL;

UPDATE `career_company` SET `start_date` = '2024-04-01', `end_date` = '2026-08-14' WHERE `id` = 1 AND `start_date` IS NULL;
UPDATE `career_company` SET `start_date` = '2023-04-01', `end_date` = '2024-04-01' WHERE `id` = 2 AND `start_date` IS NULL;
UPDATE `career_company` SET `start_date` = '2020-11-01', `end_date` = '2023-03-01' WHERE `id` = 3 AND `start_date` IS NULL;
UPDATE `career_company` SET `start_date` = '2020-07-01', `end_date` = '2020-10-01' WHERE `id` = 4 AND `start_date` IS NULL;
UPDATE `career_company` SET `start_date` = '2018-06-01', `end_date` = '2020-06-01' WHERE `id` = 5 AND `start_date` IS NULL;

-- 4) TNS Soft 퇴사 반영 (2026-08-14). 이전 실행에서 end_date 가 비어있던 경우 채운다.
UPDATE `career_company` SET `end_date` = '2026-08-14' WHERE `id` = 1 AND `end_date` IS NULL;
-- 재직중인 회사 없음 → is_current 전부 0 (whoami 는 developer_profile.availability 문구를 대신 표시)
UPDATE `career_company` SET `is_current` = 0 WHERE `is_current` = 1;

-- 확인용
-- SELECT id, company_name, short_name, start_date, end_date, is_current FROM career_company ORDER BY sort_order;
-- SELECT id, subtitle FROM profile_intro_section ORDER BY sort_order;
