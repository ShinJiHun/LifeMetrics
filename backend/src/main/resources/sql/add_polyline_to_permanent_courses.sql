-- permanent_courses에 polyline 컬럼 추가
-- prod(riding_db)에 직접 실행할 것. 실행 후 create.sql을 mariadb-dump로 다시 떠서 갱신할 것.
--
-- NAS의 gpx 파일(폴더에 여러 개면 그중 대표 1개, PermanentGpxService.listGpxFiles 정렬 기준
-- 첫 번째)을 파싱해서 activity_core.polyline과 동일한 포맷(Google encoded polyline)으로
-- 인코딩해 저장한다. 값은 컬럼 추가 후 관리자가
--   POST /api/permanents/polyline/refresh-all
-- 를 한 번 호출해서 채운다(로컬 Mac에는 NAS가 마운트 안 돼 있어서 반드시 배포된 prod에서 호출).

ALTER TABLE `permanent_courses`
  ADD COLUMN `polyline` longtext DEFAULT NULL
    COMMENT 'NAS gpx(대표 파일) 인코딩 경로 — 코스 확인 페이지 지도 표시용';
