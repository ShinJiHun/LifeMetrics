-- activity_core에 라이딩 타입(퍼머넌트/브레베/플레쉬/포퓨레어/투어링/일반) 선택 기능 추가
-- prod(riding_db)에 직접 실행할 것. 실행 후 create.sql을 mariadb-dump로 다시 떠서 갱신할 것.
--
-- permanent_courses, permanent_courses_change_log 두 테이블도 이미 prod에 생성되어 있으나
-- (2026-08, ebrevet API 동기화 배치 도입 시 별도 실행함) create.sql 스냅샷에는 아직 안 잡혀 있음.
-- 다음에 create.sql 재덤프할 때 같이 반영됨.
--
-- ⚠️ 콜레이션 주의: activity_core 테이블 기본 콜레이션은 utf8mb4_general_ci인데,
-- permanent_courses.permanent_no는 utf8mb4_unicode_ci로 만들어져 있어 콜레이션이 다르다.
-- FK를 걸려는 두 컬럼의 콜레이션이 다르면 MariaDB가 errno 150으로 거부하므로,
-- 아래처럼 새 컬럼에 permanent_courses와 동일한 콜레이션을 명시로 지정해야 한다.
-- (테이블 전체의 기본 콜레이션은 건드리지 않음 — 다른 컬럼에 영향 없음)

ALTER TABLE `activity_core`
  ADD COLUMN `ride_type` varchar(20)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '라이딩 타입: PERMANENT/BREVET/FLECHE/POPULAIRE/TOURING/GENERAL',
  ADD COLUMN `permanent_no` varchar(20)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT 'ride_type=PERMANENT일 때 permanent_courses.permanent_no 참조',
  ADD KEY `idx_activity_core_ride_type` (`ride_type`),
  ADD CONSTRAINT `fk_activity_core_permanent_no`
    FOREIGN KEY (`permanent_no`) REFERENCES `permanent_courses` (`permanent_no`)
    ON UPDATE CASCADE ON DELETE SET NULL;
