-- riding_db 스키마 (구조만, 데이터 없음)
-- 출처: prod (34.172.162.148 / riding-mariadb 컨테이너), mariadb-dump --no-data
-- 생성일: 2026-07-18
-- 주의: 수기로 편집하지 말 것. prod에서 다시 덤프해 갱신할 것.

/*M!999999\- enable the sandbox mode */ 
-- MariaDB dump 10.19-11.6.2-MariaDB, for debian-linux-gnu (x86_64)
--
-- Host: localhost    Database: riding_db
-- ------------------------------------------------------
-- Server version	11.6.2-MariaDB-ubu2404
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*M!100616 SET @OLD_NOTE_VERBOSITY=@@NOTE_VERBOSITY, NOTE_VERBOSITY=0 */;

--
-- Table structure for table `activity`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `activity` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activity_core_id` bigint(20) NOT NULL,
  `activity_date` date DEFAULT NULL,
  `activity_title` varchar(255) DEFAULT NULL,
  `activity_content` text DEFAULT NULL,
  `visibility` varchar(50) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `has_power` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `activity_core_id` (`activity_core_id`),
  CONSTRAINT `activity_ibfk_1` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `activity_comment`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `activity_comment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activity_core_id` bigint(20) NOT NULL,
  `author` varchar(100) DEFAULT NULL,
  `comment` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `activity_core_id` (`activity_core_id`),
  CONSTRAINT `activity_comment_ibfk_1` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `activity_core`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  `uphill_distance` double DEFAULT NULL,
  `flat_distance` double DEFAULT NULL,
  `down_distance` double DEFAULT NULL,
  `avg_power_weather_adj` double DEFAULT NULL,
  `total_work_weather_adj_kj` double DEFAULT NULL,
  `start_lat` double DEFAULT NULL,
  `start_lon` double DEFAULT NULL,
  `end_lat` double DEFAULT NULL,
  `end_lon` double DEFAULT NULL,
  `polyline` longtext DEFAULT NULL,
  `device_id` bigint(20) DEFAULT NULL,
  `source` varchar(30) DEFAULT NULL,
  `dem_status` varchar(20) DEFAULT 'PENDING',
  `dem_updated_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `user_id` bigint(20) DEFAULT 1,
  `strava_activity_id` bigint(20) DEFAULT NULL,
  `strava_synced_at` datetime DEFAULT NULL,
  `normalized_power` double DEFAULT NULL,
  `tss` double DEFAULT NULL,
  `intensity_factor` double DEFAULT NULL,
  `left_right_balance` double DEFAULT NULL,
  `has_power` tinyint(1) NOT NULL DEFAULT 0,
  `power_source` varchar(20) DEFAULT NULL,
  `strava_match_status` varchar(20) DEFAULT NULL COMMENT 'NULL=미시도, MATCHED=매칭됨, NO_MATCH=Strava에 없음',
  `name` varchar(255) DEFAULT NULL COMMENT '라이드 제목',
  `location_name` varchar(100) DEFAULT NULL COMMENT '출발지명',
  `relative_effort` int(11) DEFAULT NULL COMMENT 'TRIMP 기반 RE 점수',
  `max_cadence` double DEFAULT NULL COMMENT '최대 케이던스 rpm',
  `bike_id` bigint(20) DEFAULT NULL COMMENT '사용 자전거 (자동매칭)',
  `course_id` bigint(20) DEFAULT NULL COMMENT '연결된 코스, 자유 라이딩은 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `filename` (`filename`),
  UNIQUE KEY `idx_strava_activity_id` (`strava_activity_id`),
  KEY `device_id` (`device_id`),
  KEY `fk_activity_bike` (`bike_id`),
  KEY `idx_ac_user_time` (`user_id`,`start_time`),
  KEY `fk_activity_course` (`course_id`),
  CONSTRAINT `activity_core_ibfk_1` FOREIGN KEY (`device_id`) REFERENCES `device_info` (`id`),
  CONSTRAINT `fk_activity_bike` FOREIGN KEY (`bike_id`) REFERENCES `bike` (`id`),
  CONSTRAINT `fk_activity_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_uca1400_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`tho881`@`172.18.0.1`*/ /*!50003 TRIGGER trg_activity_core_bike_by_purchase
BEFORE INSERT ON activity_core
FOR EACH ROW
BEGIN
    IF NEW.bike_id IS NULL THEN
        SET NEW.bike_id = (
            SELECT b.id
              FROM bike b
             WHERE b.purchase_date IS NOT NULL
               AND b.purchase_date <= NEW.start_time
               AND b.is_retired = 0            -- 은퇴 자전거 제외(원치 않으면 삭제)
             ORDER BY b.purchase_date DESC
             LIMIT 1
        );
    END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `activity_image`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `activity_image` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activity_core_id` bigint(20) NOT NULL,
  `image_path` varchar(500) DEFAULT NULL,
  `image_type` varchar(50) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `activity_core_id` (`activity_core_id`),
  CONSTRAINT `activity_image_ibfk_1` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `activity_point`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  `left_power` double DEFAULT NULL,
  `right_power` double DEFAULT NULL,
  `left_right_balance` double DEFAULT NULL,
  PRIMARY KEY (`activity_core_id`,`seq`),
  CONSTRAINT `activity_point_ibfk_1` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `activity_weather`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  UNIQUE KEY `uq_weather_core` (`activity_core_id`),
  KEY `idx_aw_core` (`activity_core_id`),
  CONSTRAINT `activity_weather_ibfk_1` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=216 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `activity_weather_point`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `activity_weather_point` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activity_core_id` bigint(20) NOT NULL,
  `seq` int(11) NOT NULL,
  `point_time` datetime NOT NULL,
  `lat` double NOT NULL,
  `lon` double NOT NULL,
  `temperature` double DEFAULT NULL,
  `humidity` double DEFAULT NULL,
  `wind_speed` double DEFAULT NULL,
  `wind_deg` double DEFAULT NULL,
  `pressure` double DEFAULT NULL,
  `weather_code` int(11) DEFAULT NULL,
  `weather_main` varchar(50) DEFAULT NULL,
  `weather_desc` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_awp_core` (`activity_core_id`),
  CONSTRAINT `fk_awp_core` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3014 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_analysis`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `ai_analysis` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `analysis_type` varchar(50) NOT NULL,
  `target_id` bigint(20) NOT NULL,
  `target_period` varchar(20) DEFAULT NULL,
  `analysis_data` text NOT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_type_target` (`user_id`,`analysis_type`,`target_id`,`target_period`),
  KEY `idx_user_type` (`user_id`,`analysis_type`)
) ENGINE=InnoDB AUTO_INCREMENT=226 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `app_user`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `app_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(100) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `bike`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `bike` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '별칭',
  `brand` varchar(50) DEFAULT NULL,
  `model` varchar(100) DEFAULT NULL,
  `model_year` int(11) DEFAULT NULL,
  `bike_type` varchar(30) DEFAULT NULL COMMENT 'ROAD/GRAVEL/MTB/TT/CX',
  `ride_category` varchar(20) DEFAULT NULL COMMENT 'ENDURANCE/RACING/AERO/ALLROAD',
  `frame_size` varchar(20) DEFAULT NULL,
  `frame_material` varchar(30) DEFAULT NULL COMMENT '카본/알루미늄/티타늄/스틸',
  `weight_kg` decimal(4,2) DEFAULT NULL,
  `wheel_size` varchar(20) DEFAULT NULL,
  `groupset` varchar(50) DEFAULT NULL COMMENT '예: Shimano Ultegra Di2 R8170',
  `shifting_type` varchar(20) DEFAULT NULL COMMENT 'DI2/AXS/EPS/MECHANICAL',
  `speeds` int(11) DEFAULT NULL,
  `chainring_outer` int(11) DEFAULT NULL COMMENT '바깥(큰) 체인링 T — 자동매칭용',
  `chainring_inner` int(11) DEFAULT NULL COMMENT '안쪽(작은) 체인링 T — 1x면 outer와 동일',
  `cassette_label` varchar(30) DEFAULT NULL COMMENT '카세트 표기 (예: 11-30T) — 표시용, 선택',
  `purchase_date` date DEFAULT NULL,
  `purchase_price` int(11) DEFAULT NULL,
  `total_distance` double NOT NULL DEFAULT 0 COMMENT '누적거리 (activity_core 단위 일치)',
  `total_time` int(11) NOT NULL DEFAULT 0 COMMENT '누적시간(초)',
  `photo_url` varchar(255) DEFAULT NULL,
  `is_retired` tinyint(1) NOT NULL DEFAULT 0,
  `is_default` tinyint(1) NOT NULL DEFAULT 0,
  `notes` text DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `gear_name` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `bike_component`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `bike_component` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `bike_id` bigint(20) NOT NULL,
  `component_type` varchar(30) NOT NULL COMMENT 'CHAIN/CASSETTE/CHAINRING/TIRE_FRONT/TIRE_REAR/BRAKE_PAD/BB/WHEELSET/...',
  `brand` varchar(50) DEFAULT NULL,
  `model` varchar(100) DEFAULT NULL COMMENT '제품 라벨 (표시/식별용, 계산 아님)',
  `install_date` date NOT NULL COMMENT '장착일 = 마모 귀속 윈도우 시작',
  `install_distance` double NOT NULL DEFAULT 0 COMMENT '장착 시점 자전거 누적거리',
  `removed_date` date DEFAULT NULL COMMENT '탈거/교체일 (NULL=현재 장착, 윈도우 끝)',
  `replace_threshold` double DEFAULT NULL COMMENT '교체 권장 사용거리 (km 보조 알림, 선택)',
  `price` int(11) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `notes` text DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_bike_active` (`bike_id`,`is_active`),
  KEY `idx_bike_window` (`bike_id`,`component_type`,`install_date`,`removed_date`),
  CONSTRAINT `fk_component_bike` FOREIGN KEY (`bike_id`) REFERENCES `bike` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `blog_post`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `blog_post` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `url` varchar(512) NOT NULL,
  `post_no` int(11) DEFAULT NULL,
  `title` varchar(512) DEFAULT NULL,
  `content` longtext DEFAULT NULL,
  `author` varchar(100) DEFAULT NULL,
  `categories` varchar(512) DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `crawled_at` datetime DEFAULT NULL,
  `is_private` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_blog_post_url` (`url`),
  KEY `idx_blog_post_private` (`is_private`)
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `chain_measurement`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `chain_measurement` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `component_id` bigint(20) NOT NULL COMMENT 'bike_component(체인) id',
  `measure_date` date NOT NULL,
  `bike_distance_at_measure` double NOT NULL COMMENT '측정 시점 자전거 누적거리',
  `elongation_pct` double NOT NULL COMMENT '신율 % (0.25/0.5/0.75...)',
  `tool` varchar(50) DEFAULT NULL,
  `notes` text DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_cm_component` (`component_id`,`measure_date`),
  CONSTRAINT `fk_cm_component` FOREIGN KEY (`component_id`) REFERENCES `bike_component` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `course` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(32) DEFAULT NULL COMMENT '란도너스 코드, PT-193 등. TOURING은 NULL',
  `name` varchar(128) NOT NULL COMMENT '코스명',
  `type` varchar(16) NOT NULL COMMENT 'PERMANENT / BREVET / TOURING',
  `distance_official` decimal(6,1) DEFAULT NULL COMMENT '공식 거리(km)',
  `elevation_official` int(11) DEFAULT NULL COMMENT '공식 획득고도(m)',
  `gpx_path` varchar(255) DEFAULT NULL COMMENT '기준 GPX 파일 경로',
  `tcx_path` varchar(255) DEFAULT NULL COMMENT '기준 TCX 파일 경로(선택)',
  `region` varchar(64) DEFAULT NULL,
  `source_url` varchar(512) DEFAULT NULL COMMENT '란도너스 코스 페이지 URL',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `device_component`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `device_component` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `device_id` bigint(20) NOT NULL,
  `component_type` varchar(32) NOT NULL,
  `spec` varchar(64) NOT NULL,
  `effective_from` date NOT NULL,
  `effective_to` date DEFAULT NULL,
  `note` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_device_component_lookup` (`device_id`,`component_type`,`effective_from`),
  KEY `idx_dc_device` (`device_id`),
  CONSTRAINT `fk_device_component_device` FOREIGN KEY (`device_id`) REFERENCES `device_info` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `device_info`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `device_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `manufacturer` varchar(100) DEFAULT NULL,
  `model` varchar(100) DEFAULT NULL,
  `serial_number` varchar(100) DEFAULT NULL,
  `firmware_version` varchar(50) DEFAULT NULL,
  `device_type` enum('HEAD_UNIT','SPEED_SENSOR','CADENCE_SENSOR','HEART_RATE','POWER_METER','BIKE') NOT NULL,
  `user_label` varchar(100) DEFAULT NULL,
  `owner_user_id` bigint(20) DEFAULT NULL,
  `first_seen_at` datetime DEFAULT NULL,
  `last_seen_at` datetime DEFAULT NULL,
  `purchased_at` date DEFAULT NULL COMMENT '기기 구매일',
  `is_active` tinyint(1) DEFAULT 1,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_device` (`manufacturer`,`model`,`serial_number`),
  KEY `idx_di_owner` (`owner_user_id`,`device_type`,`is_active`),
  CONSTRAINT `device_info_ibfk_1` FOREIGN KEY (`owner_user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exercise_category`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `exercise_category` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exercise_item`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `exercise_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `category_id` bigint(20) NOT NULL,
  `name` varchar(100) DEFAULT NULL,
  `youtube_url` varchar(500) DEFAULT NULL,
  `gif_url` varchar(500) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `is_active` tinyint(1) DEFAULT 1,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `equipment_type` varchar(50) DEFAULT NULL,
  `media_url` varchar(500) DEFAULT NULL,
  `name_ko` varchar(100) DEFAULT NULL,
  `name_en` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_exercise_item` (`category_id`,`name`),
  CONSTRAINT `exercise_item_ibfk_1` FOREIGN KEY (`category_id`) REFERENCES `exercise_category` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=856 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exercise_log`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  KEY `exercise_item_id` (`exercise_item_id`),
  KEY `idx_log_session` (`session_id`),
  CONSTRAINT `exercise_log_ibfk_1` FOREIGN KEY (`session_id`) REFERENCES `exercise_session` (`id`) ON DELETE CASCADE,
  CONSTRAINT `exercise_log_ibfk_2` FOREIGN KEY (`exercise_item_id`) REFERENCES `exercise_item` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=701 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exercise_media`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `exercise_media` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `exercise_item_id` bigint(20) NOT NULL,
  `media_type` varchar(30) DEFAULT NULL,
  `media_url` varchar(500) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `exercise_item_id` (`exercise_item_id`),
  CONSTRAINT `exercise_media_ibfk_1` FOREIGN KEY (`exercise_item_id`) REFERENCES `exercise_item` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exercise_muscle_map`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `exercise_muscle_map` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `exercise_item_id` bigint(20) NOT NULL COMMENT 'exercise_item FK',
  `muscle_group_id` bigint(20) NOT NULL COMMENT 'muscle_group FK',
  `role` enum('PRIMARY','SECONDARY','SYNERGIST') NOT NULL DEFAULT 'PRIMARY' COMMENT 'PRIMARY=주동근, SECONDARY=보조근, SYNERGIST=협력근',
  `activation_level` tinyint(4) DEFAULT 100 COMMENT '근육 활성도 (0~100)',
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exercise_muscle` (`exercise_item_id`,`muscle_group_id`,`role`),
  KEY `fk_emm_muscle_group` (`muscle_group_id`),
  CONSTRAINT `fk_emm_exercise_item` FOREIGN KEY (`exercise_item_id`) REFERENCES `exercise_item` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_emm_muscle_group` FOREIGN KEY (`muscle_group_id`) REFERENCES `muscle_group` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1997 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='운동-근육 매핑';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exercise_session`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `exercise_session` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `session_date` date DEFAULT NULL,
  `memo` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `is_pt` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_session` (`user_id`,`session_date`),
  KEY `idx_session_user_date` (`user_id`,`session_date`),
  CONSTRAINT `exercise_session_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=155 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exercise_set`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `exercise_set` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `log_id` bigint(20) NOT NULL,
  `set_number` int(11) NOT NULL,
  `weight` decimal(6,2) DEFAULT NULL,
  `reps` int(11) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_set_log` (`log_id`),
  CONSTRAINT `exercise_set_ibfk_1` FOREIGN KEY (`log_id`) REFERENCES `exercise_log` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1126 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `file_date`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `file_date` (
  `filename` varchar(255) NOT NULL,
  `processed_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`filename`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `food_daily_summary`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `food_daily_summary` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL DEFAULT 1,
  `log_date` date NOT NULL,
  `total_calories` double DEFAULT 0,
  `total_carbs` double DEFAULT 0,
  `total_protein` double DEFAULT 0,
  `total_fat` double DEFAULT 0,
  `total_sodium` double DEFAULT NULL,
  `total_sugar` double DEFAULT NULL,
  `total_dietary_fiber` double DEFAULT NULL,
  `meal_count` int(11) DEFAULT 0 COMMENT '식사 횟수',
  `data_source` varchar(50) DEFAULT 'google_fit',
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`log_date`),
  KEY `idx_log_date` (`log_date`)
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='일일 영양 섭취 요약';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `food_log`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `food_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL DEFAULT 1,
  `log_date` date NOT NULL,
  `meal_type` varchar(20) NOT NULL DEFAULT 'unknown' COMMENT 'breakfast, lunch, dinner, snack, unknown',
  `food_name` varchar(300) DEFAULT NULL,
  `calories` double DEFAULT 0,
  `carbs` double DEFAULT 0 COMMENT '탄수화물(g)',
  `protein` double DEFAULT 0 COMMENT '단백질(g)',
  `fat` double DEFAULT 0 COMMENT '지방(g)',
  `saturated_fat` double DEFAULT NULL,
  `unsaturated_fat` double DEFAULT NULL,
  `polyunsaturated_fat` double DEFAULT NULL,
  `monounsaturated_fat` double DEFAULT NULL,
  `trans_fat` double DEFAULT NULL,
  `cholesterol` double DEFAULT NULL COMMENT 'mg',
  `sodium` double DEFAULT NULL COMMENT 'mg',
  `potassium` double DEFAULT NULL COMMENT 'mg',
  `dietary_fiber` double DEFAULT NULL COMMENT 'g',
  `sugar` double DEFAULT NULL COMMENT 'g',
  `vitamin_a` double DEFAULT NULL,
  `vitamin_c` double DEFAULT NULL,
  `calcium` double DEFAULT NULL COMMENT 'mg',
  `iron` double DEFAULT NULL COMMENT 'mg',
  `eat_time` datetime DEFAULT NULL COMMENT '실제 섭취 시간',
  `data_source` varchar(200) DEFAULT NULL COMMENT '데이터 출처 (google_fit, manual 등)',
  `memo` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`,`log_date`),
  KEY `idx_log_date` (`log_date`),
  KEY `idx_meal_type` (`meal_type`)
) ENGINE=InnoDB AUTO_INCREMENT=305 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='일일 식단 기록';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gear_usage`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `gear_usage` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activity_core_id` bigint(20) NOT NULL,
  `front_gear` int(11) NOT NULL COMMENT 'FIT 관측 앞 체인링 T',
  `rear_gear` int(11) NOT NULL COMMENT 'FIT 관측 뒤 코그 T',
  `gear_ratio` double DEFAULT NULL,
  `terrain` varchar(10) NOT NULL COMMENT 'UPHILL/FLAT/DOWNHILL',
  `duration_sec` double NOT NULL DEFAULT 0,
  `distance` double NOT NULL DEFAULT 0,
  `avg_power` double DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gu` (`activity_core_id`,`front_gear`,`rear_gear`,`terrain`),
  KEY `idx_gu_activity` (`activity_core_id`),
  CONSTRAINT `fk_gu_activity` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `muscle_group`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `muscle_group` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name_ko` varchar(50) NOT NULL COMMENT '한글명',
  `name_en` varchar(50) NOT NULL COMMENT '영문명',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '상위 근육 그룹 (NULL이면 대분류)',
  `body_part` enum('UPPER','LOWER','CORE') NOT NULL COMMENT '상체/하체/코어',
  `sort_order` int(11) DEFAULT 0 COMMENT '정렬 순서',
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_muscle_group_name_en` (`name_en`),
  KEY `fk_muscle_group_parent` (`parent_id`),
  CONSTRAINT `fk_muscle_group_parent` FOREIGN KEY (`parent_id`) REFERENCES `muscle_group` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=1105 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='근육 그룹 마스터';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `persona_profile`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `persona_profile` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `profile_text` longtext NOT NULL,
  `post_count` int(11) DEFAULT NULL,
  `generated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `segment`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `segment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `description` text DEFAULT NULL,
  `start_lat` double NOT NULL,
  `start_lon` double NOT NULL,
  `end_lat` double NOT NULL,
  `end_lon` double NOT NULL,
  `start_radius_m` int(11) DEFAULT 30,
  `end_radius_m` int(11) DEFAULT 30,
  `match_tolerance_m` int(11) DEFAULT 50,
  `min_match_percent` decimal(5,2) DEFAULT 80.00,
  `distance` double NOT NULL,
  `elevation_gain` double DEFAULT 0,
  `elevation_loss` double DEFAULT 0,
  `avg_grade` decimal(5,2) DEFAULT NULL,
  `max_grade` decimal(5,2) DEFAULT NULL,
  `direction_degrees` double DEFAULT NULL,
  `bbox_min_lat` double DEFAULT NULL,
  `bbox_max_lat` double DEFAULT NULL,
  `bbox_min_lon` double DEFAULT NULL,
  `bbox_max_lon` double DEFAULT NULL,
  `polyline` text NOT NULL,
  `is_active` tinyint(1) DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `strava_segment_id` bigint(20) DEFAULT NULL,
  `detail_fetched_at` datetime DEFAULT NULL COMMENT '세그먼트 상세(polyline 등) 조회 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_strava_segment_id` (`strava_segment_id`),
  KEY `idx_bbox` (`bbox_min_lat`,`bbox_max_lat`,`bbox_min_lon`,`bbox_max_lon`),
  KEY `idx_start` (`start_lat`,`start_lon`),
  KEY `idx_active` (`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=5537 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `segment_effort`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `segment_effort` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `segment_id` bigint(20) NOT NULL,
  `activity_core_id` bigint(20) NOT NULL,
  `start_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `end_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `elapsed_time_sec` int(11) NOT NULL,
  `moving_time_sec` int(11) DEFAULT NULL,
  `match_percent` decimal(5,2) DEFAULT NULL,
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
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_segment_time` (`segment_id`,`elapsed_time_sec`),
  KEY `idx_activity` (`activity_core_id`),
  KEY `idx_pr` (`segment_id`,`pr_rank`),
  KEY `idx_segment_effort_user_id` (`user_id`),
  CONSTRAINT `segment_effort_ibfk_1` FOREIGN KEY (`segment_id`) REFERENCES `segment` (`id`),
  CONSTRAINT `segment_effort_ibfk_2` FOREIGN KEY (`activity_core_id`) REFERENCES `activity_core` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=10387 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Temporary table structure for view `segment_leaderboard`
--

SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
/*!50001 CREATE VIEW `segment_leaderboard` AS SELECT
 1 AS `segment_id`,
  1 AS `activity_core_id`,
  1 AS `elapsed_time_sec`,
  1 AS `avg_speed`,
  1 AS `avg_power`,
  1 AS `start_time`,
  1 AS `rank` */;
SET character_set_client = @saved_cs_client;

--
-- Table structure for table `segment_point`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `segment_point` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `segment_id` bigint(20) NOT NULL,
  `seq` int(11) NOT NULL,
  `lat` double NOT NULL,
  `lon` double NOT NULL,
  `altitude` double DEFAULT NULL,
  `distance_from_start` double DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_segment_seq` (`segment_id`,`seq`),
  CONSTRAINT `segment_point_ibfk_1` FOREIGN KEY (`segment_id`) REFERENCES `segment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=196 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Temporary table structure for view `segment_pr`
--

SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
/*!50001 CREATE VIEW `segment_pr` AS SELECT
 1 AS `id`,
  1 AS `segment_id`,
  1 AS `activity_core_id`,
  1 AS `start_time`,
  1 AS `end_time`,
  1 AS `elapsed_time_sec`,
  1 AS `moving_time_sec`,
  1 AS `match_percent`,
  1 AS `start_distance_m`,
  1 AS `end_distance_m`,
  1 AS `avg_speed`,
  1 AS `max_speed`,
  1 AS `avg_power`,
  1 AS `max_power`,
  1 AS `avg_heart_rate`,
  1 AS `max_heart_rate`,
  1 AS `avg_cadence`,
  1 AS `start_point_seq`,
  1 AS `end_point_seq`,
  1 AS `pr_rank`,
  1 AS `created_at`,
  1 AS `segment_name`,
  1 AS `segment_distance` */;
SET character_set_client = @saved_cs_client;

--
-- Table structure for table `site_post`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `site_post` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `persona` varchar(20) NOT NULL COMMENT 'DEVELOPER / RIDER / HUMAN',
  `slug` varchar(220) NOT NULL,
  `title` varchar(255) NOT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `content` longtext NOT NULL COMMENT 'Markdown',
  `mood_emoji` varchar(16) DEFAULT NULL,
  `mood_label` varchar(50) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED',
  `view_count` int(11) NOT NULL DEFAULT 0,
  `rag_opt_in` tinyint(1) NOT NULL DEFAULT 1 COMMENT '페르소나 챗봇 임베딩 포함 여부',
  `published_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_post_slug` (`persona`,`slug`),
  KEY `idx_site_post_list` (`persona`,`status`,`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `strava_sync_checkpoint`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `strava_sync_checkpoint` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activity_core_id` bigint(20) NOT NULL,
  `stage` varchar(30) NOT NULL COMMENT 'matched|activity_fetched|efforts_done|failed',
  `strava_activity_id` bigint(20) DEFAULT NULL,
  `total_efforts` int(11) DEFAULT NULL,
  `processed_efforts` int(11) DEFAULT 0,
  `next_effort_idx` int(11) DEFAULT 0,
  `pending_segment_ids` text DEFAULT NULL COMMENT '상세 조회 대기 중인 segment_id JSON 배열',
  `last_error` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity` (`activity_core_id`),
  KEY `idx_stage` (`stage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tmp_exercise_mapping`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `tmp_exercise_mapping` (
  `old_id` bigint(20) NOT NULL,
  `new_id` bigint(20) NOT NULL,
  PRIMARY KEY (`old_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_basic_profile`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `user_basic_profile` (
  `user_id` bigint(20) NOT NULL,
  `career` longtext DEFAULT NULL,
  `education` longtext DEFAULT NULL,
  `intro` longtext DEFAULT NULL,
  `contact` longtext DEFAULT NULL,
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_body_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `user_body_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) DEFAULT NULL,
  `record_date` date DEFAULT NULL,
  `weight` double DEFAULT NULL,
  `skeletal_muscle_mass` double DEFAULT NULL,
  `body_fat_mass` double DEFAULT NULL,
  `body_fat_percentage` double DEFAULT NULL,
  `bmi` double DEFAULT NULL,
  `visceral_fat_level` int(11) DEFAULT NULL,
  `total_body_water` double DEFAULT NULL,
  `protein` double DEFAULT NULL,
  `mineral` double DEFAULT NULL,
  `waist_circumference` double DEFAULT NULL,
  `thigh_circumference` double DEFAULT NULL,
  `chest_circumference` double DEFAULT NULL,
  `source` varchar(50) DEFAULT NULL,
  `raw_filename` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `measurement_type` varchar(20) NOT NULL DEFAULT 'FITDAYS',
  `raw_llm_json` longtext DEFAULT NULL COMMENT 'LLM 분석 결과 JSON (summary, evaluation, recommendations 등)',
  `bone_mass` double DEFAULT NULL,
  `basal_metabolic_rate` double DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_date_type` (`user_id`,`record_date`,`measurement_type`),
  CONSTRAINT `user_body_record_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_profile`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `user_profile` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `gender` varchar(10) DEFAULT NULL COMMENT 'MALE / FEMALE 또는 남/여',
  `age` int(11) DEFAULT NULL COMMENT '나이 (코드가 그대로 출력하므로 INT로 저장)',
  `height_cm` decimal(5,2) DEFAULT NULL COMMENT '키 (cm)',
  `goal_weight` decimal(5,2) DEFAULT NULL COMMENT '목표 체중 (kg)',
  `goal_body_fat_percentage` decimal(5,2) DEFAULT NULL COMMENT '목표 체지방률 (%)',
  `goal_skeletal_muscle_mass` decimal(5,2) DEFAULT NULL COMMENT '목표 골격근량 (kg)',
  `goal_ftp` int(11) DEFAULT NULL COMMENT '목표 FTP (W)',
  `goal_avg_speed` decimal(5,2) DEFAULT NULL COMMENT '목표 평균 속도 (km/h)',
  `goal_wkg` decimal(4,2) DEFAULT NULL COMMENT '목표 W/kg',
  `memo` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Temporary table structure for view `v_exercise_muscles`
--

SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
/*!50001 CREATE VIEW `v_exercise_muscles` AS SELECT
 1 AS `exercise_item_id`,
  1 AS `name`,
  1 AS `name_ko`,
  1 AS `name_en`,
  1 AS `equipment_type`,
  1 AS `category_id`,
  1 AS `muscle_group_id`,
  1 AS `muscle_name_ko`,
  1 AS `muscle_name_en`,
  1 AS `muscle_category_ko`,
  1 AS `muscle_category_en`,
  1 AS `role`,
  1 AS `activation_level` */;
SET character_set_client = @saved_cs_client;

--
-- Temporary table structure for view `v_muscle_exercises`
--

SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
/*!50001 CREATE VIEW `v_muscle_exercises` AS SELECT
 1 AS `muscle_group_id`,
  1 AS `muscle_name_ko`,
  1 AS `muscle_name_en`,
  1 AS `category_ko`,
  1 AS `exercise_item_id`,
  1 AS `name`,
  1 AS `exercise_name_en`,
  1 AS `equipment_type`,
  1 AS `role`,
  1 AS `activation_level` */;
SET character_set_client = @saved_cs_client;

--
-- Temporary table structure for view `v_persona_corpus`
--

SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
/*!50001 CREATE VIEW `v_persona_corpus` AS SELECT
 1 AS `source`,
  1 AS `ref_id`,
  1 AS `title`,
  1 AS `content`,
  1 AS `published_at` */;
SET character_set_client = @saved_cs_client;

--
-- Final view structure for view `segment_leaderboard`
--

/*!50001 DROP VIEW IF EXISTS `segment_leaderboard`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb3 */;
/*!50001 SET character_set_results     = utf8mb3 */;
/*!50001 SET collation_connection      = utf8mb3_general_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`tho881`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `segment_leaderboard` AS select `se`.`segment_id` AS `segment_id`,`se`.`activity_core_id` AS `activity_core_id`,`se`.`elapsed_time_sec` AS `elapsed_time_sec`,`se`.`avg_speed` AS `avg_speed`,`se`.`avg_power` AS `avg_power`,`se`.`start_time` AS `start_time`,row_number() over ( partition by `se`.`segment_id` order by `se`.`elapsed_time_sec`) AS `rank` from (`segment_effort` `se` join `segment` `s` on(`se`.`segment_id` = `s`.`id`)) where `s`.`is_active` = 1 */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `segment_pr`
--

/*!50001 DROP VIEW IF EXISTS `segment_pr`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb3 */;
/*!50001 SET character_set_results     = utf8mb3 */;
/*!50001 SET collation_connection      = utf8mb3_general_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`tho881`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `segment_pr` AS select `se`.`id` AS `id`,`se`.`segment_id` AS `segment_id`,`se`.`activity_core_id` AS `activity_core_id`,`se`.`start_time` AS `start_time`,`se`.`end_time` AS `end_time`,`se`.`elapsed_time_sec` AS `elapsed_time_sec`,`se`.`moving_time_sec` AS `moving_time_sec`,`se`.`match_percent` AS `match_percent`,`se`.`start_distance_m` AS `start_distance_m`,`se`.`end_distance_m` AS `end_distance_m`,`se`.`avg_speed` AS `avg_speed`,`se`.`max_speed` AS `max_speed`,`se`.`avg_power` AS `avg_power`,`se`.`max_power` AS `max_power`,`se`.`avg_heart_rate` AS `avg_heart_rate`,`se`.`max_heart_rate` AS `max_heart_rate`,`se`.`avg_cadence` AS `avg_cadence`,`se`.`start_point_seq` AS `start_point_seq`,`se`.`end_point_seq` AS `end_point_seq`,`se`.`pr_rank` AS `pr_rank`,`se`.`created_at` AS `created_at`,`s`.`name` AS `segment_name`,`s`.`distance` AS `segment_distance` from (`segment_effort` `se` join `segment` `s` on(`se`.`segment_id` = `s`.`id`)) where `se`.`pr_rank` = 1 */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `v_exercise_muscles`
--

/*!50001 DROP VIEW IF EXISTS `v_exercise_muscles`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_general_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`tho881`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `v_exercise_muscles` AS select `ei`.`id` AS `exercise_item_id`,`ei`.`name` AS `name`,`ei`.`name_ko` AS `name_ko`,`ei`.`name_en` AS `name_en`,`ei`.`equipment_type` AS `equipment_type`,`ei`.`category_id` AS `category_id`,`mg`.`id` AS `muscle_group_id`,`mg`.`name_ko` AS `muscle_name_ko`,`mg`.`name_en` AS `muscle_name_en`,coalesce(`pmg`.`name_ko`,`mg`.`name_ko`) AS `muscle_category_ko`,coalesce(`pmg`.`name_en`,`mg`.`name_en`) AS `muscle_category_en`,`emm`.`role` AS `role`,`emm`.`activation_level` AS `activation_level` from (((`exercise_item` `ei` join `exercise_muscle_map` `emm` on(`ei`.`id` = `emm`.`exercise_item_id`)) join `muscle_group` `mg` on(`emm`.`muscle_group_id` = `mg`.`id`)) left join `muscle_group` `pmg` on(`mg`.`parent_id` = `pmg`.`id`)) order by `ei`.`id`,field(`emm`.`role`,'PRIMARY','SECONDARY','SYNERGIST'),`emm`.`activation_level` desc */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `v_muscle_exercises`
--

/*!50001 DROP VIEW IF EXISTS `v_muscle_exercises`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_general_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`tho881`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `v_muscle_exercises` AS select `mg`.`id` AS `muscle_group_id`,`mg`.`name_ko` AS `muscle_name_ko`,`mg`.`name_en` AS `muscle_name_en`,coalesce(`pmg`.`name_ko`,`mg`.`name_ko`) AS `category_ko`,`ei`.`id` AS `exercise_item_id`,`ei`.`name` AS `name`,`ei`.`name_en` AS `exercise_name_en`,`ei`.`equipment_type` AS `equipment_type`,`emm`.`role` AS `role`,`emm`.`activation_level` AS `activation_level` from (((`muscle_group` `mg` left join `muscle_group` `pmg` on(`mg`.`parent_id` = `pmg`.`id`)) join `exercise_muscle_map` `emm` on(`mg`.`id` = `emm`.`muscle_group_id`)) join `exercise_item` `ei` on(`emm`.`exercise_item_id` = `ei`.`id`)) order by `mg`.`id`,field(`emm`.`role`,'PRIMARY','SECONDARY','SYNERGIST'),`emm`.`activation_level` desc */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `v_persona_corpus`
--

/*!50001 DROP VIEW IF EXISTS `v_persona_corpus`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_uca1400_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`tho881`@`172.18.0.1` SQL SECURITY DEFINER */
/*!50001 VIEW `v_persona_corpus` AS select 'TISTORY' AS `source`,`blog_post`.`id` AS `ref_id`,`blog_post`.`title` AS `title`,`blog_post`.`content` AS `content`,`blog_post`.`published_at` AS `published_at` from `blog_post` union all select 'SITE' AS `source`,`site_post`.`id` AS `ref_id`,`site_post`.`title` AS `title`,`site_post`.`content` AS `content`,`site_post`.`published_at` AS `published_at` from `site_post` where `site_post`.`status` = 'PUBLISHED' and `site_post`.`rag_opt_in` = 1 */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*M!100616 SET NOTE_VERBOSITY=@OLD_NOTE_VERBOSITY */;

-- Dump completed
