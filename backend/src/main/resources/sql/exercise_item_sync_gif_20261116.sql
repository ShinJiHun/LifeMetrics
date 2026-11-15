-- 운동 종목 GIF 경로를 개발(riding_db_dev)과 같게 — 2026-11-16
-- ddl-auto=none 이므로 riding DB 에 직접 실행한다. 운영 riding_db 용 (개발은 이미 이 상태).
--
-- 실행 순서 (운영 배포 때, riding_db 백업 후):
--   1. exercise_cardio.sql          (feat/cardio: 유산소 칸 추가, 복근 4종 · 트레드밀 카테고리, 트레드밀 → 러닝머신)
--   2. exercise_item_fix_20261116.sql (GIF · 카테고리 바로잡기, 실내 사이클 · 로잉 머신)
--   3. 이 파일
--
-- 왜: 운영 gif_url 35개 중 32개가 예전 파일 이름(BARBELL_BANCH_PRESS.gif, leg/... 등)이라
--     NAS(/mnt/200gb/NAS/exercise-gifs) 에 없어 GIF 가 안 나온다. 2026-11-15 에 GIF 를
--     영문 이름 + 부위 폴더로 새로 올리고 개발 DB 만 연결해 둔 것을 운영에도 반영한다.
--     개발에서 GIF 를 비워 둔 6건은 운영도 비운다 (가리키던 파일이 없음).
--
-- id 와 name_ko 가 둘 다 맞을 때만 바꾸고, 다시 실행해도 결과가 같다.
-- 개발에만 있는 PT 시트 자동 추가 종목(935~940 등)은 옮기지 않는다 — 운영 시트 동기화가 따로 만든다.

START TRANSACTION;

