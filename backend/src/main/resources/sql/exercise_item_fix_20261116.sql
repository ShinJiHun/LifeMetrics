-- 운동 종목(exercise_item) 바로잡기 + 헬스장 유산소 종목 — 2026-11-16
-- ddl-auto=none 이므로 이 스크립트를 riding DB 에 직접 실행한다.
--   개발: riding_db_dev (2026-11-16 실행)   운영: riding_db (운영 배포 때, 백업 후)
--
-- 운영과 개발의 id 가 다를 수 있어 모든 UPDATE 는 id 와 name_ko 가 둘 다 맞을 때만 바꾼다.
-- 이미 바뀐 DB 에 다시 실행해도 결과가 같다(INSERT 는 같은 이름이 없을 때만).
-- 카테고리: 1 가슴, 2 등, 3 어깨, 5 하체, 6 복근, 7 유산소, 9 삼두, 10 이두
--
-- 왜: 앱의 '오늘 운동 추천' 이 카테고리로 종목을 고르는데, 컬 · 트라이셉스 익스텐션이
--     하체/복근에 들어가 있어 '하체' 를 고르면 바벨 컬이 나오는 식이었다.
--     유산소 기록은 기구 종목이 있어야 저장되므로 실내 사이클 · 로잉 머신을 추가한다.

START TRANSACTION;

-- 1) GIF 가 운동과 맞지 않던 것
-- 케이블 레터럴 레이즈 GIF 가 잘못 붙어 있었음, 어깨→가슴
UPDATE exercise_item SET gif_url = 'chest/INCLINE_DUMBBELL_BENCH_PRESS.gif', category_id = 1 WHERE id = 8 AND name_ko = '덤벨 인클라인 프레스';
-- 덤벨 플라이 GIF 가 잘못 붙어 있었음, 어깨→가슴
UPDATE exercise_item SET gif_url = 'chest/DECLINE_CHEST_PRESS_MACHINE.gif', category_id = 1 WHERE id = 17 AND name_ko = '펙덱 디클라인 프레스';

-- 2) 카테고리가 틀린 것 (GIF 는 맞음)
UPDATE exercise_item SET category_id = 10 WHERE id = 220 AND name_ko = '바벨 컬'; -- 하체→이두
UPDATE exercise_item SET category_id = 10 WHERE id = 223 AND name_ko = '덤벨 컬'; -- 하체→이두
UPDATE exercise_item SET category_id = 10 WHERE id = 222 AND name_ko = '덤벨 해머 컬'; -- 하체→이두
UPDATE exercise_item SET category_id = 10 WHERE id = 226 AND name_ko = '바벨 이지바 컬'; -- 하체→이두
UPDATE exercise_item SET category_id = 10 WHERE id = 743 AND name_ko = '바벨 이지바 바이 쉡스퀄'; -- 하체→이두
UPDATE exercise_item SET category_id = 9 WHERE id = 229 AND name_ko = '케이블 라인 트라이셉스 익스텐션'; -- 하체→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 234 AND name_ko = '케이블 오버헤드 익스텐션'; -- 하체→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 236 AND name_ko = '케이블 시티드 오버헤드 익스텐션'; -- 하체→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 238 AND name_ko = '라인 트라이셉스 익스텐션'; -- 하체→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 243 AND name_ko = '케이블 스탠딩 오버헤드 익스텐션'; -- 하체→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 774 AND name_ko = '라인 트라이쉡스 익스텐션'; -- 하체→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 777 AND name_ko = '라잉 트라이 쉡스 오버헤드 익스텐션'; -- 하체→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 231 AND name_ko = '클로즈 그립 스미스 벤치 프레스'; -- 복근→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 232 AND name_ko = '벤치 딥스'; -- 복근→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 235 AND name_ko = '케이블 푸쉬 다운'; -- 복근→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 838 AND name_ko = '케이블 로프 푸쉬다운'; -- 복근→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 839 AND name_ko = '덤벨 킥백'; -- 복근→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 816 AND name_ko = '케이블 푸쉬다운'; -- 등→삼두
UPDATE exercise_item SET category_id = 9 WHERE id = 228 AND name_ko = '스미스 클로즈 그립 프레스'; -- 어깨→삼두
UPDATE exercise_item SET category_id = 1 WHERE id = 4 AND name_ko = '디클라인 프레스'; -- 어깨→가슴
UPDATE exercise_item SET category_id = 1 WHERE id = 20 AND name_ko = '스미스 인클라인 프레스'; -- 어깨→가슴
UPDATE exercise_item SET category_id = 1 WHERE id = 12 AND name_ko = '덤벨 풀 오버'; -- 어깨→가슴
UPDATE exercise_item SET category_id = 3 WHERE id = 117 AND name_ko = '업라이트 로우'; -- 등→어깨
UPDATE exercise_item SET category_id = 3 WHERE id = 146 AND name_ko = '바벨 업 라이트 로우'; -- 등→어깨
UPDATE exercise_item SET category_id = 3 WHERE id = 169 AND name_ko = '바벨러 업라이트 로우'; -- 등→어깨
UPDATE exercise_item SET category_id = 3 WHERE id = 795 AND name_ko = '바베러 업라이트 로우'; -- 등→어깨
UPDATE exercise_item SET category_id = 5 WHERE id = 179 AND name_ko = '박스 점프'; -- 팔→하체
UPDATE exercise_item SET category_id = 5 WHERE id = 192 AND name_ko = '머신 힙 어드덕션'; -- 팔→하체
UPDATE exercise_item SET category_id = 5 WHERE id = 700 AND name_ko = '바벨 백수쿼트'; -- 팔→하체
UPDATE exercise_item SET category_id = 6 WHERE id = 252 AND name_ko = '레그 레이즈'; -- 하체→복근
UPDATE exercise_item SET category_id = 6 WHERE id = 862 AND name_ko = '행잉 레그 리어'; -- 하체→복근
UPDATE exercise_item SET category_id = 2 WHERE id = 863 AND name_ko = '백 익스텐션'; -- 삼두→등
UPDATE exercise_item SET category_id = 2 WHERE id = 67 AND name_ko = '하이퍼 익스텐션'; -- 하체→등
UPDATE exercise_item SET category_id = 2 WHERE id = 253 AND name_ko = '하이픈 익스텐션'; -- 하체→등
UPDATE exercise_item SET category_id = 5 WHERE id = 806 AND name_ko = '덤벨 스티프 레그 데드리프트'; -- 등→하체

