-- Health Connect(삼성헬스) 동기화 테이블 — riding_db
-- 폰 앱이 하루 1번 POST /api/health-sync 로 올린다. 같은 레코드를 다시 보내도 덮어쓰도록 hc_record_id 로 upsert.

-- 1) 식단: 기존 food_log 재사용 (data_source = 'health_connect')
ALTER TABLE food_log
    ADD COLUMN IF NOT EXISTS hc_record_id VARCHAR(64) NULL COMMENT 'Health Connect NutritionRecord id' AFTER data_source,
    ADD UNIQUE INDEX IF NOT EXISTS uk_food_log_hc_record (hc_record_id);

-- 2) 일별 활동량 (Health Connect aggregate 값: 여러 앱 기록의 중복 제거가 끝난 수치)
CREATE TABLE IF NOT EXISTS hc_daily_activity (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT  NOT NULL DEFAULT 1,
    activity_date      DATE    NOT NULL COMMENT 'KST 기준 날짜',
    steps              BIGINT  NULL,
    distance_m         DOUBLE  NULL,
    active_kcal        DOUBLE  NULL,
    total_kcal         DOUBLE  NULL,
    exercise_minutes   INT     NULL,
    sleep_minutes      INT     NULL,
    resting_heart_rate INT     NULL,
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_hc_daily_user_date (user_id, activity_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Health Connect 일별 활동량';

-- 3) 운동 세션
CREATE TABLE IF NOT EXISTS hc_exercise_session (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL DEFAULT 1,
    hc_record_id   VARCHAR(64)  NOT NULL,
    exercise_type  VARCHAR(50)  NOT NULL COMMENT 'Health Connect ExerciseSessionRecord 타입명 (BIKING, WALKING ...)',
    title          VARCHAR(200) NULL,
    start_time     DATETIME     NOT NULL COMMENT 'KST',
    end_time       DATETIME     NOT NULL COMMENT 'KST',
    duration_min   INT          NOT NULL,
    active_kcal    DOUBLE       NULL,
    distance_m     DOUBLE       NULL,
    avg_heart_rate INT          NULL,
    source_app     VARCHAR(200) NULL,
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_hc_exercise_record (hc_record_id),
    INDEX idx_hc_exercise_user_start (user_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Health Connect 운동 세션';

-- 4) 동기화 이력 (마지막 동기화 확인용)
CREATE TABLE IF NOT EXISTS hc_sync_log (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT      NOT NULL DEFAULT 1,
    device           VARCHAR(100) NULL,
    synced_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    day_count        INT NOT NULL DEFAULT 0,
    nutrition_count  INT NOT NULL DEFAULT 0,
    exercise_count   INT NOT NULL DEFAULT 0,
    deleted_count    INT NOT NULL DEFAULT 0,
    INDEX idx_hc_sync_user_time (user_id, synced_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Health Connect 동기화 이력';
