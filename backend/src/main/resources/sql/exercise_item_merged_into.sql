-- 중복 운동 종목 병합용 컬럼 — 2026-11-21
-- ddl-auto=none 이므로 직접 실행한다. dev(riding_db_dev)·운영(riding_db) 각각, 새 백엔드 배포 *전에*.
-- 추가만 하므로 이전 버전 백엔드에는 영향 없음. 다시 실행해도 같음(IF NOT EXISTS).
--
-- merged_into: 이 종목이 합쳐진 대표 종목 id. NULL 이면 독립 종목.
-- 병합은 관리자 API 로 한다: GET /api/exercise/items/duplicates (미리보기) → POST /api/exercise/items/merge-duplicates
-- PT 시트 동기화는 merged_into 를 따라가 오타 이름도 대표 종목에 기록한다.

ALTER TABLE exercise_item
    ADD COLUMN IF NOT EXISTS merged_into BIGINT NULL AFTER is_active,
    ADD INDEX IF NOT EXISTS idx_exercise_item_merged_into (merged_into);
