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

-- 2026-11-11: 진단서 업로드로 채우기 — 상병코드와 원본 파일명 (파일 자체는 저장하지 않는다)
ALTER TABLE `riding_db`.`health_treatment`
    ADD COLUMN IF NOT EXISTS `diagnosis_code` VARCHAR(20)  NULL COMMENT 'KCD 상병코드 (예: M76.3)' AFTER `name`,
    ADD COLUMN IF NOT EXISTS `source_file`    VARCHAR(255) NULL COMMENT '진단서에서 읽어 채운 경우 원본 파일명' AFTER `notes`;

-- 2026-11-12: 진료·처방 내역 — 처방전·약제비 영수증·진료비 세부내역서·처방조제내역을 AI 로 읽어 치료 1건 아래에 날짜별로 쌓는다.
-- 원본 파일은 저장하지 않는다. 분석은 그 날짜 전 14일 진료와 복용 기간에 걸친 약을 함께 본다.
CREATE TABLE IF NOT EXISTS `riding_db`.`health_treatment_visit` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`        BIGINT        NOT NULL,
    `treatment_id`   BIGINT        NOT NULL COMMENT 'health_treatment.id',
    `visit_date`     DATE          NOT NULL COMMENT '진료일·조제일',
    `institution`    VARCHAR(100)  NULL COMMENT '의료기관명 (병원·약국)',
    `department`     VARCHAR(50)   NULL COMMENT '진료과',
    `doc_type`       VARCHAR(50)   NULL COMMENT '처방전 / 약제비 영수증 / 진료비 세부내역서 / 처방조제내역 …',
    `diagnosis`      VARCHAR(200)  NULL COMMENT '그날 적힌 상병',
    `items_json`     JSON          NULL COMMENT '[{category,name,dose,timesPerDay,days,drugClass,amount,…}]',
    `total_amount`   INT           NULL COMMENT '진료비 총액(원)',
    `patient_amount` INT           NULL COMMENT '본인부담금(원)',
    `summary`        TEXT          NULL,
    `source_file`    VARCHAR(255)  NULL,
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_htv_treatment_date` (`treatment_id`, `visit_date`),
    INDEX `idx_htv_user_date` (`user_id`, `visit_date`)
);
