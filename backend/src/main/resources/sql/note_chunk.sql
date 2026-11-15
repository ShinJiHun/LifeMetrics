-- 애슐리트 챗 RAG: 긴 텍스트(치료·진료, 과거 AI 분석 등)를 조각내 임베딩한 것 (NoteChunk)
-- Schema: riding_db. ddl-auto=none 이고 Flyway 도 쓰지 않으므로 운영 DB 에 수동으로 적용한다(멱등).
-- 원본이 바뀌면 content_hash 가 달라져 그 조각만 다시 임베딩한다. 벡터는 float32 리틀엔디언 BLOB(정규화됨).

CREATE TABLE IF NOT EXISTS `riding_db`.`note_chunk` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`      BIGINT       NOT NULL DEFAULT 1,
    `source`       VARCHAR(30)  NOT NULL COMMENT 'treatment | visit | ai_analysis | body_goal | randonneuring',
    `source_id`    BIGINT       NOT NULL COMMENT '원본 테이블 id',
    `chunk_no`     INT          NOT NULL DEFAULT 0,
    `chunk_date`   DATE         NULL COMMENT '원본 날짜 (기간 필터용)',
    `title`        VARCHAR(200) NOT NULL,
    `text`         MEDIUMTEXT   NOT NULL,
    `content_hash` CHAR(64)     NOT NULL COMMENT 'sha256(모델·차원·본문)',
    `model`        VARCHAR(60)  NOT NULL,
    `dims`         INT          NOT NULL,
    `embedding`    MEDIUMBLOB   NOT NULL,
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uq_note_chunk` (`user_id`, `source`, `source_id`, `chunk_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='애슐리트 챗 RAG 조각';
