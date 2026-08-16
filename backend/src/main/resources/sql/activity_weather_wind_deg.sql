-- activity_weather.wind_deg 컬럼 추가 — 라이딩 시작 시점 풍향(0~360도) 캐시용
-- ddl-auto=none 이므로 이 스크립트를 journal_db 에 직접 실행한다.
--   mysql -h 127.0.0.1 -P 3307 -u <user> -p journal_db < activity_weather_wind_deg.sql
--
-- RideLivePage(라이딩 라이브 리플레이)에서 당시 풍향을 화살표로 보여주기 위해 추가.
-- 값은 ActivityService.getActivityDetail() 이 최초 조회 시 Open-Meteo Archive API로
-- 채워 넣고 이 컬럼에 캐싱한다 (매 조회마다 외부 API를 다시 부르지 않기 위함).

USE `journal_db`;

ALTER TABLE `activity_weather`
    ADD COLUMN `wind_deg` INT DEFAULT NULL COMMENT '풍향(도, 0~360, 북풍=0)';
