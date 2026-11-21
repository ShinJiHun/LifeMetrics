-- 휴먼 일기: 강연 요약 (2026-11-21) — journal_db
-- 일기 메모(diary_note)에 강연 필기가 있으면 일기와 따로 강연 요약 초안을 만든다. 메모 하나에 강연 여러 개도 가능.
-- 원본(diary_note, NAS inbox 파일)은 고치지 않는다. 여러 번 실행해도 안전하다.

-- 강연 요약 초안·게시본. source_note_id 로 원본 메모를 가리킨다. 메모 하나에 강연이 여럿이면(카톡 필기 등)
-- 같은 source_note_id 로 여러 건이 생기고 seq(메모 안 순서, 0부터)로 구분한다.
-- 메모를 지우면 고치지 않은 초안은 같이 지우고, 고쳤거나 게시한 것은 source_note_id 만 NULL 로 남긴다.
-- status: draft | published. edited=1(사람이 고침)이거나 게시한 것은 배치가 다시 만들지 않는다.
CREATE TABLE IF NOT EXISTS `journal_db`.`lecture_entry` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `source_note_id` BIGINT       DEFAULT NULL,
    `seq`            INT          NOT NULL DEFAULT 0,
    `lecture_date`   DATE         NOT NULL,          -- 메모 날짜
    `title`          VARCHAR(255) NOT NULL,
    `speaker`        VARCHAR(255) DEFAULT NULL,      -- 연사 (태그가 아니라 별도 항목)
    `content`        MEDIUMTEXT   NOT NULL,          -- 마크다운: 핵심 주장, 구조, 인상 깊은 문장, 내 생각
    `warnings`       TEXT         DEFAULT NULL,      -- 게시 전 확인할 점(JSON 배열)
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'draft',
    `input_hash`     CHAR(64)     DEFAULT NULL,      -- 만들 때 쓴 메모 내용의 해시
    `edited`         TINYINT(1)   NOT NULL DEFAULT 0,
    `post_id`        BIGINT       DEFAULT NULL,
    `generated_at`   DATETIME     NOT NULL,
    `published_at`   DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_lecture_entry_note_seq` (`source_note_id`, `seq`),
    KEY `idx_lecture_entry_date` (`lecture_date`, `id`),
    KEY `idx_lecture_entry_speaker` (`speaker`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 처음(메모 1:1) 구조로 이미 만들어진 테이블 맞추기: seq 추가, 유니크 키를 (메모, seq) 로. 인덱스만 바꾸고 데이터는 그대로.
ALTER TABLE `journal_db`.`lecture_entry` ADD COLUMN IF NOT EXISTS `seq` INT NOT NULL DEFAULT 0 AFTER `source_note_id`;
ALTER TABLE `journal_db`.`lecture_entry` ADD UNIQUE KEY IF NOT EXISTS `uq_lecture_entry_note_seq` (`source_note_id`, `seq`);
ALTER TABLE `journal_db`.`lecture_entry` DROP INDEX IF EXISTS `uq_lecture_entry_note`;

-- 태그(주제·분야·키워드). 대소문자 구분 없는 콜레이션이라 'ai' 와 'AI' 는 같은 태그.
CREATE TABLE IF NOT EXISTS `journal_db`.`lecture_tag` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `name`       VARCHAR(64) NOT NULL,
    `created_at` DATETIME    NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_lecture_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `journal_db`.`lecture_entry_tag` (
    `entry_id` BIGINT NOT NULL,
    `tag_id`   BIGINT NOT NULL,
    PRIMARY KEY (`entry_id`, `tag_id`),
    KEY `idx_lecture_entry_tag_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 메모별 강연 판별 결과. 내용 해시가 같으면 AI 를 다시 부르지 않는다
-- (강연이 아닌 메모를 매번 다시 보지 않고, [버리기]한 요약이 다음 배치에 되살아나지 않게).
CREATE TABLE IF NOT EXISTS `journal_db`.`lecture_note_scan` (
    `note_id`    BIGINT     NOT NULL,
    `input_hash` CHAR(64)   NOT NULL,
    `is_lecture` TINYINT(1) NOT NULL DEFAULT 0,
    `scanned_at` DATETIME   NOT NULL,
    PRIMARY KEY (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
