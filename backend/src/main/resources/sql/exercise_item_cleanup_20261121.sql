-- 운동 종목 병합 뒤 정리 — 2026-11-21
-- 중복 병합(POST /api/exercise/items/merge-duplicates) 다음에 실행한다. 운영 riding_db 기준으로 만들었고 dev 에도 실행해도 된다.
-- id 와 이름이 둘 다 맞을 때만 바꾸므로 id 가 다른 DB 에서는 건너뛰고, 다시 실행해도 결과가 같다.
-- 카테고리: 1 가슴, 2 등, 3 어깨, 5 하체, 6 복근, 7 유산소, 9 삼두, 10 이두

START TRANSACTION;

-- 1) 대표 종목 이름이 오타("페러럴 레이즈")라 바른 이름("레터럴 레이즈", 병합된 759)과 맞바꾼다.
--    759 는 merged_into=145 라 시트에 "페러럴 레이즈" 오타가 다시 나와도 145 로 기록된다.
--    (category_id, name) 유니크라 임시 이름을 거친다.
UPDATE exercise_item SET name = '__tmp_759' WHERE id = 759 AND merged_into = 145;
UPDATE exercise_item SET name = '레터럴 레이즈', name_ko = '레터럴 레이즈' WHERE id = 145 AND name_ko = '페러럴 레이즈';
UPDATE exercise_item SET name = '페러럴 레이즈', name_ko = '페러럴 레이즈' WHERE id = 759 AND name = '__tmp_759';

-- 2) 영문명이 잘못 들어가 벤치 딥스와 묶이던 종목
UPDATE exercise_item SET name_en = 'Kettlebell Deadlift and Row'
 WHERE id = 836 AND name_ko = '케틀벨 데드 엔 로우' AND name_en = 'Bench Dips';

-- 3) 카테고리 바로잡기 (컬은 이두, 익스텐션은 삼두, 니 레이즈는 복근, 인클라인 프레스는 가슴)
UPDATE exercise_item SET category_id = 10 WHERE id = 62  AND name_ko = '바벨 바이셉스 컬';
UPDATE exercise_item SET category_id = 10 WHERE id = 225 AND name_ko = '케이블 바이셉스 컬';
UPDATE exercise_item SET category_id = 9  WHERE id = 230 AND name_ko = '시티드 오버헤드 익스텐션';
UPDATE exercise_item SET category_id = 9  WHERE id = 245 AND name_ko = '라잉 트라이셉스 오버헤드 익스텐션';
UPDATE exercise_item SET category_id = 6  WHERE id = 189 AND name_ko = '시티드 니 레이즈';
UPDATE exercise_item SET category_id = 1  WHERE id = 14  AND name_ko = '토처 인클라인 프레스';
UPDATE exercise_item SET category_id = 9  WHERE id = 22  AND name_ko = '덤벨 클로즈 그립 프레스';

-- 4) "푸처 로우로우 원암"(717)은 양손 "토처 로우 로우"(694)가 아니라 "원암 토처 로우"(89)와 같은 운동 → 89 로 병합.
--    영문명도 고쳐서 다음 병합 미리보기에서 694 와 묶이지 않게 한다. (dev 는 이미 694 로 병합돼 있어 건너뜀)
UPDATE exercise_log SET exercise_item_id = 89
 WHERE exercise_item_id = 717
   AND EXISTS (SELECT 1 FROM exercise_item WHERE id = 717 AND merged_into IS NULL AND name_ko = '푸처 로우로우 원암');
UPDATE exercise_item SET merged_into = 89, is_active = 0, name_en = 'One-Arm Torture Row'
 WHERE id = 717 AND merged_into IS NULL AND name_ko = '푸처 로우로우 원암';

COMMIT;
