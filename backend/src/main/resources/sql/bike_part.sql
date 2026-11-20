-- 장비 변경 계획: 자전거 소모품(체인·타이어·카세트·브레이크패드 등) 교체 주기 (2026-11-19)
-- 사용 거리는 저장하지 않고 activity_core 에서 장착일~탈거일 사이 라이딩 거리를 매번 합산한다.
-- 교체하면 기존 행에 removed_on 을 채우고 새 행을 만든다(교체 이력이 남는다).
CREATE TABLE IF NOT EXISTS `riding_db`.`bike_part` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `bike_id`         BIGINT       NOT NULL,
  `part_type`       VARCHAR(32)  NOT NULL COMMENT 'CHAIN / TIRE_FRONT / TIRE_REAR / CASSETTE / CHAINRING / BRAKE_PAD / BAR_TAPE / CABLE / SEALANT / OTHER',
  `name`            VARCHAR(100) NULL     COMMENT '제품명 (예: 시마노 CN-HG701)',
  `installed_on`    DATE         NOT NULL COMMENT '장착일 — 이날 라이딩부터 사용 거리에 포함',
  `removed_on`      DATE         NULL     COMMENT '탈거일 — 이날 라이딩부터 제외. NULL 이면 사용 중',
  `initial_km`      DOUBLE       NOT NULL DEFAULT 0 COMMENT '기록 이전에 이미 쓴 거리(km)',
  `interval_km`     INT          NULL     COMMENT '권장 교체 거리(km)',
  `interval_months` INT          NULL     COMMENT '권장 교체 기간(개월)',
  `note`            VARCHAR(255) NULL,
  `created_at`      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bike_part_bike` (`bike_id`, `removed_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
