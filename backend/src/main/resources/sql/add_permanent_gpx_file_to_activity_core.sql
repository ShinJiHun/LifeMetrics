-- activity_core에 permanent_gpx_file 컬럼 추가
-- prod(riding_db)에 직접 실행할 것. 실행 후 create.sql을 mariadb-dump로 다시 떠서 갱신할 것.
--
-- 퍼머넌트 코스 폴더 안에 gpx가 여러 개인 경우(예: PT-20 본코스/Plan B, PT-98 큐시트 포함본)
-- 업로드 시 실제로 탄 gpx 파일명을 여기에 저장한다. permanent_no만으로는 어느 변형을
-- 탔는지 구분이 안 되기 때문. 값은 NAS의 실제 파일명 그대로(예: "PT-20_Plan_B_(2024).gpx").
-- FK로 묶이는 컬럼이 아니라서(permanent_no와 달리) 콜레이션은 테이블 기본값 그대로 둔다.

ALTER TABLE `activity_core`
  ADD COLUMN `permanent_gpx_file` varchar(255) DEFAULT NULL
    COMMENT '퍼머넌트 코스 폴더 안에서 실제로 탄 gpx 파일명 (여러 변형이 있을 때만 채움)';