-- 1) 종목별 GIF 경로 (215건)
UPDATE exercise_item SET gif_url = 'shoulder/SMITH_MACHINE_OVERHEAD_PRESS.gif' WHERE id = 2 AND name_ko = '스미스 프레스';  -- 운영: shoulder/SMITH_PRESS.gif
UPDATE exercise_item SET gif_url = 'chest/BENCH_PRESS.gif' WHERE id = 3 AND name_ko = '바벨 벤치 프레스';  -- 운영: chest/BARBELL_BANCH_PRESS.gif
UPDATE exercise_item SET gif_url = 'chest/DECLINE_CHEST_PRESS_MACHINE.gif' WHERE id = 4 AND name_ko = '디클라인 프레스';  -- 운영: shoulder/DECLINE_PRESS.gif
UPDATE exercise_item SET gif_url = 'chest/STANDING_CABLE_FLY.gif' WHERE id = 5 AND name_ko = '케이블 크로스 오버';  -- 운영: chest/CABLE_CROSSOVER.gif
UPDATE exercise_item SET gif_url = 'chest/SMITH_MACHINE_BENCH_PRESS.gif' WHERE id = 6 AND name_ko = '스미스 벤치 프레스';  -- 운영: chest/SMITH_BENCH_PRESS.gif
UPDATE exercise_item SET gif_url = 'chest/INCLINE_CABLE_FLY.gif' WHERE id = 7 AND name_ko = '케이블 인클라인 플라이';  -- 운영: chest/CABLE_INCLINE_FLY.gif
UPDATE exercise_item SET gif_url = 'chest/INCLINE_DUMBBELL_BENCH_PRESS.gif' WHERE id = 8 AND name_ko = '덤벨 인클라인 프레스';  -- 운영: shoulder/CABLE_LATERAL_RAISE.gif
UPDATE exercise_item SET gif_url = 'chest/CHEST_PRESS_MACHINE.gif' WHERE id = 9 AND name_ko = '체스트 프레스';  -- 운영: chest/CHEST_PRESS.gif
UPDATE exercise_item SET gif_url = 'chest/CHEST_PRESS_MACHINE.gif' WHERE id = 10 AND name_ko = '체스트 프레스 원암';  -- 운영: chest/CHEST_PRESS_ONE_ARM.gif
UPDATE exercise_item SET gif_url = 'triceps/CLOSE_GRIP_PUSH_UP.gif' WHERE id = 11 AND name_ko = '클로즈 그립 푸쉬업';  -- 운영: chest/CLOSE_GRIP_PUSHUP.gif
UPDATE exercise_item SET gif_url = 'chest/DUMBBELL_PULLOVER.gif' WHERE id = 12 AND name_ko = '덤벨 풀 오버';  -- 운영: shoulder/DUMBBELL_PULLOVER.gif
UPDATE exercise_item SET gif_url = 'triceps/ASSISTED_DIPS_MACHINE.gif' WHERE id = 13 AND name_ko = '어시스트 딥스';  -- 운영: leg/ASSISTED_DIPS.gif
UPDATE exercise_item SET gif_url = NULL WHERE id = 14 AND name_ko = '토처 인클라인 프레스';  -- 운영: shoulder/TOUCH_INCLINE_PRESS.gif
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_SHOULDER_PRESS.gif' WHERE id = 15 AND name_ko = '덤벨 얼터네이터 프레스';  -- 운영: shoulder/DUMBBELL_ALTERNATE.gif
UPDATE exercise_item SET gif_url = 'triceps/DIPS.gif' WHERE id = 16 AND name_ko = '딥스 프레스';  -- 운영: leg/DIPS_PRESS.gif
UPDATE exercise_item SET gif_url = 'chest/DECLINE_CHEST_PRESS_MACHINE.gif' WHERE id = 17 AND name_ko = '펙덱 디클라인 프레스';  -- 운영: chest/DUMBBELL_FLY.gif
UPDATE exercise_item SET gif_url = 'chest/CHEST_PRESS_MACHINE.gif' WHERE id = 19 AND name_ko = '머신 체스트 프레스';  -- 운영: chest/MACHINE_CHEST_PRESS.gif
UPDATE exercise_item SET gif_url = 'chest/SMITH_MACHINE_INCLINE_BENCH_PRESS.gif' WHERE id = 20 AND name_ko = '스미스 인클라인 프레스';  -- 운영: chest/SMITH_BENCH_INCLINE_PRESS.gif
UPDATE exercise_item SET gif_url = 'chest/DUMBBELL_FLY.gif' WHERE id = 21 AND name_ko = '덤벨 플라이';
UPDATE exercise_item SET gif_url = 'chest/PEC_DECK_FLY_MACHINE.gif' WHERE id = 23 AND name_ko = '펙덱 플라이';
UPDATE exercise_item SET gif_url = 'back/LAT_PULLDOWN.gif' WHERE id = 24 AND name_ko = '렛풀다운';
UPDATE exercise_item SET gif_url = 'chest/DECLINE_PUSH_UP.gif' WHERE id = 25 AND name_ko = '디클라인 푸쉬업';
UPDATE exercise_item SET gif_url = 'chest/DECLINE_CHEST_PRESS_MACHINE.gif' WHERE id = 26 AND name_ko = '디클라인 체스트 프레스';
UPDATE exercise_item SET gif_url = 'chest/INCLINE_DUMBBELL_BENCH_PRESS.gif' WHERE id = 27 AND name_ko = '덤벨 인클라인 체스트 프레스';
UPDATE exercise_item SET gif_url = 'chest/BENCH_PRESS.gif' WHERE id = 28 AND name_ko = '플랫 벤치 프레스';
UPDATE exercise_item SET gif_url = 'chest/DUMBBELL_BENCH_PRESS.gif' WHERE id = 31 AND name_ko = '덤벨 벤치 프레스';
UPDATE exercise_item SET gif_url = 'chest/INCLINE_PUSH_UP.gif' WHERE id = 35 AND name_ko = '인클라인 푸쉬업';
UPDATE exercise_item SET gif_url = 'back/STANDING_CABLE_ROW.gif' WHERE id = 46 AND name_ko = '케이블 스탠딩 로우';
UPDATE exercise_item SET gif_url = 'back/CONVENTIONAL_DEADLIFT.gif' WHERE id = 48 AND name_ko = '바벨 데드리프트';  -- 운영: back/BARBELL_DEADLIFT.gif
UPDATE exercise_item SET gif_url = 'back/SEATED_ROW_MACHINE.gif' WHERE id = 50 AND name_ko = '머신 로우';
UPDATE exercise_item SET gif_url = 'back/CONVENTIONAL_DEADLIFT.gif' WHERE id = 52 AND name_ko = '컨벤셔널 데드리프트';
UPDATE exercise_item SET gif_url = 'back/DUMBBELL_ROW.gif' WHERE id = 54 AND name_ko = '덤벨 벤트오버 로우';
UPDATE exercise_item SET gif_url = 'back/ONE_ARM_KETTLEBELL_ROW.gif' WHERE id = 57 AND name_ko = '원암 케틀 벨 로우';
UPDATE exercise_item SET gif_url = 'back/ASSISTED_PULL_UP_MACHINE.gif' WHERE id = 63 AND name_ko = '풀업 어시스트';
UPDATE exercise_item SET gif_url = 'back/BARBELL_ROW.gif' WHERE id = 64 AND name_ko = '바벨 로우';
UPDATE exercise_item SET gif_url = 'back/HIGH_PULLEY_CABLE_ROW.gif' WHERE id = 65 AND name_ko = '케이블 하이 로우';
UPDATE exercise_item SET gif_url = 'back/ONE_ARM_DUMBBELL_ROW.gif' WHERE id = 66 AND name_ko = '덤벨 원암 로우';
UPDATE exercise_item SET gif_url = 'back/HYPEREXTENSION.gif' WHERE id = 67 AND name_ko = '하이퍼 익스텐션';
UPDATE exercise_item SET gif_url = 'shoulder/CABLE_REVERSE_FLY.gif' WHERE id = 70 AND name_ko = '케이블 원암 리버스 플라이';
UPDATE exercise_item SET gif_url = 'back/STANDING_CABLE_ROW.gif' WHERE id = 71 AND name_ko = '케이블 스탠딩 로우 패러럴 그립';
UPDATE exercise_item SET gif_url = 'chest/CABLE_PULLOVER.gif' WHERE id = 74 AND name_ko = '케이블 풀 오버';
UPDATE exercise_item SET gif_url = 'back/ONE_ARM_SEATED_CABLE_ROW.gif' WHERE id = 75 AND name_ko = '원암 케이블 로우';
UPDATE exercise_item SET gif_url = 'back/ONE_ARM_KETTLEBELL_ROW.gif' WHERE id = 76 AND name_ko = '케틀 벨 원암 로우';
UPDATE exercise_item SET gif_url = 'back/SEATED_CABLE_ROW.gif' WHERE id = 80 AND name_ko = '케이블 시티드 넥 그립 로우';
UPDATE exercise_item SET gif_url = 'back/FRONT_PULLDOWN_MACHINE.gif' WHERE id = 81 AND name_ko = '머신 렛풀 다운';
UPDATE exercise_item SET gif_url = 'back/SMITH_MACHINE_DEADLIFT.gif' WHERE id = 82 AND name_ko = '스미스 머신 데드리프트';
UPDATE exercise_item SET gif_url = 'back/HIGH_PULLEY_ONE_ARM_CABLE_ROW.gif' WHERE id = 83 AND name_ko = '케이블 원암 하이 로우';
UPDATE exercise_item SET gif_url = 'back/SEATED_CABLE_ROW.gif' WHERE id = 85 AND name_ko = '케이블 시티드 언더 그립 로우';
UPDATE exercise_item SET gif_url = 'back/STANDING_CABLE_ROW.gif' WHERE id = 86 AND name_ko = '케이블 스탠딩 언더 그립 로우';
UPDATE exercise_item SET gif_url = 'back/PULL_UP.gif' WHERE id = 87 AND name_ko = '풀업';
UPDATE exercise_item SET gif_url = 'back/BEHIND_THE_NECK_PULLDOWN.gif' WHERE id = 93 AND name_ko = '렛풀 다운 비하인드';
UPDATE exercise_item SET gif_url = 'back/T_BAR_ROW_MACHINE.gif' WHERE id = 95 AND name_ko = '티바 로우';
UPDATE exercise_item SET gif_url = 'shoulder/BENT_OVER_DUMBBELL_LATERAL_RAISE.gif' WHERE id = 97 AND name_ko = '벤트 오버 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_SHOULDER_PRESS.gif' WHERE id = 98 AND name_ko = '덤벨 숄더 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/BEHIND_THE_NECK_PRESS.gif' WHERE id = 102 AND name_ko = '비하인드 넥 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/FACE_PULL.gif' WHERE id = 104 AND name_ko = '케이블 페이스풀';
UPDATE exercise_item SET gif_url = 'shoulder/OVERHEAD_PRESS.gif' WHERE id = 106 AND name_ko = '밀리터리 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/SMITH_MACHINE_OVERHEAD_PRESS.gif' WHERE id = 107 AND name_ko = '스미스 숄더 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/LATERAL_RAISE_MACHINE.gif' WHERE id = 108 AND name_ko = '머신 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_LATERAL_RAISE.gif' WHERE id = 109 AND name_ko = '덤벨 사이드 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/FACE_PULL.gif' WHERE id = 110 AND name_ko = '페이스 풀';
UPDATE exercise_item SET gif_url = 'shoulder/CABLE_REVERSE_FLY.gif' WHERE id = 115 AND name_ko = '케이블 리버스 플라이';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_SHOULDER_PRESS.gif' WHERE id = 116 AND name_ko = '덤벨 스탠딩 숄더 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/BARBELL_UPRIGHT_ROW.gif' WHERE id = 117 AND name_ko = '업라이트 로우';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_FRONT_RAISE.gif' WHERE id = 118 AND name_ko = '덤벨 프론트 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/SHOULDER_PRESS_MACHINE.gif' WHERE id = 119 AND name_ko = '숄더 프레스 머신 리버스';
UPDATE exercise_item SET gif_url = 'shoulder/OVERHEAD_PRESS.gif' WHERE id = 123 AND name_ko = '바벨 숄더 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/BEHIND_THE_NECK_PRESS.gif' WHERE id = 124 AND name_ko = '스미스 비하인드 넥 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/LATERAL_RAISE_MACHINE.gif' WHERE id = 126 AND name_ko = '사이드 레터럴 레이즈 머신';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_LATERAL_RAISE.gif' WHERE id = 127 AND name_ko = '덤벨 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_LATERAL_RAISE.gif' WHERE id = 131 AND name_ko = '사이드 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_FRONT_RAISE.gif' WHERE id = 132 AND name_ko = '프론트 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/SHOULDER_PRESS_MACHINE.gif' WHERE id = 133 AND name_ko = '머신 숄더 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/CABLE_FRONT_RAISE.gif' WHERE id = 134 AND name_ko = '케이블 프론트 레이즈 로프';
UPDATE exercise_item SET gif_url = 'shoulder/LATERAL_RAISE_MACHINE.gif' WHERE id = 137 AND name_ko = '머신 사이드 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/BEHIND_THE_NECK_PRESS.gif' WHERE id = 140 AND name_ko = '숄더 비하인드 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/BARBELL_UPRIGHT_ROW.gif' WHERE id = 146 AND name_ko = '바벨 업 라이트 로우';
UPDATE exercise_item SET gif_url = 'shoulder/BENT_OVER_DUMBBELL_LATERAL_RAISE.gif' WHERE id = 147 AND name_ko = '덤벨 벤트 오버 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/REAR_DELTOID_FLY_MACHINE.gif' WHERE id = 149 AND name_ko = '머신 리어 플라이';
UPDATE exercise_item SET gif_url = 'shoulder/CABLE_FRONT_RAISE.gif' WHERE id = 150 AND name_ko = '케이블 프론트 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/BEHIND_THE_NECK_PRESS.gif' WHERE id = 151 AND name_ko = '바벨 비하인드 넥 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/REAR_DELTOID_FLY_MACHINE.gif' WHERE id = 152 AND name_ko = '머신 리버스 플라이';
UPDATE exercise_item SET gif_url = 'shoulder/CABLE_REVERSE_FLY.gif' WHERE id = 158 AND name_ko = '케이블 리어 플라이';
UPDATE exercise_item SET gif_url = 'shoulder/CABLE_LATERAL_RAISE.gif' WHERE id = 163 AND name_ko = '케이블 사이드 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/CABLE_FRONT_RAISE.gif' WHERE id = 165 AND name_ko = '케이블 패러럴 그립 프론트 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/CABLE_FRONT_RAISE.gif' WHERE id = 166 AND name_ko = '케이블 로프 프론트 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/BARBELL_UPRIGHT_ROW.gif' WHERE id = 169 AND name_ko = '바벨러 업라이트 로우';
UPDATE exercise_item SET gif_url = 'legs/LEG_EXTENSION.gif' WHERE id = 170 AND name_ko = '레그 익스텐션';
UPDATE exercise_item SET gif_url = 'legs/SMITH_MACHINE_SQUAT.gif' WHERE id = 173 AND name_ko = '스미스 스쿼트 언더';
UPDATE exercise_item SET gif_url = 'legs/BARBELL_SPLIT_SQUAT.gif' WHERE id = 174 AND name_ko = '바벨 스플릿 스쿼트';
UPDATE exercise_item SET gif_url = 'legs/BARBELL_BACK_SQUAT.gif' WHERE id = 177 AND name_ko = '바벨 백 스쿼트';
UPDATE exercise_item SET gif_url = 'legs/BARBELL_LUNGE.gif' WHERE id = 178 AND name_ko = '바벨 런지';
UPDATE exercise_item SET gif_url = 'etc/BOX_JUMP.gif' WHERE id = 179 AND name_ko = '박스 점프';
UPDATE exercise_item SET gif_url = 'legs/LEG_CURL.gif' WHERE id = 183 AND name_ko = '레그 컬';
UPDATE exercise_item SET gif_url = 'legs/LEG_PRESS.gif' WHERE id = 184 AND name_ko = '레그 프레스';
UPDATE exercise_item SET gif_url = 'legs/JUMP_SQUAT.gif' WHERE id = 186 AND name_ko = '스쿼트 점프 스쿼트';
UPDATE exercise_item SET gif_url = 'legs/HIP_ADDUCTION_MACHINE.gif' WHERE id = 192 AND name_ko = '머신 힙 어드덕션';
UPDATE exercise_item SET gif_url = 'legs/BARBELL_BACK_SQUAT.gif' WHERE id = 197 AND name_ko = '백 스쿼트';
UPDATE exercise_item SET gif_url = 'legs/LEG_PRESS.gif' WHERE id = 202 AND name_ko = '머신 레그 프레스';
UPDATE exercise_item SET gif_url = 'legs/SMITH_MACHINE_SPLIT_SQUAT.gif' WHERE id = 206 AND name_ko = '스미스 스플릿 스쿼트';
UPDATE exercise_item SET gif_url = 'legs/SMITH_MACHINE_SQUAT.gif' WHERE id = 207 AND name_ko = '스미스 백 스쿼트';
UPDATE exercise_item SET gif_url = 'legs/HACK_SQUAT_MACHINE.gif' WHERE id = 209 AND name_ko = '핵 스쿼트';
UPDATE exercise_item SET gif_url = 'legs/DUMBBELL_GOBLET_SQUAT.gif' WHERE id = 217 AND name_ko = '덤벨 고블린 스쿼트';
UPDATE exercise_item SET gif_url = 'legs/DUMBBELL_SQUAT.gif' WHERE id = 218 AND name_ko = '덤벨 스쿼트';
UPDATE exercise_item SET gif_url = 'biceps/BARBELL_CURL.gif' WHERE id = 220 AND name_ko = '바벨 컬';
UPDATE exercise_item SET gif_url = 'biceps/DUMBBELL_HAMMER_CURL.gif' WHERE id = 222 AND name_ko = '덤벨 해머 컬';
UPDATE exercise_item SET gif_url = 'biceps/DUMBBELL_CURL.gif' WHERE id = 223 AND name_ko = '덤벨 컬';
UPDATE exercise_item SET gif_url = 'biceps/EZ_BAR_CURL.gif' WHERE id = 226 AND name_ko = '바벨 이지바 컬';
UPDATE exercise_item SET gif_url = 'triceps/CLOSE_GRIP_SMITH_MACHINE_BENCH_PRESS.gif' WHERE id = 228 AND name_ko = '스미스 클로즈 그립 프레스';
UPDATE exercise_item SET gif_url = 'triceps/CABLE_LYING_TRICEPS_EXTENSION.gif' WHERE id = 229 AND name_ko = '케이블 라인 트라이셉스 익스텐션';
UPDATE exercise_item SET gif_url = 'triceps/CLOSE_GRIP_SMITH_MACHINE_BENCH_PRESS.gif' WHERE id = 231 AND name_ko = '클로즈 그립 스미스 벤치 프레스';
UPDATE exercise_item SET gif_url = 'triceps/BENCH_DIPS.gif' WHERE id = 232 AND name_ko = '벤치 딥스';
UPDATE exercise_item SET gif_url = 'triceps/CABLE_OVERHEAD_TRICEPS_EXTENSION.gif' WHERE id = 234 AND name_ko = '케이블 오버헤드 익스텐션';
UPDATE exercise_item SET gif_url = 'triceps/CABLE_PUSHDOWN.gif' WHERE id = 235 AND name_ko = '케이블 푸쉬 다운';
UPDATE exercise_item SET gif_url = 'triceps/CABLE_OVERHEAD_TRICEPS_EXTENSION.gif' WHERE id = 236 AND name_ko = '케이블 시티드 오버헤드 익스텐션';
UPDATE exercise_item SET gif_url = 'triceps/BARBELL_LYING_TRICEPS_EXTENSION.gif' WHERE id = 238 AND name_ko = '라인 트라이셉스 익스텐션';
UPDATE exercise_item SET gif_url = 'triceps/CABLE_OVERHEAD_TRICEPS_EXTENSION.gif' WHERE id = 243 AND name_ko = '케이블 스탠딩 오버헤드 익스텐션';
UPDATE exercise_item SET gif_url = 'abs/SIT_UP.gif' WHERE id = 251 AND name_ko = '싯업';
UPDATE exercise_item SET gif_url = 'abs/LEG_RAISE.gif' WHERE id = 252 AND name_ko = '레그 레이즈';
UPDATE exercise_item SET gif_url = 'back/HYPEREXTENSION.gif' WHERE id = 253 AND name_ko = '하이픈 익스텐션';
UPDATE exercise_item SET gif_url = 'shoulder/BENT_OVER_DUMBBELL_LATERAL_RAISE.gif' WHERE id = 669 AND name_ko = '벤투 오버 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'back/STANDING_CABLE_ROW.gif' WHERE id = 672 AND name_ko = '케이블 스탠딩 로우 로우';
UPDATE exercise_item SET gif_url = 'legs/BARBELL_SPLIT_SQUAT.gif' WHERE id = 674 AND name_ko = '스플릿 스쿼트 바벨';
UPDATE exercise_item SET gif_url = 'back/BARBELL_ROW.gif' WHERE id = 675 AND name_ko = '바벨 벤투 오버 로우';
UPDATE exercise_item SET gif_url = 'back/SEATED_ROW_MACHINE.gif' WHERE id = 676 AND name_ko = '머신 로우 로우';
UPDATE exercise_item SET gif_url = 'back/CONVENTIONAL_DEADLIFT.gif' WHERE id = 682 AND name_ko = '바벨 데드 리프트';
UPDATE exercise_item SET gif_url = 'back/STANDING_CABLE_ROW.gif' WHERE id = 683 AND name_ko = '케이블 스탠딩 로우 케이블';
UPDATE exercise_item SET gif_url = 'chest/CHEST_PRESS_MACHINE.gif' WHERE id = 686 AND name_ko = '체스트 프레스 머신 숏';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_SHOULDER_PRESS.gif' WHERE id = 687 AND name_ko = '덤벨 어깨 프레스';
UPDATE exercise_item SET gif_url = 'legs/LEG_PRESS.gif' WHERE id = 690 AND name_ko = '레그 프레스 (크로스핏)';
UPDATE exercise_item SET gif_url = 'back/DUMBBELL_ROW.gif' WHERE id = 695 AND name_ko = '덤벨 벤투 오버 로우';
UPDATE exercise_item SET gif_url = 'chest/CHEST_PRESS_MACHINE.gif' WHERE id = 698 AND name_ko = '체스트 프레스 머신';
UPDATE exercise_item SET gif_url = 'legs/BARBELL_BACK_SQUAT.gif' WHERE id = 700 AND name_ko = '바벨 백수쿼트';
UPDATE exercise_item SET gif_url = 'back/ASSISTED_PULL_UP_MACHINE.gif' WHERE id = 703 AND name_ko = '벤트 풀업 어시스트';
UPDATE exercise_item SET gif_url = 'back/BARBELL_ROW.gif' WHERE id = 704 AND name_ko = '바벨로우';
UPDATE exercise_item SET gif_url = 'back/HIGH_PULLEY_CABLE_ROW.gif' WHERE id = 705 AND name_ko = '가바벨 하이 로우 풀다운';
UPDATE exercise_item SET gif_url = 'shoulder/BENT_OVER_DUMBBELL_LATERAL_RAISE.gif' WHERE id = 706 AND name_ko = '벤투오버 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/BEHIND_THE_NECK_PRESS.gif' WHERE id = 707 AND name_ko = '프론트 비하인드 넥 프레스';
UPDATE exercise_item SET gif_url = 'back/HIGH_PULLEY_CABLE_ROW.gif' WHERE id = 713 AND name_ko = '케이블 하이로우';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_LATERAL_RAISE.gif' WHERE id = 714 AND name_ko = '덤킨 사이드 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'chest/SMITH_MACHINE_INCLINE_BENCH_PRESS.gif' WHERE id = 715 AND name_ko = '스미스 인클라인 벤치 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_SHOULDER_PRESS.gif' WHERE id = 723 AND name_ko = '덤벨 숄도 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/FACE_PULL.gif' WHERE id = 724 AND name_ko = '케이블 페이스 풀';
UPDATE exercise_item SET gif_url = 'chest/SMITH_MACHINE_INCLINE_BENCH_PRESS.gif' WHERE id = 728 AND name_ko = '스미스 인클라인';
UPDATE exercise_item SET gif_url = 'chest/INCLINE_DUMBBELL_BENCH_PRESS.gif' WHERE id = 729 AND name_ko = '덤벨 인클라인';
UPDATE exercise_item SET gif_url = 'back/SEATED_CABLE_ROW.gif' WHERE id = 732 AND name_ko = '케이블 시티드 페러럴 그립 로우';
UPDATE exercise_item SET gif_url = 'shoulder/SHOULDER_PRESS_MACHINE.gif' WHERE id = 738 AND name_ko = '숄더 머신 프레스';
UPDATE exercise_item SET gif_url = 'chest/DECLINE_PUSH_UP.gif' WHERE id = 740 AND name_ko = '디 클라인 푸쉬업';
UPDATE exercise_item SET gif_url = 'chest/DECLINE_CHEST_PRESS_MACHINE.gif' WHERE id = 741 AND name_ko = '디 클라인 체스트 프레스';
UPDATE exercise_item SET gif_url = 'triceps/ASSISTED_DIPS_MACHINE.gif' WHERE id = 742 AND name_ko = '딥스 어시스트';
UPDATE exercise_item SET gif_url = 'biceps/EZ_BAR_CURL.gif' WHERE id = 743 AND name_ko = '바벨 이지바 바이 쉡스퀄';
UPDATE exercise_item SET gif_url = 'shoulder/BEHIND_THE_NECK_PRESS.gif' WHERE id = 744 AND name_ko = '비하인드 넥프레스';
UPDATE exercise_item SET gif_url = 'shoulder/BENT_OVER_DUMBBELL_LATERAL_RAISE.gif' WHERE id = 745 AND name_ko = '덤벨 벤투 오버 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'back/LOW_PULLEY_CABLE_ROPE_ROW.gif' WHERE id = 746 AND name_ko = '케이블 로프 로우 로우';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_FRONT_RAISE.gif' WHERE id = 748 AND name_ko = '덤벨 프론르 레이즈';
UPDATE exercise_item SET gif_url = 'chest/INCLINE_PUSH_UP.gif' WHERE id = 755 AND name_ko = '인클라인 푸쉬 업';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_LATERAL_RAISE.gif' WHERE id = 759 AND name_ko = '레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/BENT_OVER_DUMBBELL_LATERAL_RAISE.gif' WHERE id = 762 AND name_ko = '덤벨 벤투오버 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'chest/SMITH_MACHINE_INCLINE_BENCH_PRESS.gif' WHERE id = 764 AND name_ko = '스미스 머신 인클라인';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_SHOULDER_PRESS.gif' WHERE id = 765 AND name_ko = '덤벨 프레스';
UPDATE exercise_item SET gif_url = 'triceps/DIPS.gif' WHERE id = 769 AND name_ko = '딥스';  -- 운영: chest/DIPS.gif
UPDATE exercise_item SET gif_url = 'chest/STANDING_CABLE_FLY.gif' WHERE id = 770 AND name_ko = '케이블 체스트 크로스 오버';  -- 운영: chest/CABLE_CHEST_CROSSOVER.gif
UPDATE exercise_item SET gif_url = 'back/HIGH_PULLEY_CABLE_ROW.gif' WHERE id = 772 AND name_ko = '케이블 하이로우 언더 그립';  -- 운영: back/CABLE_HIGH_ROW.gif
UPDATE exercise_item SET gif_url = NULL WHERE id = 773 AND name_ko = '베틀벨 벤투 오버 로우';  -- 운영: back/DUMBBELL_BENT_OVER_ROW.gif
UPDATE exercise_item SET gif_url = 'triceps/BARBELL_LYING_TRICEPS_EXTENSION.gif' WHERE id = 774 AND name_ko = '라인 트라이쉡스 익스텐션';  -- 운영: leg/LINE_TRICEPS_EXTENSION.gif
UPDATE exercise_item SET gif_url = 'chest/DECLINE_PUSH_UP.gif' WHERE id = 775 AND name_ko = '디 푸쉬업';
UPDATE exercise_item SET gif_url = 'chest/SMITH_MACHINE_INCLINE_BENCH_PRESS.gif' WHERE id = 776 AND name_ko = '스미스 벤치 인클라인 프레스';
UPDATE exercise_item SET gif_url = 'triceps/BARBELL_LYING_TRICEPS_EXTENSION.gif' WHERE id = 777 AND name_ko = '라잉 트라이 쉡스 오버헤드 익스텐션';  -- 운영: leg/LINE_TRICEPS_OHP.gif
UPDATE exercise_item SET gif_url = 'chest/DECLINE_CHEST_PRESS_MACHINE.gif' WHERE id = 778 AND name_ko = '체스트 메뉴얼 토처 디클라인 프레스';  -- 운영: chest/CHEST_MENU_TOUCH_DECLINE_PRESS.gif
UPDATE exercise_item SET gif_url = 'back/STANDING_CABLE_ROW.gif' WHERE id = 780 AND name_ko = '케이블 스탠딩 로우 언더그립';  -- 운영: back/CABLE_STANDING_ROW.gif
UPDATE exercise_item SET gif_url = NULL WHERE id = 781 AND name_ko = '원암 토처 토처 로우 로우';  -- 운영: leg/ONE_ARM_TOUCH_ROW.gif
UPDATE exercise_item SET gif_url = 'back/ONE_ARM_KETTLEBELL_ROW.gif' WHERE id = 782 AND name_ko = '케틀벨 원암';  -- 운영: shoulder/CABLE_BICEP.gif
UPDATE exercise_item SET gif_url = 'triceps/BENCH_DIPS.gif' WHERE id = 783 AND name_ko = '딥스 벤치';  -- 운영: leg/DIPS_BENCH.gif
UPDATE exercise_item SET gif_url = NULL WHERE id = 784 AND name_ko = '토처 로우 원암';  -- 운영: back/TOUCH_ROW.gif
UPDATE exercise_item SET gif_url = NULL WHERE id = 785 AND name_ko = '와이드 풀다운 리어';  -- 운영: shoulder/WIDE_PULLDOWN.gif
UPDATE exercise_item SET gif_url = NULL WHERE id = 786 AND name_ko = '토처 인 클라인';  -- 운영: chest/TOUCH_INCLINE_PRESS.gif
UPDATE exercise_item SET gif_url = 'chest/PEC_DECK_FLY_MACHINE.gif' WHERE id = 787 AND name_ko = '머신 펙덱 플라이';  -- 운영: chest/MACHINE_FLY.gif
UPDATE exercise_item SET gif_url = 'shoulder/BARBELL_UPRIGHT_ROW.gif' WHERE id = 795 AND name_ko = '바베러 업라이트 로우';
UPDATE exercise_item SET gif_url = 'biceps/DUMBBELL_HAMMER_CURL.gif' WHERE id = 798 AND name_ko = '덤벨 헤머 컬';
UPDATE exercise_item SET gif_url = 'chest/SMITH_MACHINE_BENCH_PRESS.gif' WHERE id = 804 AND name_ko = '스미스 머신 벤치 프레스';
UPDATE exercise_item SET gif_url = 'legs/DUMBBELL_STIFF_LEG_DEADLIFT.gif' WHERE id = 806 AND name_ko = '덤벨 스티프 레그 데드리프트';
UPDATE exercise_item SET gif_url = 'triceps/CLOSE_GRIP_PUSH_UP.gif' WHERE id = 807 AND name_ko = '클로즈 푸쉬업';
UPDATE exercise_item SET gif_url = 'back/CABLE_ARM_PULLDOWN.gif' WHERE id = 812 AND name_ko = '케이블 암풀 다운';
UPDATE exercise_item SET gif_url = 'triceps/CABLE_PUSHDOWN.gif' WHERE id = 816 AND name_ko = '케이블 푸쉬다운';
UPDATE exercise_item SET gif_url = 'shoulder/DUMBBELL_LATERAL_RAISE.gif' WHERE id = 821 AND name_ko = '덤벨 시티드 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'chest/SMITH_MACHINE_BENCH_PRESS.gif' WHERE id = 824 AND name_ko = '스미스머신 벤치 프레스';
UPDATE exercise_item SET gif_url = 'legs/DUMBBELL_GOBLET_SQUAT.gif' WHERE id = 825 AND name_ko = '고블릿 스쿼트 덤벨';
UPDATE exercise_item SET gif_url = 'back/ONE_ARM_KETTLEBELL_ROW.gif' WHERE id = 830 AND name_ko = '케틀벨 원암 로우';
UPDATE exercise_item SET gif_url = 'shoulder/EZ_BAR_FRONT_RAISE.gif' WHERE id = 832 AND name_ko = '아자바벨 프론트 레이즈';
UPDATE exercise_item SET gif_url = 'shoulder/LATERAL_RAISE_MACHINE.gif' WHERE id = 833 AND name_ko = '머신 시티드 레터럴 레이즈';
UPDATE exercise_item SET gif_url = 'chest/CHEST_PRESS_MACHINE.gif' WHERE id = 834 AND name_ko = '머신 체스트 프레스 네거티브';
UPDATE exercise_item SET gif_url = 'triceps/CABLE_ROPE_PUSHDOWN.gif' WHERE id = 838 AND name_ko = '케이블 로프 푸쉬다운';
UPDATE exercise_item SET gif_url = 'triceps/DUMBBELL_KICKBACK.gif' WHERE id = 839 AND name_ko = '덤벨 킥백';
UPDATE exercise_item SET gif_url = 'legs/BARBELL_BACK_SQUAT.gif' WHERE id = 844 AND name_ko = '바벨 백 스쿼트 봉';
UPDATE exercise_item SET gif_url = 'shoulder/FACE_PULL.gif' WHERE id = 848 AND name_ko = '케이블 오버그립 페이스 풀';
UPDATE exercise_item SET gif_url = 'legs/STANDING_HIP_ABDUCTION.gif' WHERE id = 851 AND name_ko = '스탠딩 힙 어브덕션';
UPDATE exercise_item SET gif_url = 'triceps/CLOSE_GRIP_SMITH_MACHINE_BENCH_PRESS.gif' WHERE id = 852 AND name_ko = '스미스 머신 클로즈 프레스';
UPDATE exercise_item SET gif_url = 'triceps/CABLE_OVERHEAD_TRICEPS_EXTENSION.gif' WHERE id = 853 AND name_ko = '케이블 인클라인 오버헤드 익스텐션';
UPDATE exercise_item SET gif_url = 'biceps/CABLE_HAMMER_CURL.gif' WHERE id = 855 AND name_ko = '케이블 로프 헤머 컬';
UPDATE exercise_item SET gif_url = 'chest/BENCH_PRESS.gif' WHERE id = 856 AND name_ko = '벤치 프레스';
UPDATE exercise_item SET gif_url = 'shoulder/SEATED_DUMBBELL_SHOULDER_PRESS.gif' WHERE id = 861 AND name_ko = '시티드 덤벨 숄더 프레스';
UPDATE exercise_item SET gif_url = 'abs/HANGING_LEG_RAISE.gif' WHERE id = 862 AND name_ko = '행잉 레그 리어';
UPDATE exercise_item SET gif_url = 'back/BACK_EXTENSION.gif' WHERE id = 863 AND name_ko = '백 익스텐션';
UPDATE exercise_item SET gif_url = 'triceps/CLOSE_GRIP_BENCH_PRESS.gif' WHERE id = 864 AND name_ko = '클로드 벤치 프레스';
UPDATE exercise_item SET gif_url = 'triceps/DUMBBELL_LYING_TRICEPS_EXTENSION.gif' WHERE id = 865 AND name_ko = '라잉 크라잉 쉡스 익스텐션 덤벨';
UPDATE exercise_item SET gif_url = 'biceps/INCLINE_DUMBBELL_CURL.gif' WHERE id = 866 AND name_ko = '인클라인 덤벨 컬';
UPDATE exercise_item SET gif_url = 'biceps/CABLE_HAMMER_CURL.gif' WHERE id = 867 AND name_ko = '케이블 헤머 컬';
UPDATE exercise_item SET gif_url = 'shoulder/SHOULDER_PRESS_MACHINE.gif' WHERE id = 886 AND name_ko = '머신 숄더 프레스 리버스';
UPDATE exercise_item SET gif_url = 'shoulder/CABLE_EXTERNAL_ROTATION.gif' WHERE id = 898 AND name_ko = '케이블 스탠딩 익스터널 로테이션';
UPDATE exercise_item SET gif_url = 'back/ONE_ARM_SEATED_CABLE_ROW.gif' WHERE id = 918 AND name_ko = '케이블 시티드 원암 로우';
UPDATE exercise_item SET gif_url = 'legs/DUMBBELL_SINGLE_LEG_DEADLIFT.gif' WHERE id = 921 AND name_ko = '싱글 레그 데드 리프트';
UPDATE exercise_item SET gif_url = 'legs/KETTLEBELL_SWING.gif' WHERE id = 925 AND name_ko = '케틀벨 스윙';
UPDATE exercise_item SET gif_url = 'chest/INCLINE_BENCH_PRESS.gif' WHERE id = 928 AND name_ko = '바벨 인클라인 벤치 프레스';
UPDATE exercise_item SET gif_url = 'legs/AIR_SQUAT.gif' WHERE id = 930 AND name_ko = '에어 스쿼트';
UPDATE exercise_item SET gif_url = 'legs/SMITH_MACHINE_SQUAT.gif' WHERE id = 931 AND name_ko = '스미스 머신 라이트 스쿼트';

-- 2) 트레드밀: exercise_cardio.sql 에서 이름이 러닝머신으로 바뀌므로 영문명으로 찾음
UPDATE exercise_item SET gif_url = 'cardio/TREADMILL.gif' WHERE name_en = 'Treadmill';

-- 3) 천국의 계단(스텝밀): 개발에는 시트 동기화로 생겼고 운영엔 없어 추가 (앱 유산소 기록용)
INSERT INTO exercise_item (category_id, name, name_ko, name_en, gif_url, is_active)
SELECT 7, '천국의 계단', '천국의 계단', 'Stair Climber', 'cardio/STEPMILL.gif', 1
WHERE NOT EXISTS (SELECT 1 FROM exercise_item WHERE name_ko = '천국의 계단');

COMMIT;

-- 확인: GIF 가 있는 활성 종목 수 (개발 2026-11-16 기준 520 / 214, PT 시트 자동 추가 종목 수만큼 다를 수 있음)
SELECT COUNT(*) AS items, SUM(gif_url IS NOT NULL) AS with_gif FROM exercise_item WHERE is_active;
