-- 연금복권 당첨번호 테이블
-- Schema: lotto_db
-- Table: pension_numbers

CREATE TABLE IF NOT EXISTS `lotto_db`.`pension_numbers` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `round_number` INT NOT NULL UNIQUE COMMENT '회차 (예: 1, 2, 3...)',
    `winning_jo` INT NOT NULL COMMENT '당첨 조 (1-5)',
    `winning_numbers` VARCHAR(10) NOT NULL COMMENT '당첨 6자리 숫자 (예: 123456)',
    `bonus_number` VARCHAR(10) NOT NULL COMMENT '보너스 2자리 숫자 (예: 45)',
    `draw_date` VARCHAR(50) COMMENT '당첨일자 (예: 2024년 9월 6일)',
    `first_prize_amount` BIGINT COMMENT '1등 당첨금 (단위: 원)',
    `accumulated_prize_amount` BIGINT COMMENT '누적 당첨액 (단위: 원)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '데이터 생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '데이터 수정일시',
    INDEX `idx_round_number` (`round_number`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='연금복권 당첨번호 데이터';
