-- 휴먼 일기 (2026-11-18) — journal_db
-- 하루 동안 모은 메모(Claude 앱·CLI 대화 정리, 직접 쓴 글)를 밤 10시 배치가 문체 가이드대로 일기 초안으로 만든다.
-- 초안은 관리자가 확인·수정한 뒤 [게시]해야 휴먼 블로그(journal_post)에 올라간다. 조회 포함 관리자 전용.

-- 메모 원문. source: app(Claude 앱 대화 정리) | cli(Claude Code 대화 정리) | manual(직접 입력)
-- file_name 은 파일로 올렸거나 NAS 폴더에서 가져온 경우. 같은 날짜·출처·파일명은 한 번만 들어간다(폴더 재스캔 중복 방지).
CREATE TABLE IF NOT EXISTS `journal_db`.`diary_note` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `note_date`  DATE         NOT NULL,
    `source`     VARCHAR(16)  NOT NULL DEFAULT 'manual',
    `file_name`  VARCHAR(255) DEFAULT NULL,
    `content`    MEDIUMTEXT   NOT NULL,
    `created_at` DATETIME     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_diary_note_date` (`note_date`, `id`),
    UNIQUE KEY `uq_diary_note_file` (`note_date`, `source`, `file_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 초안·게시본. kind: diary(그날 일기) | book(독서 글, 메모에 책 내용이 있을 때만)
-- status: draft | published. 게시하면 post_id 에 journal_post.id 를 남기고 더는 다시 만들지 않는다.
-- input_hash: 초안을 만들 때 쓴 메모·기록의 해시. 배치는 해시가 같으면 건너뛴다(수정한 초안을 덮어쓰지 않도록).
CREATE TABLE IF NOT EXISTS `journal_db`.`diary_entry` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `entry_date`   DATE         NOT NULL,
    `kind`         VARCHAR(16)  NOT NULL DEFAULT 'diary',
    `title`        VARCHAR(255) NOT NULL,
    `content`      MEDIUMTEXT   NOT NULL,          -- 마크다운
    `warnings`     TEXT         DEFAULT NULL,      -- 게시 전 확인할 점(JSON 배열): 실명, 회사 내부 이야기, 부고·건강 등
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'draft',
    `input_hash`   CHAR(64)     DEFAULT NULL,
    `edited`       TINYINT(1)   NOT NULL DEFAULT 0, -- 사람이 고쳤으면 1 (배치가 덮어쓰지 않음)
    `post_id`      BIGINT       DEFAULT NULL,
    `generated_at` DATETIME     NOT NULL,
    `published_at` DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_diary_entry` (`entry_date`, `kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
