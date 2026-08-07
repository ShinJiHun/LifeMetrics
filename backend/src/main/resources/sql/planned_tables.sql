-- 미래 작업용 테이블 정의 (prod 미적용)
--
-- prod riding_db 에는 존재하지 않는 테이블들이다. 설계만 해 두고 아직 만들지 않았다.
-- create.sql 은 prod 덤프로 교체되면서 이 정의들이 빠졌기에, 설계를 잃지 않도록
-- 여기로 분리해 보관한다.
--
-- create.sql = prod 의 정확한 거울 (mariadb-dump 결과, 수기 편집 금지)
-- 이 파일   = 아직 만들지 않은 것들 (수기 관리)
--
-- 실제로 기능을 붙일 때 해당 블록만 prod 에 실행하고, 그 다음 create.sql 을
-- 다시 덤프해서 갱신할 것. 그러면 자연스럽게 이 파일에서 빠진다.
--
-- ※ 아래 테이블들은 현재 HTTP 로 도달 불가능하므로 없어도 런타임 오류가 없다.
--   ddl-auto=none 이라 기동 시 스키마 검증도 하지 않는다.


-- ═══════════════════════════════════════════════════════════════
-- 1. 지도/세그먼트 (백엔드 참조 없음 — 지도 작업 시 개발 예정)
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS `activity_segment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activity_core_id` bigint(20) NOT NULL,
  `segment_id` bigint(20) NOT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `elapsed_time` int(11) DEFAULT NULL,
  `avg_speed` double DEFAULT NULL,
  `max_speed` double DEFAULT NULL,
  `avg_cadence` double DEFAULT NULL,
  `avg_heart_rate` double DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_as_core` (`activity_core_id`),
  KEY `fk_as_segment` (`segment_id`),
  CONSTRAINT `fk_as_core` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_as_segment` FOREIGN KEY (`segment_id`) REFERENCES `segment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;


-- ═══════════════════════════════════════════════════════════════
-- 2. 체성분 AI 분석 (엔티티·서비스는 있으나 컨트롤러 미연결)
--
--    BodyAnalysisResult / BodyAnalysisLifetimeCurrent / BodyAnalysisSummaryState
--    + BodyAnalysisServiceImpl, BodyAnalysisPromptServiceImpl
--
--    실제 인바디 데이터는 user_body_record 에 있고(prod 존재), BodyController
--    (/api/body/*) 는 BodyService → user_body_record 만 사용한다.
--    아래는 그 위에 얹을 AI 분석 결과 저장용 골격이다.
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS `body_analysis_result` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `body_record_id` bigint(20) NOT NULL,
  `goal_type` varchar(20) NOT NULL,
  `model_provider` varchar(100) NOT NULL,
  `model_name` varchar(100) NOT NULL,
  `body_type` varchar(50) NOT NULL,
  `analysis_version` varchar(20) DEFAULT 'v1',
  `analysis_json` longtext DEFAULT NULL,
  `analysis_text` text DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `body_analysis_lifetime_current` (
  `user_id` bigint(20) NOT NULL,
  `goal_type` varchar(20) NOT NULL,
  `snapshot_id` bigint(20) DEFAULT NULL,
  `model_provider` varchar(100) DEFAULT NULL,
  `model_name` varchar(100) DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`user_id`,`goal_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `body_analysis_summary_state` (
  `user_id` bigint(20) NOT NULL,
  `summary_json` longtext DEFAULT NULL,
  `version` varchar(20) DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;


-- ═══════════════════════════════════════════════════════════════
-- 3. 건강검진 기록 (백엔드 참조 없음)
--    app_user FK 가 있으므로 app_user 생성 이후에 실행할 것.
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS `user_health_check` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `measure_date` date NOT NULL,
  `systolic_bp` int(11) DEFAULT NULL,
  `diastolic_bp` int(11) DEFAULT NULL,
  `heart_rate` int(11) DEFAULT NULL,
  `oxygen_saturation` decimal(5,2) DEFAULT NULL,
  `source` enum('manual','hospital','device') DEFAULT NULL COMMENT 'manual, hospital, device',
  `note` varchar(255) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`,`measure_date`),
  CONSTRAINT `fk_user_health_check_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;