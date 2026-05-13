
-- riding_db 데이터베이스 구조 내보내기
CREATE DATABASE IF NOT EXISTS `riding_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci */;
USE `riding_db`;

-- 테이블 riding_db.activity 구조 내보내기
CREATE TABLE IF NOT EXISTS `activity` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activity_core_id` bigint(20) NOT NULL,
  `activity_date` date DEFAULT NULL,
  `activity_title` varchar(255) DEFAULT NULL,
  `activity_content` text DEFAULT NULL,
  `visibility` varchar(50) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_activity_core` (`activity_core_id`),
  CONSTRAINT `fk_activity_core` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.activity_comment 구조 내보내기
CREATE TABLE IF NOT EXISTS `activity_comment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activity_core_id` bigint(20) NOT NULL,
  `author` varchar(100) DEFAULT NULL,
  `comment` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_comment_core` (`activity_core_id`),
  CONSTRAINT `fk_comment_core` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.activity_core 구조 내보내기
CREATE TABLE IF NOT EXISTS `activity_core` (
  `id` bigint(20) NOT NULL,
  `filename` varchar(255) NOT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `total_distance` double DEFAULT NULL,
  `moving_time` int(11) DEFAULT NULL,
  `elapsed_time` int(11) DEFAULT NULL,
  `total_ascent` double DEFAULT NULL,
  `total_descent` double DEFAULT NULL,
  `avg_speed` double DEFAULT NULL,
  `max_speed` double DEFAULT NULL,
  `avg_cadence` double DEFAULT NULL,
  `avg_heart_rate` double DEFAULT NULL,
  `max_heart_rate` double DEFAULT NULL,
  `avg_power` double DEFAULT NULL,
  `max_power` double DEFAULT NULL,
  `calories` double DEFAULT NULL,
  `avg_power_weather_adj` double DEFAULT NULL,
  `total_work_weather_adj_kj` double DEFAULT NULL,
  `start_lat` double DEFAULT NULL,
  `start_lon` double DEFAULT NULL,
  `end_lat` double DEFAULT NULL,
  `end_lon` double DEFAULT NULL,
  `polyline` longtext DEFAULT NULL,
  `device_id` bigint(20) DEFAULT NULL,
  `source` varchar(30) DEFAULT NULL,
  `gear_name` varchar(100) DEFAULT NULL,
  `uphill_distance` double DEFAULT NULL,
  `flat_distance` double DEFAULT NULL,
  `down_distance` double DEFAULT NULL,
  `dem_status` varchar(20) DEFAULT 'PENDING',
  `dem_updated_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `has_power` tinyint(1) DEFAULT 0,
  `user_id` bigint(20) DEFAULT NULL,
  `strava_activity_id` bigint(20) DEFAULT NULL,
  `strava_synced_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_filename` (`filename`),
  KEY `idx_device_id` (`device_id`),
  CONSTRAINT `fk_core_device` FOREIGN KEY (`device_id`) REFERENCES `device_info` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.activity_image 구조 내보내기
CREATE TABLE IF NOT EXISTS `activity_image` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activity_core_id` bigint(20) NOT NULL,
  `image_path` varchar(500) DEFAULT NULL,
  `image_type` varchar(50) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_image_core` (`activity_core_id`),
  CONSTRAINT `fk_image_core` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.activity_point 구조 내보내기
CREATE TABLE IF NOT EXISTS `activity_point` (
  `activity_core_id` bigint(20) NOT NULL,
  `seq` int(11) NOT NULL,
  `point_time` datetime DEFAULT NULL,
  `lat` double DEFAULT NULL,
  `lon` double DEFAULT NULL,
  `altitude` double DEFAULT NULL,
  `altitude_dem` double DEFAULT NULL,
  `distance` double DEFAULT NULL,
  `speed` double DEFAULT NULL,
  `cadence` double DEFAULT NULL,
  `heart_rate` double DEFAULT NULL,
  `power` double DEFAULT NULL,
  `temperature` double DEFAULT NULL,
  `heading` double DEFAULT NULL,
  `slope` double DEFAULT NULL,
  `slope_dem` double DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  KEY `idx_point_core` (`activity_core_id`),
  KEY `idx_point_time` (`point_time`),
  CONSTRAINT `fk_point_core` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.activity_segment 구조 내보내기
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

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.activity_weather 구조 내보내기
CREATE TABLE IF NOT EXISTS `activity_weather` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activity_core_id` bigint(20) NOT NULL,
  `temperature` double DEFAULT NULL,
  `humidity` double DEFAULT NULL,
  `wind_speed` double DEFAULT NULL,
  `wind_deg` double DEFAULT NULL,
  `pressure` double DEFAULT NULL,
  `weather_main` varchar(50) DEFAULT NULL,
  `weather_desc` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_weather_core` (`activity_core_id`),
  CONSTRAINT `fk_weather_core` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.app_user 구조 내보내기
CREATE TABLE IF NOT EXISTS `app_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(100) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `nickname` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.body_analysis_lifetime_current 구조 내보내기
CREATE TABLE IF NOT EXISTS `body_analysis_lifetime_current` (
  `user_id` bigint(20) NOT NULL,
  `goal_type` varchar(20) NOT NULL,
  `snapshot_id` bigint(20) DEFAULT NULL,
  `model_provider` varchar(100) DEFAULT NULL,
  `model_name` varchar(100) DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`user_id`,`goal_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.body_analysis_result 구조 내보내기
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

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.body_analysis_summary_state 구조 내보내기
CREATE TABLE IF NOT EXISTS `body_analysis_summary_state` (
  `user_id` bigint(20) NOT NULL,
  `summary_json` longtext DEFAULT NULL,
  `version` varchar(20) DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.device_info 구조 내보내기
CREATE TABLE IF NOT EXISTS `device_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `manufacturer` varchar(100) DEFAULT NULL,
  `model` varchar(100) DEFAULT NULL,
  `serial_number` varchar(100) DEFAULT NULL,
  `firmware_version` varchar(50) DEFAULT NULL,
  `device_type` varchar(50) DEFAULT NULL,
  `user_label` varchar(100) DEFAULT NULL,
  `owner_user_id` bigint(20) DEFAULT NULL,
  `first_seen_at` datetime DEFAULT NULL,
  `last_seen_at` datetime DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_device` (`manufacturer`,`model`,`serial_number`),
  KEY `fk_device_owner` (`owner_user_id`),
  KEY `idx_device_serial` (`serial_number`),
  CONSTRAINT `fk_device_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.exercise_category 구조 내보내기
CREATE TABLE IF NOT EXISTS `exercise_category` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `description` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_exercise_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.exercise_item 구조 내보내기
CREATE TABLE IF NOT EXISTS `exercise_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `category_id` bigint(20) NOT NULL,
  `name` varchar(100) NOT NULL,
  `youtube_url` varchar(500) DEFAULT NULL,
  `gif_url` varchar(500) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `is_active` tinyint(1) DEFAULT 1,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_exercise_item` (`category_id`,`name`),
  CONSTRAINT `fk_exercise_item_category` FOREIGN KEY (`category_id`) REFERENCES `exercise_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.exercise_log 구조 내보내기
CREATE TABLE IF NOT EXISTS `exercise_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `session_id` bigint(20) NOT NULL,
  `exercise_item_id` bigint(20) NOT NULL,
  `weight` decimal(6,2) DEFAULT NULL,
  `reps` int(11) DEFAULT NULL,
  `sets` int(11) DEFAULT NULL,
  `duration_sec` int(11) DEFAULT NULL,
  `rpe` tinyint(4) DEFAULT NULL,
  `memo` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_exercise_log_session` (`session_id`),
  KEY `idx_exercise_log_item` (`exercise_item_id`),
  CONSTRAINT `fk_exercise_log_item` FOREIGN KEY (`exercise_item_id`) REFERENCES `exercise_item` (`id`),
  CONSTRAINT `fk_exercise_log_session` FOREIGN KEY (`session_id`) REFERENCES `exercise_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.exercise_media 구조 내보내기
CREATE TABLE IF NOT EXISTS `exercise_media` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `exercise_item_id` bigint(20) NOT NULL,
  `media_type` varchar(30) DEFAULT NULL,
  `media_url` varchar(500) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_exercise_media_item` (`exercise_item_id`),
  CONSTRAINT `fk_exercise_media_item` FOREIGN KEY (`exercise_item_id`) REFERENCES `exercise_item` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.exercise_muscle_map 구조 내보내기
CREATE TABLE IF NOT EXISTS `exercise_muscle_map` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `exercise_item_id` bigint(20) DEFAULT NULL,
  `muscle_group_id` bigint(20) DEFAULT NULL,
  `role` varchar(20) DEFAULT NULL,
  `activation_level` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.exercise_session 구조 내보내기
CREATE TABLE IF NOT EXISTS `exercise_session` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `session_date` date NOT NULL,
  `memo` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `is_pt` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_session_date` (`user_id`,`session_date`),
  CONSTRAINT `fk_exercise_session_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.file_date 구조 내보내기
CREATE TABLE IF NOT EXISTS `file_date` (
  `filename` varchar(255) NOT NULL,
  `processed_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`filename`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.segment 구조 내보내기
CREATE TABLE IF NOT EXISTS `segment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `start_lat` double DEFAULT NULL,
  `start_lon` double DEFAULT NULL,
  `end_lat` double DEFAULT NULL,
  `end_lon` double DEFAULT NULL,
  `start_radius_m` int(11) DEFAULT NULL,
  `end_radius_m` int(11) DEFAULT NULL,
  `match_tolerance_m` int(11) DEFAULT NULL,
  `min_match_percent` double DEFAULT NULL,
  `distance` double DEFAULT NULL,
  `elevation_gain` double DEFAULT NULL,
  `elevation_loss` double DEFAULT NULL,
  `avg_grade` double DEFAULT NULL,
  `max_grade` double DEFAULT NULL,
  `direction_degrees` double DEFAULT NULL,
  `bbox_min_lat` double DEFAULT NULL,
  `bbox_max_lat` double DEFAULT NULL,
  `bbox_min_lon` double DEFAULT NULL,
  `bbox_max_lon` double DEFAULT NULL,
  `polyline` text DEFAULT NULL,
  `is_active` tinyint(1) DEFAULT 1,
  `strava_segment_id` bigint(20) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=75 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.segment_effort 구조 내보내기
CREATE TABLE IF NOT EXISTS `segment_effort` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `segment_id` bigint(20) DEFAULT NULL,
  `activity_core_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `elapsed_time_sec` int(11) DEFAULT NULL,
  `moving_time_sec` int(11) DEFAULT NULL,
  `match_percent` double DEFAULT NULL,
  `start_distance_m` double DEFAULT NULL,
  `end_distance_m` double DEFAULT NULL,
  `avg_speed` double DEFAULT NULL,
  `max_speed` double DEFAULT NULL,
  `avg_power` double DEFAULT NULL,
  `max_power` double DEFAULT NULL,
  `avg_heart_rate` int(11) DEFAULT NULL,
  `max_heart_rate` int(11) DEFAULT NULL,
  `avg_cadence` int(11) DEFAULT NULL,
  `start_point_seq` int(11) DEFAULT NULL,
  `end_point_seq` int(11) DEFAULT NULL,
  `pr_rank` int(11) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=75 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.user_body_record 구조 내보내기
CREATE TABLE IF NOT EXISTS `user_body_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `measurement_type` varchar(20) NOT NULL DEFAULT 'INBODY',
  `record_date` date NOT NULL,
  `filename` varchar(255) NOT NULL,
  `weight` decimal(5,2) DEFAULT NULL,
  `skeletal_muscle_mass` decimal(5,2) DEFAULT NULL,
  `muscle_mass` decimal(5,2) DEFAULT NULL,
  `body_fat_mass` decimal(5,2) DEFAULT NULL,
  `body_fat_percentage` decimal(5,2) DEFAULT NULL,
  `bmi` decimal(5,2) DEFAULT NULL,
  `visceral_fat_level` int(11) DEFAULT NULL,
  `ecw_tbw_ratio` decimal(5,3) DEFAULT NULL,
  `extracellular_water` decimal(5,2) DEFAULT NULL,
  `total_body_water` decimal(5,2) DEFAULT NULL,
  `raw_llm_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`raw_llm_json`)),
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  `waist_circumference` decimal(5,2) DEFAULT NULL,
  `thigh_circumference` decimal(5,2) DEFAULT NULL,
  `chest_circumference` decimal(5,2) DEFAULT NULL,
  `protein` decimal(5,2) DEFAULT NULL,
  `mineral` decimal(5,2) DEFAULT NULL,
  `bone_mass` decimal(5,2) DEFAULT NULL,
  `basal_metabolic_rate` decimal(7,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_file` (`user_id`,`filename`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.user_health_check 구조 내보내기
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

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 riding_db.user_profile 구조 내보내기
CREATE TABLE IF NOT EXISTS `user_profile` (
  `user_id` bigint(20) NOT NULL,
  `gender` enum('M','F') NOT NULL COMMENT 'M: 남성, F: 여성',
  `birth_date` date NOT NULL COMMENT '생년월일',
  `height_cm` decimal(5,2) DEFAULT NULL COMMENT '키 (cm)',
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='사용자 신체 기준 정보 (고정값)';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 뷰 riding_db.v_user_body_stats 구조 내보내기
-- VIEW 종속성 오류를 극복하기 위해 임시 테이블을 생성합니다.
CREATE TABLE `v_user_body_stats` (
	`user_id` BIGINT(20) NULL,
	`record_date` DATE NULL,
	`weight` DECIMAL(5,2) NULL,
	`skeletal_muscle_mass` DECIMAL(5,2) NULL,
	`body_fat_percentage` DECIMAL(5,2) NULL,
	`ecw_tbw_ratio` DECIMAL(5,3) NULL,
	`weight_ma` DECIMAL(9,6) NULL,
	`body_fat_ma` DECIMAL(9,6) NULL,
	`weight_delta` DECIMAL(6,2) NULL,
	`body_fat_delta` DECIMAL(6,2) NULL
) ENGINE=MyISAM;

-- 임시 테이블을 제거하고 최종 VIEW 구조를 생성
DROP TABLE IF EXISTS `v_user_body_stats`;
CREATE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `v_user_body_stats` AS select `ubr`.`user_id` AS `user_id`,`ubr`.`record_date` AS `record_date`,`ubr`.`weight` AS `weight`,`ubr`.`skeletal_muscle_mass` AS `skeletal_muscle_mass`,`ubr`.`body_fat_percentage` AS `body_fat_percentage`,`ubr`.`ecw_tbw_ratio` AS `ecw_tbw_ratio`,avg(`ubr`.`weight`) over ( partition by `ubr`.`user_id` order by `ubr`.`record_date` rows between 3 preceding  and  current row ) AS `weight_ma`,avg(`ubr`.`body_fat_percentage`) over ( partition by `ubr`.`user_id` order by `ubr`.`record_date` rows between 3 preceding  and  current row ) AS `body_fat_ma`,`ubr`.`weight` - lag(`ubr`.`weight`,1) over ( partition by `ubr`.`user_id` order by `ubr`.`record_date`) AS `weight_delta`,`ubr`.`body_fat_percentage` - lag(`ubr`.`body_fat_percentage`,1) over ( partition by `ubr`.`user_id` order by `ubr`.`record_date`) AS `body_fat_delta` from `user_body_record` `ubr`
;

/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
