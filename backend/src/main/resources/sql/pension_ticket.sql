-- 구매한 연금복권 기록 (PensionTicketEntity)
-- Schema: lotto_db / Table: pension_ticket
-- ddl-auto=none 이고 Flyway 도 쓰지 않으므로 운영 DB 에 수동으로 적용한다(멱등: 여러 번 실행해도 안전).
-- 신규 테이블 추가뿐이라 이 테이블을 모르는 이전 버전 백엔드에는 영향이 없다.

CREATE TABLE IF NOT EXISTS `lotto_db`.`pension_ticket` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `round`        INT         NOT NULL COMMENT '회차',
    `jo`           INT         NOT NULL COMMENT '조 (1-5)',
    `n1`           INT         NOT NULL,
    `n2`           INT         NOT NULL,
    `n3`           INT         NOT NULL,
    `n4`           INT         NOT NULL,
    `n5`           INT         NOT NULL,
    `n6`           INT         NOT NULL,
    `source`       VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'QR | MANUAL',
    `purchased_at` DATE        NULL COMMENT '구매일자',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_pension_ticket_round` (`round`),
    INDEX `idx_pension_ticket_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='구매한 연금복권 기록';
