-- 유산소 기록 (2026-11-15)
-- 헬스장 유산소(시트 '유산소' 탭 / 앱 입력)를 exercise_log 에 같이 저장한다.
-- 운동 시간은 기존 duration_sec 사용. 거리·칼로리·심박만 추가.
ALTER TABLE exercise_log
    ADD COLUMN IF NOT EXISTS distance_km DECIMAL(6,2) NULL AFTER duration_sec,
    ADD COLUMN IF NOT EXISTS kcal        INT          NULL AFTER distance_km,
    ADD COLUMN IF NOT EXISTS avg_hr      SMALLINT     NULL AFTER kcal;

-- 카테고리 정리: '유산소'에 잘못 들어간 복근 운동 → '복근', 트레드밀 → '유산소'
UPDATE exercise_item i
JOIN exercise_category src ON src.id = i.category_id AND src.name = '유산소'
JOIN exercise_category dst ON dst.name = '복근'
SET i.category_id = dst.id
WHERE i.name IN ('싯업', '복부', '굼벵이 휠', '복근 운동');

UPDATE exercise_item i
JOIN exercise_category dst ON dst.name = '유산소'
SET i.category_id = dst.id
WHERE i.name_en = 'Treadmill';
