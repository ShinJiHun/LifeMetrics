-- 따릉이(서울 공공자전거) 대여소 좌표 캐시.
-- 따릉이 이용내역 캡처에는 대여소 이름만 있고 좌표가 없어서, 추정 경로를 그리려면
-- 대여소 이름/번호 → 좌표를 찾아야 한다. 서울 열린데이터 bikeList API 로 채운다
-- (POST /api/ddareungi/stations/sync, seoul.openapi-key 필요).
-- prod(riding_db)에 직접 실행할 것. dev 와 prod 가 DB를 같이 쓰므로 한 번만 실행하면 된다.

CREATE TABLE IF NOT EXISTS `ddareungi_station` (
  `station_id`   varchar(20)  NOT NULL COMMENT '서울시 대여소 ID (ST-4 등)',
  `station_no`   varchar(10)  DEFAULT NULL COMMENT '대여소 번호 (이름 앞 숫자, 예: 102)',
  `station_name` varchar(100) NOT NULL COMMENT '대여소 이름 (번호 제외)',
  `lat`          double       NOT NULL,
  `lon`          double       NOT NULL,
  `updated_at`   datetime     NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`station_id`),
  KEY `idx_ddareungi_station_no` (`station_no`),
  KEY `idx_ddareungi_station_name` (`station_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
