-- 개발자·인간 페르소나 블로그 콘텐츠 (대메뉴/소메뉴/글)
-- ddl-auto=none 이므로 이 스크립트를 journal_db 에 직접 실행한다.
--   mysql -h 127.0.0.1 -P 3307 -u <user> -p journal_db < journal_content.sql

USE `journal_db`;

-- 대메뉴 — 예: 자바의 정석 / 회사 일기
CREATE TABLE IF NOT EXISTS `journal_category` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `persona`    VARCHAR(20)  NOT NULL,            -- developer | human
  `name`       VARCHAR(255) NOT NULL,
  `sort_order` INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_category_persona` (`persona`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 소메뉴 — 예: 1단원 / 26년
CREATE TABLE IF NOT EXISTS `journal_sub_menu` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `category_id` BIGINT       NOT NULL,
  `name`        VARCHAR(255) NOT NULL,
  `sort_order`  INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_sub_category` (`category_id`),
  CONSTRAINT `fk_sub_category` FOREIGN KEY (`category_id`)
      REFERENCES `journal_category` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 글 — visibility: public | private
CREATE TABLE IF NOT EXISTS `journal_post` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `sub_id`     BIGINT       NOT NULL,
  `title`      VARCHAR(512) DEFAULT NULL,
  `content`    LONGTEXT     DEFAULT NULL,
  `visibility` VARCHAR(20)  NOT NULL DEFAULT 'public',
  `created_at` DATETIME     DEFAULT current_timestamp(),
  `updated_at` DATETIME     DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_post_sub` (`sub_id`),
  CONSTRAINT `fk_post_sub` FOREIGN KEY (`sub_id`)
      REFERENCES `journal_sub_menu` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
