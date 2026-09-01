-- 건강검진(국민건강보험공단 일반건강검진 결과통보서) 기록 테이블
-- prod(riding_db)에 직접 실행할 것. ddl-auto=none.
--   mysql -h 127.0.0.1 -P 3307 -u tho881 -p riding_db < add_health_checkup.sql
--
-- 관리자만 조회하는 데이터(HealthCheckupController 에서 강제).
-- 주요 수치는 개별 컬럼으로 파싱해 저장(대시보드 시계열 쿼리용),
-- 원본 텍스트는 raw_text 에 통째로 보관(공단 양식 변경/누락 항목 대조용).

CREATE TABLE IF NOT EXISTS `health_checkup` (
    `id`                       BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`                  BIGINT       NOT NULL,

    -- 메타 / 종합소견
    `checkup_date`             DATE         NOT NULL,
    `checkup_place`            VARCHAR(20)  DEFAULT NULL,   -- 내원 | 출장
    `checkup_org`              VARCHAR(255) DEFAULT NULL,
    `checkup_doctor`           VARCHAR(100) DEFAULT NULL,
    `overall_judgment`         VARCHAR(100) DEFAULT NULL,
    `extra_exams`              VARCHAR(255) DEFAULT NULL,
    `suspected_disease`        TEXT         DEFAULT NULL,
    `existing_disease`         TEXT         DEFAULT NULL,
    `lifestyle_advice`         TEXT         DEFAULT NULL,
    `etc_advice`               TEXT         DEFAULT NULL,

    -- 계측검사
    `height_cm`                DECIMAL(5,1) DEFAULT NULL,
    `weight_kg`                DECIMAL(5,1) DEFAULT NULL,
    `bmi`                      DECIMAL(4,1) DEFAULT NULL,
    `bmi_grade`               VARCHAR(20)  DEFAULT NULL,
    `waist_cm`                DECIMAL(5,1) DEFAULT NULL,
    `waist_result`            VARCHAR(20)  DEFAULT NULL,
    `vision_left`             DECIMAL(3,1) DEFAULT NULL,
    `vision_right`            DECIMAL(3,1) DEFAULT NULL,
    `vision_corrected`        TINYINT(1)   DEFAULT NULL,
    `hearing_left`            VARCHAR(20)  DEFAULT NULL,
    `hearing_right`           VARCHAR(20)  DEFAULT NULL,
    `hearing_result`          VARCHAR(40)  DEFAULT NULL,

    -- 혈압
    `systolic_bp`             INT          DEFAULT NULL,
    `diastolic_bp`            INT          DEFAULT NULL,
    `bp_result`               VARCHAR(40)  DEFAULT NULL,

    -- 혈액검사
    `hemoglobin`             DECIMAL(4,1) DEFAULT NULL,
    `anemia_result`          VARCHAR(40)  DEFAULT NULL,
    `fasting_blood_sugar`    INT          DEFAULT NULL,
    `diabetes_result`        VARCHAR(40)  DEFAULT NULL,
    `total_cholesterol`      INT          DEFAULT NULL,
    `hdl_cholesterol`        INT          DEFAULT NULL,
    `triglyceride`           INT          DEFAULT NULL,
    `ldl_cholesterol`        INT          DEFAULT NULL,
    `lipid_result`           VARCHAR(60)  DEFAULT NULL,
    `serum_creatinine`       DECIMAL(4,1) DEFAULT NULL,
    `egfr`                   INT          DEFAULT NULL,
    `kidney_result`          VARCHAR(40)  DEFAULT NULL,
    `ast`                    INT          DEFAULT NULL,
    `alt`                    INT          DEFAULT NULL,
    `ggt`                    INT          DEFAULT NULL,
    `liver_result`           VARCHAR(40)  DEFAULT NULL,

    -- 요검사 / 영상검사
    `urine_protein_result`   VARCHAR(40)  DEFAULT NULL,
    `chest_xray_result`      VARCHAR(60)  DEFAULT NULL,

    -- 진찰(문진)
    `past_history`           VARCHAR(255) DEFAULT NULL,
    `medication`             VARCHAR(255) DEFAULT NULL,
    `need_smoking_cessation` TINYINT(1)   DEFAULT NULL,
    `need_drinking_reduction` TINYINT(1)  DEFAULT NULL,
    `need_physical_activity`  TINYINT(1)  DEFAULT NULL,
    `need_strength_exercise`  TINYINT(1)  DEFAULT NULL,

    -- 항목별 추가검사
    `hep_b_result`           VARCHAR(80)  DEFAULT NULL,
    `hep_c_result`           VARCHAR(80)  DEFAULT NULL,
    `depression_result`      VARCHAR(60)  DEFAULT NULL,
    `depression_score`       INT          DEFAULT NULL,
    `psychosis_result`       VARCHAR(60)  DEFAULT NULL,
    `cognitive_result`       VARCHAR(60)  DEFAULT NULL,
    `bone_density_result`    VARCHAR(60)  DEFAULT NULL,
    `urination_result`       VARCHAR(60)  DEFAULT NULL,

    -- 원본 보존
    `raw_text`               LONGTEXT     DEFAULT NULL,
    `source_file`            VARCHAR(255) DEFAULT NULL,
    `created_at`             DATETIME     DEFAULT NULL,
    `updated_at`             DATETIME     DEFAULT NULL,

    PRIMARY KEY (`id`),
    KEY `idx_health_checkup_user_date` (`user_id`, `checkup_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
