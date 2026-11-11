-- 치료·부상 기록 (HealthTreatment): 관리자가 직접 적는다.
-- 일일분석·라이딩 분석·웨이트 세션 분석이 그 날짜에 진행 중이던 치료를 함께 보고 해석한다
-- (예: 장경인대 치료 중이라 하체를 가볍게 한 날을 "강도 부족"이 아니라 "치료에 맞춘 조절"로 본다).
-- end_date 가 NULL 이면 치료 중.
-- Schema: riding_db / Table: health_treatment
-- ddl-auto=none 이고 Flyway 도 쓰지 않으므로 운영 DB 에 수동으로 적용한다(멱등: 여러 번 실행해도 안전).

CREATE TABLE IF NOT EXISTS `riding_db`.`health_treatment` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`       BIGINT        NOT NULL,
    `name`          VARCHAR(100)  NOT NULL COMMENT '진단명/부상명 (예: 장경인대 증후군)',
    `body_part`     VARCHAR(100)  NULL COMMENT '부위 (예: 오른쪽 무릎 바깥쪽)',
    `start_date`    DATE          NOT NULL,
    `end_date`      DATE          NULL COMMENT 'NULL 이면 치료 중',
    `restrictions`  TEXT          NULL COMMENT '운동 제한·권고 (예: 하체 고중량·러닝 자제)',
    `notes`         TEXT          NULL COMMENT '치료 내용·경과 메모',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_health_treatment_user_dates` (`user_id`, `start_date`, `end_date`)
);