-- 3) 헬스장 유산소 종목: 실내 사이클 · 로잉 머신 추가, 천국의 계단 GIF 연결
INSERT INTO exercise_item (category_id, name, name_ko, name_en, equipment_type, gif_url, description, is_active)
SELECT 7, '실내 사이클', '실내 사이클', 'Indoor Cycle', 'MACHINE', 'cardio/CYCLING.gif',
       '헬스장 실내 자전거로 하는 유산소 운동입니다. 케이던스 85~95 를 유지하며 저항(파워)으로 강도를 조절하고, 무릎 부담이 적어 하체 운동 뒤나 걸음이 많은 날에 좋습니다.', 1
WHERE NOT EXISTS (SELECT 1 FROM exercise_item WHERE name_ko = '실내 사이클');
INSERT INTO exercise_item (category_id, name, name_ko, name_en, equipment_type, gif_url, description, is_active)
SELECT 7, '로잉 머신', '로잉 머신', 'Rowing Machine', 'MACHINE', 'cardio/ROWING_MACHINE.gif',
       '다리로 밀고 몸통을 젖힌 뒤 팔로 당기는 순서로 노를 젓는 전신 유산소 운동입니다. 등과 하체를 함께 쓰며, 짧고 강한 인터벌에 적합합니다.', 1
WHERE NOT EXISTS (SELECT 1 FROM exercise_item WHERE name_ko = '로잉 머신');
UPDATE exercise_item SET gif_url = 'cardio/STEPMILL.gif' WHERE name_ko = '천국의 계단' AND gif_url IS NULL;

COMMIT;

-- 확인: 바뀐 행 수가 아래 목록과 맞는지 본다
SELECT id, name_ko, category_id, gif_url FROM exercise_item
WHERE id IN (8, 17, 220, 223, 222, 226, 743, 229, 234, 236, 238, 243, 774, 777, 231, 232, 235, 838, 839, 816, 228, 4, 20, 12, 117, 146, 169, 795, 179, 192, 700, 252, 862, 863, 67, 253, 806)
   OR name_ko IN ('실내 사이클', '로잉 머신', '천국의 계단')
ORDER BY category_id, id;
