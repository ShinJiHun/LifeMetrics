-- 애슐리트 챗 대화 기록 (AthleteChatSession / AthleteChatMessage)
-- Schema: riding_db. ddl-auto=none 이고 Flyway 도 쓰지 않으므로 운영 DB 에 수동으로 적용한다(멱등).
-- 건강·식단·치료 내용이 들어가므로 조회 API 도 관리자 전용이다.

CREATE TABLE IF NOT EXISTS `riding_db`.`athlete_chat_session` (
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`    BIGINT       NOT NULL DEFAULT 1,
    `title`      VARCHAR(200) NOT NULL COMMENT '첫 질문 앞부분',
    `mode`       VARCHAR(20)  NULL COMMENT '대화를 시작한 챗 모드 (context | tools)',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_athlete_chat_session_user` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='애슐리트 챗 대화';

CREATE TABLE IF NOT EXISTS `riding_db`.`athlete_chat_message` (
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
    `session_id` BIGINT      NOT NULL,
    `role`       VARCHAR(10) NOT NULL COMMENT 'user | assistant',
    `content`    MEDIUMTEXT  NOT NULL,
    `tool_calls` JSON        NULL COMMENT '답변 근거로 호출한 도구 [{name, input, error}]',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_athlete_chat_message_session` (`session_id`, `id`),
    CONSTRAINT `fk_athlete_chat_message_session` FOREIGN KEY (`session_id`)
        REFERENCES `athlete_chat_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='애슐리트 챗 메시지';
