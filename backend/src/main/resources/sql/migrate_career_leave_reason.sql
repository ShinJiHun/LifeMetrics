-- 회사별 퇴사/이직 사유 + 현재 구직 상황 서술용 컬럼 추가 (journal_db, ddl-auto=none)
--   mysql -h 127.0.0.1 -P 3307 -u tho881 -p journal_db < migrate_career_leave_reason.sql
--
-- 이 두 컬럼의 실제 값(퇴사 사유, 구직 상황)은 개인정보라 공개 저장소에 두지 않는다.
--   → sql/local/career_personal_data.sql (gitignore) 로 1회 주입하거나 관리자 화면에서 입력.
-- 포트폴리오 화면에는 노출하지 않고 페르소나 챗 컨텍스트로만 쓴다.

USE `journal_db`;

ALTER TABLE `career_company`    ADD COLUMN IF NOT EXISTS `leave_reason`    TEXT DEFAULT NULL AFTER `role`;
ALTER TABLE `developer_profile` ADD COLUMN IF NOT EXISTS `job_search_note` TEXT DEFAULT NULL AFTER `open_to_work`;

-- profile_intro_section "커리어" 문구가 현재형이면 과거형으로 (재직중인 회사 없음)
UPDATE `profile_intro_section`
SET `lines` = REPLACE(`lines`, '현재는 TNS Soft에서 음성인식(STT) 도메인의 백엔드와 인프라를 담당하고 있습니다.',
                                'TNS Soft에서 음성인식(STT) 도메인의 백엔드와 인프라를 담당했습니다.')
WHERE `subtitle` = '커리어' AND `lines` LIKE '%현재는 TNS Soft에서%';
