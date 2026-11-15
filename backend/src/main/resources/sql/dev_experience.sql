-- 개발자 페르소나 경험 카드 (DevExperience): 프로젝트 중 있었던 일을 "상황 → 원인 → 결정 → 결과 → 배운 점"으로 기록.
-- 개발자 페르소나 챗이 질문과 관련된 카드를 찾아 근거로 쓴다(공개 카드만).
-- Schema: journal_db. ddl-auto=none 이고 Flyway 도 쓰지 않으므로 운영 DB 에 수동으로 적용한다(멱등).

CREATE TABLE IF NOT EXISTS `journal_db`.`dev_experience` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `project`     VARCHAR(100) NOT NULL COMMENT '프로젝트명 (예: LifeMetrics)',
    `happened_on` DATE         NULL COMMENT '있었던 날',
    `title`       VARCHAR(200) NOT NULL,
    `situation`   TEXT         NOT NULL COMMENT '상황·문제',
    `cause`       TEXT         NULL COMMENT '원인 분석',
    `decision`    TEXT         NULL COMMENT '결정과 이유 (고려한 대안 포함)',
    `result`      TEXT         NULL COMMENT '결과 (가능하면 숫자)',
    `lesson`      TEXT         NULL COMMENT '배운 점',
    `tags`        VARCHAR(300) NULL COMMENT '쉼표 구분 (예: Spring,배포,장애 대응)',
    `visibility`  VARCHAR(10)  NOT NULL DEFAULT 'PRIVATE' COMMENT 'PUBLIC | PRIVATE — 방문자 챗에는 PUBLIC 만',
    `source_ref`  VARCHAR(200) NULL COMMENT '근거 (커밋 해시, 링크 등)',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_dev_experience_project` (`project`, `happened_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='개발자 페르소나 경험 카드';
