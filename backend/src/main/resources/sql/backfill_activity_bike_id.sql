-- activity_core.bike_id backfill (purchase_date 기준)
--
-- trg_activity_core_bike_by_purchase 는 BEFORE INSERT 라서, 트리거 생성 이전에
-- 들어간 활동은 bike_id 가 NULL 로 남아 있다. 그 행들을 같은 규칙으로 채운다.
--
-- 트리거와의 차이: 트리거의 is_retired = 0 조건을 뺐다.
-- 과거 라이드는 지금 은퇴한 자전거로 탔을 수 있고, 그걸 제외하면 당시 보유하지도
-- 않았던 현재 자전거로 잘못 매칭된다. 구매일 기준만 남기는 게 이력상 정확하다.
--
-- 순서대로: (1) 현황 확인 → (2) 매칭 결과 미리보기 → (3) 실제 UPDATE

-- ---------------------------------------------------------------------------
-- (1) 현황: 채울 게 얼마나 되나
-- ---------------------------------------------------------------------------
SELECT
    COUNT(*)                                          AS total,
    SUM(bike_id IS NULL)                              AS null_bike_id,
    SUM(bike_id IS NOT NULL)                          AS already_set
FROM activity_core;

-- 구매일이 없는 자전거는 후보가 될 수 없다. 있으면 먼저 채워야 매칭된다.
SELECT id, name, purchase_date, is_retired
FROM bike
ORDER BY purchase_date;

-- ---------------------------------------------------------------------------
-- (2) 미리보기: 어떤 활동이 어떤 자전거로 붙을지 (UPDATE 전에 반드시 확인)
-- ---------------------------------------------------------------------------
SELECT
    a.id,
    a.start_time,
    a.name                                            AS activity_name,
    b.id                                              AS matched_bike_id,
    b.name                                            AS matched_bike,
    b.purchase_date,
    b.is_retired
FROM activity_core a
LEFT JOIN bike b ON b.id = (
    SELECT b2.id
      FROM bike b2
     WHERE b2.purchase_date IS NOT NULL
       AND b2.purchase_date <= a.start_time
     ORDER BY b2.purchase_date DESC
     LIMIT 1
)
WHERE a.bike_id IS NULL
ORDER BY a.start_time;

-- 매칭 자전거별 건수 요약
SELECT
    b.name                                            AS matched_bike,
    COUNT(*)                                          AS activities,
    MIN(a.start_time)                                 AS first_ride,
    MAX(a.start_time)                                 AS last_ride
FROM activity_core a
LEFT JOIN bike b ON b.id = (
    SELECT b2.id
      FROM bike b2
     WHERE b2.purchase_date IS NOT NULL
       AND b2.purchase_date <= a.start_time
     ORDER BY b2.purchase_date DESC
     LIMIT 1
)
WHERE a.bike_id IS NULL
GROUP BY b.id, b.name
ORDER BY activities DESC;

-- ---------------------------------------------------------------------------
-- (3) 실제 backfill
--
-- - bike_id 가 이미 있는 행은 건드리지 않는다 (수동 교정분 보존)
-- - 첫 자전거 구매일보다 앞선 활동은 매칭 대상이 없으므로 NULL 로 남긴다
-- ---------------------------------------------------------------------------
-- START TRANSACTION;

UPDATE activity_core a
SET a.bike_id = (
    SELECT b.id
      FROM bike b
     WHERE b.purchase_date IS NOT NULL
       AND b.purchase_date <= a.start_time
     ORDER BY b.purchase_date DESC
     LIMIT 1
)
WHERE a.bike_id IS NULL
  AND EXISTS (
    SELECT 1
      FROM bike b
     WHERE b.purchase_date IS NOT NULL
       AND b.purchase_date <= a.start_time
  );

-- 확인 후 COMMIT; / 이상하면 ROLLBACK;
-- COMMIT;

-- ---------------------------------------------------------------------------
-- (4) 사후 확인: 남은 NULL 은 전부 첫 구매일 이전 활동이어야 한다
-- ---------------------------------------------------------------------------
SELECT id, start_time, name
FROM activity_core
WHERE bike_id IS NULL
ORDER BY start_time;
