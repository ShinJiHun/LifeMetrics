-- 개발자 포트폴리오 페이지(/api/profile) — 소개/경력/학력/연락처
-- ddl-auto=none 이므로 이 스크립트를 journal_db 에 직접 실행한다.
--   mysql -h 127.0.0.1 -P 3307 -u <user> -p journal_db < profile.sql

USE `journal_db`;

-- 소개 문구(엘리베이터 피치) + 연락처. 단일 행만 사용한다.
CREATE TABLE IF NOT EXISTS `developer_profile` (
                                                   `id`             BIGINT NOT NULL AUTO_INCREMENT,
                                                   `elevator_pitch` TEXT   NOT NULL,
                                                   `highlights`     TEXT   DEFAULT NULL,   -- 줄바꿈(\n)으로 구분된 핵심 성과 목록
                                                   `headline`       TEXT   DEFAULT NULL,   -- 포트폴리오 히어로 제목. 줄바꿈(\n)으로 개행, {{강조문구}}로 accent 색 강조
                                                   `subheadline`    TEXT   DEFAULT NULL,   -- 포트폴리오 히어로 부제
                                                   `role_tagline`   VARCHAR(255) DEFAULT NULL,  -- whoami 카드/푸터에 쓰이는 직함 태그라인
                                                   `focus_tags`     TEXT   DEFAULT NULL,   -- 쉼표(,)로 구분된 전문분야 태그 목록
                                                   `contact_blurb`  TEXT   DEFAULT NULL,   -- 연락처 섹션 안내 문구
                                                   `side_project`   VARCHAR(100) DEFAULT NULL,  -- whoami 카드 sideProject 값
                                                   `availability`   VARCHAR(100) DEFAULT NULL,  -- 구직/이직 준비 중일 때 표시할 문구
                                                   `open_to_work`   TINYINT(1)   NOT NULL DEFAULT 1,  -- 구직/이직 준비 여부
                                                   `phone`          VARCHAR(50)  DEFAULT NULL,
                                                   `github`         VARCHAR(255) DEFAULT NULL,
                                                   `blog`           VARCHAR(255) DEFAULT NULL,
                                                   PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 기존 배포(테이블이 이미 존재)에는 headline/subheadline 컬럼이 없으므로 추가한다.
ALTER TABLE `developer_profile` ADD COLUMN IF NOT EXISTS `headline` TEXT DEFAULT NULL AFTER `highlights`;
ALTER TABLE `developer_profile` ADD COLUMN IF NOT EXISTS `subheadline` TEXT DEFAULT NULL AFTER `headline`;
ALTER TABLE `developer_profile` ADD COLUMN IF NOT EXISTS `role_tagline` VARCHAR(255) DEFAULT NULL AFTER `subheadline`;
ALTER TABLE `developer_profile` ADD COLUMN IF NOT EXISTS `focus_tags` TEXT DEFAULT NULL AFTER `role_tagline`;
ALTER TABLE `developer_profile` ADD COLUMN IF NOT EXISTS `contact_blurb` TEXT DEFAULT NULL AFTER `focus_tags`;
ALTER TABLE `developer_profile` ADD COLUMN IF NOT EXISTS `side_project` VARCHAR(100) DEFAULT NULL AFTER `contact_blurb`;
ALTER TABLE `developer_profile` ADD COLUMN IF NOT EXISTS `availability` VARCHAR(100) DEFAULT NULL AFTER `side_project`;
ALTER TABLE `developer_profile` ADD COLUMN IF NOT EXISTS `open_to_work` TINYINT(1) NOT NULL DEFAULT 1 AFTER `availability`;

-- 소개 섹션 — 소제목 + 문단들
CREATE TABLE IF NOT EXISTS `profile_intro_section` (
                                                       `id`         BIGINT       NOT NULL AUTO_INCREMENT,
                                                       `subtitle`   VARCHAR(255) NOT NULL,
                                                       `lines`      TEXT         NOT NULL,     -- 줄바꿈(\n)으로 구분된 문단 목록
                                                       `sort_order` INT          NOT NULL DEFAULT 0,
                                                       PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 경력(회사) — 경력 타임라인(git log)과 경력 기술서에서 함께 사용
CREATE TABLE IF NOT EXISTS `career_company` (
                                                `id`           BIGINT       NOT NULL AUTO_INCREMENT,
                                                `path`         VARCHAR(255) NOT NULL,
                                                `domain`       VARCHAR(100) DEFAULT NULL,  -- 경력기술서 좌측 메뉴 그룹핑 기준 (예: "음성인식")
                                                `company_name` VARCHAR(255) NOT NULL,
                                                `short_name`   VARCHAR(100) DEFAULT NULL,  -- whoami 카드 등 좁은 자리에 쓰는 짧은 표기명
                                                `period_label` VARCHAR(100) NOT NULL,      -- 사람이 읽는 기간 문구
                                                `start_date`   DATE         DEFAULT NULL,  -- 총 경력 연차 계산용 입사일(매월 1일)
                                                `end_date`     DATE         DEFAULT NULL,  -- 총 경력 연차 계산용 퇴사일(재직중이면 NULL)
                                                `role`         VARCHAR(255) NOT NULL,
                                                `is_current`   TINYINT(1)   NOT NULL DEFAULT 0,
                                                `commit_hash`  VARCHAR(40)  DEFAULT NULL,
                                                `commit_tag`   VARCHAR(100) DEFAULT NULL,
                                                `stack`        TEXT         DEFAULT NULL,  -- 쉼표(,)로 구분된 기술 스택 목록
                                                `sort_order`   INT          NOT NULL DEFAULT 0,
                                                PRIMARY KEY (`id`),
                                                UNIQUE KEY `uq_career_company_path` (`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 기존 배포(테이블이 이미 존재)에는 domain 컬럼이 없으므로 추가한다.
ALTER TABLE `career_company` ADD COLUMN IF NOT EXISTS `domain` VARCHAR(100) DEFAULT NULL AFTER `path`;
ALTER TABLE `career_company` ADD COLUMN IF NOT EXISTS `short_name` VARCHAR(100) DEFAULT NULL AFTER `company_name`;
ALTER TABLE `career_company` ADD COLUMN IF NOT EXISTS `start_date` DATE DEFAULT NULL AFTER `period_label`;
ALTER TABLE `career_company` ADD COLUMN IF NOT EXISTS `end_date` DATE DEFAULT NULL AFTER `start_date`;

-- 경력 기술서의 프로젝트 항목 — career_company 에 귀속
CREATE TABLE IF NOT EXISTS `career_project` (
                                                `id`           BIGINT   NOT NULL AUTO_INCREMENT,
                                                `company_id`   BIGINT   NOT NULL,
                                                `title`        VARCHAR(512) NOT NULL,
                                                `period_label` VARCHAR(100) DEFAULT NULL,
                                                `paragraphs`   LONGTEXT NOT NULL,       -- 빈 줄(\n\n)로 구분된 설명 문단 목록
                                                `sort_order`   INT      NOT NULL DEFAULT 0,
                                                PRIMARY KEY (`id`),
                                                KEY `idx_career_project_company` (`company_id`),
                                                CONSTRAINT `fk_career_project_company` FOREIGN KEY (`company_id`)
                                                    REFERENCES `career_company` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 경력 기술서 프로젝트의 업무 항목 — career_project 에 귀속
CREATE TABLE IF NOT EXISTS `career_project_task` (
                                                     `id`           BIGINT   NOT NULL AUTO_INCREMENT,
                                                     `project_id`   BIGINT   NOT NULL,
                                                     `description`  TEXT     NOT NULL,
                                                     `sort_order`   INT      NOT NULL DEFAULT 0,
                                                     PRIMARY KEY (`id`),
                                                     KEY `idx_career_project_task_project` (`project_id`),
                                                     CONSTRAINT `fk_career_project_task_project` FOREIGN KEY (`project_id`)
                                                         REFERENCES `career_project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 업무 항목에 첨부된 화면 이미지/GIF/영상 — 실제 파일은 career.media.path 에 저장
CREATE TABLE IF NOT EXISTS `career_task_media` (
                                                   `id`           BIGINT       NOT NULL AUTO_INCREMENT,
                                                   `task_id`      BIGINT       NOT NULL,
                                                   `filename`     VARCHAR(255) NOT NULL,
                                                   `media_kind`   VARCHAR(20)  NOT NULL,  -- IMAGE | VIDEO
                                                   `sort_order`   INT          NOT NULL DEFAULT 0,
                                                   PRIMARY KEY (`id`),
                                                   KEY `idx_career_task_media_task` (`task_id`),
                                                   CONSTRAINT `fk_career_task_media_task` FOREIGN KEY (`task_id`)
                                                       REFERENCES `career_project_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 학력
CREATE TABLE IF NOT EXISTS `education` (
                                           `id`           BIGINT       NOT NULL AUTO_INCREMENT,
                                           `period_label` VARCHAR(100) NOT NULL,
                                           `school`       VARCHAR(255) NOT NULL,
                                           `major`        VARCHAR(255) NOT NULL,
                                           `sort_order`   INT          NOT NULL DEFAULT 0,
                                           PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- ── 시드 데이터 (PersonaPortfolioPage.tsx 에 하드코딩돼 있던 내용을 그대로 이전) ──

-- developer_profile 은 단일 행만 쓴다 — 테이블이 비어 있을 때만 시드한다(재실행해도 중복 안 됨).
INSERT INTO `developer_profile` (`elevator_pitch`, `highlights`, `headline`, `subheadline`, `role_tagline`, `focus_tags`, `contact_blurb`, `side_project`, `availability`, `open_to_work`, `phone`, `github`, `blog`)
SELECT
    'TNS Soft에서 음성인식(STT) 도메인의 백엔드와 인프라를 담당하고 있습니다.',
    '카카오T 주차장 연동 API 서버를 클라우드 방식에서 현장 로컬 서버 구조로 단독 전환\nKT STT 모델 평가용 음성 DB 구축 프로젝트 PM 총괄 — 기관 협약부터 녹음 프로세스까지\ngRPC 계약(sttservice.proto) 기준으로 C++ 레퍼런스 클라이언트와 Java 프로덕션 구현을 함께 설계',
    '음성 데이터가 서버와 엔진 사이를\n{{끊기지 않고}} 흐르게 만듭니다.',
    '웹 프론트엔드로 시작해 주차 시스템 백엔드, SIEM, 그리고 지금은 STT·gRPC·MRCP 기반 음성인식 인프라까지 — 도메인을 넓혀가며 8년 3개월째 만들고 운영하고 있습니다.',
    'Backend Developer',
    'STT,gRPC,MRCP',
    '음성인식 백엔드, 데이터 파이프라인, 인프라 운영에 관심 있는 팀이라면 언제든 편하게 연락 주세요.',
    'LifeMetrics',
    '이직 준비 중',
    1,
    '010-XXXX-XXXX',
    'https://github.com/ShinJiHun',
    'https://tho881.tistory.com/'
WHERE NOT EXISTS (SELECT 1 FROM `developer_profile`);

-- 기존 배포에 이미 developer_profile 행이 있는 경우를 위한 headline/subheadline/role_tagline/focus_tags/contact_blurb/side_project 백필 (재실행해도 안전)
UPDATE `developer_profile` SET `headline` = '음성 데이터가 서버와 엔진 사이를\n{{끊기지 않고}} 흐르게 만듭니다.' WHERE `headline` IS NULL;
UPDATE `developer_profile` SET `subheadline` = '웹 프론트엔드로 시작해 주차 시스템 백엔드, SIEM, 그리고 지금은 STT·gRPC·MRCP 기반 음성인식 인프라까지 — 도메인을 넓혀가며 8년 3개월째 만들고 운영하고 있습니다.' WHERE `subheadline` IS NULL;
UPDATE `developer_profile` SET `role_tagline` = 'Backend Developer' WHERE `role_tagline` IS NULL;
UPDATE `developer_profile` SET `focus_tags` = 'STT,gRPC,MRCP' WHERE `focus_tags` IS NULL;
UPDATE `developer_profile` SET `contact_blurb` = '음성인식 백엔드, 데이터 파이프라인, 인프라 운영에 관심 있는 팀이라면 언제든 편하게 연락 주세요.' WHERE `contact_blurb` IS NULL;
UPDATE `developer_profile` SET `side_project` = 'LifeMetrics' WHERE `side_project` IS NULL;
UPDATE `developer_profile` SET `availability` = '이직 준비 중' WHERE `availability` IS NULL;
UPDATE `developer_profile` SET `open_to_work` = 1 WHERE `open_to_work` IS NULL;

-- 소개 섹션 시드 — 테이블이 비어 있을 때만 삽입한다(재실행해도 중복 안 됨).
INSERT INTO `profile_intro_section` (`subtitle`, `lines`, `sort_order`)
SELECT `subtitle`, `lines`, `sort_order` FROM (
    SELECT '커리어' AS `subtitle`, '2018년 프론트엔드 개발로 시작해서, 주차 시스템 백엔드 API 서버 개발, SIEM 솔루션 풀스택 개발을 거쳐 현재는 TNS Soft에서 음성인식(STT) 도메인의 백엔드와 인프라를 담당하고 있습니다. gRPC 양방향 스트리밍, MRCP 표준 프로토콜, Asterisk 텔레포니 연동처럼 프로토콜 레이어부터 직접 설계하는 일에 강점이 있습니다.' AS `lines`, 0 AS `sort_order`
    UNION ALL SELECT '일하는 방식', '맡은 시스템은 끝까지 책임지는 편입니다. 상용 스마트녹취백업 시스템을 OpenShift·Podman 기반으로 상시 운영하며 장애를 미리 막는 일, KT 음성인식 모델 평가용 음성 DB를 노인·유아 대상 기관과 직접 협의해 구축하고 총괄한 일 모두 "만들고 끝"이 아니라 "돌아가게 만드는" 데 방점을 찍은 결과입니다.', 1
    UNION ALL SELECT '사이드 프로젝트', '업무 외 시간에는 라이딩과 운동을 즐기고, 그 데이터를 직접 설계한 개인 플랫폼(LifeMetrics)으로 관리합니다. 지금 보고 있는 이 페이지도 그 일부입니다.', 2
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `profile_intro_section`);

INSERT INTO `career_company` (`id`, `path`, `domain`, `company_name`, `short_name`, `period_label`, `start_date`, `end_date`, `role`, `is_current`, `commit_hash`, `commit_tag`, `stack`, `sort_order`) VALUES
                                                                                                                                                                    (1, '~/career/tns-soft', '음성인식', '㈜티엔에스소프트 (TNS Soft)', 'TNS Soft', '2024.04 ~ 2026.08 · 2년 5개월', '2024-04-01', '2026-08-14', '개발팀 · 과장', 0, 'a4f0e2c', 'HEAD → main', 'OpenShift,Podman,Linux,gRPC,MRCP,Asterisk,UniMRCP,C/C++,Java · Spring Boot,Vue 3,Electron,Android,STT,Project Management', 0),
                                                                                                                                                                    (2, '~/career/cyberone', '보안(SIEM)', '㈜싸이버원', '싸이버원', '2023.04 ~ 2024.04 · 1년 1개월', '2023-04-01', '2024-04-01', 'SIEM 개발팀 · 과장/팀원', 0, '7c19b83', NULL, 'Frontend,Backend,SIEM', 1),
                                                                                                                                                                    (3, '~/career/rs-solutions', '주차 시스템', '알에스솔루션즈 → KMParking 합병', 'KMParking', '2020.11 ~ 2023.03 · 2년 5개월', '2020-11-01', '2023-03-01', '개발팀/TFT팀 · 대리', 0, '2f8ad61', NULL, 'Java,Spring Framework/Boot,JQuery,MariaDB,RESTful API,TCP/IP Socket,WebSocket,PBX,RS232C,Android', 2),
                                                                                                                                                                    (4, '~/career/somansa', '솔루션 개발', '㈜소만사', '소만사', '2020.07 ~ 2020.10 · 4개월', '2020-07-01', '2020-10-01', '솔루션 개발팀 · 대리', 0, 'e91d345', NULL, 'Frontend', 3),
                                                                                                                                                                    (5, '~/career/mediazen', '음성인식', '미디어젠 · 첫 회사', '미디어젠', '2018.06 ~ 2020.06 · 2년 1개월', '2018-06-01', '2020-06-01', 'APP개발팀/SDS개발팀 · 사원', 0, '1a0c9f2', 'root commit', 'Java,C++,Spring Framework,JQuery,JavaScript,HTML/CSS,RESTful API,IPC 통신,Jenkins,SVN', 4);

-- 기존 배포에 이미 career_company 행이 있는 경우를 위한 domain/short_name/start_date/end_date 백필 (재실행해도 안전)
UPDATE `career_company` SET `domain` = '음성인식' WHERE `id` = 1 AND `domain` IS NULL;
UPDATE `career_company` SET `domain` = '보안(SIEM)' WHERE `id` = 2 AND `domain` IS NULL;
UPDATE `career_company` SET `domain` = '주차 시스템' WHERE `id` = 3 AND `domain` IS NULL;
UPDATE `career_company` SET `domain` = '솔루션 개발' WHERE `id` = 4 AND `domain` IS NULL;
UPDATE `career_company` SET `domain` = '음성인식' WHERE `id` = 5 AND `domain` IS NULL;

UPDATE `career_company` SET `short_name` = 'TNS Soft'  WHERE `id` = 1 AND `short_name` IS NULL;
UPDATE `career_company` SET `short_name` = '싸이버원'   WHERE `id` = 2 AND `short_name` IS NULL;
UPDATE `career_company` SET `short_name` = 'KMParking' WHERE `id` = 3 AND `short_name` IS NULL;
UPDATE `career_company` SET `short_name` = '소만사'     WHERE `id` = 4 AND `short_name` IS NULL;
UPDATE `career_company` SET `short_name` = '미디어젠'   WHERE `id` = 5 AND `short_name` IS NULL;

UPDATE `career_company` SET `start_date` = '2024-04-01', `end_date` = '2026-08-14' WHERE `id` = 1 AND `start_date` IS NULL;
UPDATE `career_company` SET `start_date` = '2023-04-01', `end_date` = '2024-04-01' WHERE `id` = 2 AND `start_date` IS NULL;
UPDATE `career_company` SET `start_date` = '2020-11-01', `end_date` = '2023-03-01' WHERE `id` = 3 AND `start_date` IS NULL;
UPDATE `career_company` SET `start_date` = '2020-07-01', `end_date` = '2020-10-01' WHERE `id` = 4 AND `start_date` IS NULL;
UPDATE `career_company` SET `start_date` = '2018-06-01', `end_date` = '2020-06-01' WHERE `id` = 5 AND `start_date` IS NULL;
UPDATE `career_company` SET `end_date` = '2026-08-14' WHERE `id` = 1 AND `end_date` IS NULL; -- TNS Soft 퇴사(2026-08-14)
UPDATE `career_company` SET `is_current` = 0 WHERE `is_current` = 1;                          -- 현재 재직중인 회사 없음

-- TNS Soft
INSERT INTO `career_project` (`company_id`, `title`, `period_label`, `paragraphs`, `sort_order`) VALUES
                                                                                                     (1, '스마트녹취백업 시스템 운영 및 인프라 유지보수', '2024.04 ~ 현재',
                                                                                                      '상용 스마트녹취백업 시스템의 STT 연동 모듈 및 백엔드 서비스를 상시 운영합니다.\n\nOpenShift·Podman 기반 컨테이너 인프라를 배포·모니터링하고, 일일 점검 프로세스로 장애를 예방합니다.', 0),
                                                                                                     (1, 'MRCP 기반 SCU 음성인식 LLM 연동 아키텍처 설계', '2025.12 ~ 2026.01',
                                                                                                      'KT SCU 음성인식 엔진에 LLM 응답을 연동하는 파이프라인을 설계했습니다. SIP Client → Asterisk Dialplan → UniMRCP → SCU Plugin → KT SCU 서버로 이어지는 전체 흐름을 구성했습니다.\n\nSCU Plugin은 scu_recog_engine.c(MRCP 프로토콜) → ScuAdapterWrapper.cpp(C/C++ 브릿지) → ScuAdapter.cpp(비즈니스 로직) 3계층으로 역할을 분리했고, libcurl로 LLM 서버에 HTTP POST 요청 후 콜백으로 결과를 engine.c까지 되돌려 NLSML 응답을 생성하도록 구현했습니다.', 1),
                                                                                                     (1, 'Ai-Subtitle — 실시간 AI 자막 생성 엔진 (gRPC STT 통합)', '2025.08 ~ 2025.09',
                                                                                                      '동일한 sttservice.proto 계약을 공유하는 C++ 레퍼런스 클라이언트와 Java 프로덕션 구현을 함께 개발했습니다.\n\nJava 쪽은 Spring Boot 백엔드 + Vue3/Electron 프론트로, 세션ID별 ConcurrentMap으로 스트림을 다중화해 여러 사용자의 실시간 자막을 동시에 처리하도록 설계했습니다. 실시간(gRPC 스트리밍) · 화자분리(REST 폴링) · C++ CLI 검증 경로까지 3가지 데이터 흐름을 각각 구현했습니다.', 2),
                                                                                                     (1, 'gRPC 기반 음성 통신 안드로이드 STT 샘플 앱 개발', '2025.08 ~ 2025.09',
                                                                                                      '향후 KT 프로젝트의 gRPC 적용 가능성에 대비해, 안드로이드-STT 엔진 간 저지연 스트리밍 통신을 선제적으로 검증한 프로토타입을 개발했습니다.', 3),
                                                                                                     (1, 'MRCP 기반 사내 STT 연동 및 Asterisk 콜센터 연동 샘플 개발', '2025.12 ~ 2026.01',
                                                                                                      '콜센터 솔루션 확장을 대비해 MRCP 서버와 Asterisk 텔레포니 서버를 구축하고, 사내 STT 엔진과 연동해 테스트용 안드로이드 앱까지 만들었습니다.', 4),
                                                                                                     (1, 'KT 음성인식 STT 모델 평가용 음성 DB 구축 및 PM 수행', '2026.06 ~ 2026.08',
                                                                                                      '노인·유아 음성 데이터 수집 프로젝트를 총괄했습니다. 노인회·유아 관련 기관을 직접 발굴해 협약을 주도하고, 녹음 프로세스 기획부터 일정·품질 관리, 현장 이슈 대응까지 담당했습니다.\n\n고령층·아동 특유의 음성 패턴을 반영한 데이터셋 구축으로 STT 모델의 인식률 평가·고도화에 기여했습니다.', 5);

-- 싸이버원
INSERT INTO `career_project` (`company_id`, `title`, `period_label`, `paragraphs`, `sort_order`) VALUES
    (2, 'SIEM 솔루션 프론트엔드 · 백엔드 개발 및 유지보수', '2023.04 ~ 2024.04',
     '보안 이벤트를 수집·분석하는 SIEM(Security Information and Event Management) 솔루션의 화면과 서버 로직을 개발하고 운영 중 발생하는 이슈를 유지보수했습니다.', 0);

-- 알에스솔루션즈 → KMParking 합병
INSERT INTO `career_project` (`company_id`, `title`, `period_label`, `paragraphs`, `sort_order`) VALUES
                                                                                                     (3, '카카오T 안드로이드 무인정산기 API 서버 개발', '2022.07 ~ 2023.01',
                                                                                                      '카카오T 주차장에서 사용하는 안드로이드 무인정산기와 연동되는 API 서버를 개발했습니다. 기존 클라우드 서버 방식을 각 현장 로컬 서버 구조로 대체하기 위한 로컬 전용 API를 직접 설계했습니다.', 0),
                                                                                                     (3, '아마노 코리아 무인정산기 개발', '2021.02 ~ 2021.07',
                                                                                                      '주차회사 아마노 코리아 의뢰로 WebView 기반 모바일 웹 무인정산기 안드로이드 앱을 개발했습니다.\n\nVAN사 연동 카드 결제 시스템, PBX 서버 기반 VOIP 연동, 영수증 출력기·할인권 리더기용 RS232C 시리얼통신 API를 구현했습니다.', 1),
                                                                                                     (3, '대구 스마트시티 주차 공유 플랫폼 미들웨어 개발', '2021.10 ~ 2022.03',
                                                                                                      '주차장마다 관제 솔루션 업체가 제각각인 문제를 해결하기 위해, 각 현장 API와 대구시 스마트시티 공유 플랫폼 API를 연동하는 미들웨어 서버를 개발했습니다. WebSocket과 RESTful API를 함께 사용했습니다.', 2),
                                                                                                     (3, '이기종 통합 주차관제 솔루션 · 장애인 주차면 관리 시스템', '2020.11 ~ 2023.03',
                                                                                                      '모든 종류의 주차장비와 연동 가능한 통합 주차관제 솔루션의 백엔드·프론트엔드를 개발하고, 현장별 커스터마이징을 진행했습니다. 아마노 코리아의 장애인 주차면 관리 시스템에서는 소형 카메라 센서의 이미지 데이터를 LPR 서버로 전달하는 통신 모듈을 개발했습니다.', 3),
                                                                                                     (3, '주차 무인정산기 연동 서버 개발 및 주차장 이슈 장애대응', '2020.11 ~ 2023.03',
                                                                                                      '운영 중인 주차장 현장에서 발생하는 정산기·API 연동 장애를 상시 대응했습니다.', 4);

-- 소만사
INSERT INTO `career_project` (`company_id`, `title`, `period_label`, `paragraphs`, `sort_order`) VALUES
    (4, '솔루션 프론트엔드 개발', '2020.07 ~ 2020.10', '사내 솔루션 제품의 프론트엔드 화면을 개발했습니다.', 0);

-- 미디어젠
INSERT INTO `career_project` (`company_id`, `title`, `period_label`, `paragraphs`, `sort_order`) VALUES
                                                                                                     (5, '노랑풍선 여행사 챗봇 시스템 프론트엔드 개발', '2018.07 ~ 2019.02',
                                                                                                      '기존 상담 업무 일부를 대행하는 AI 기반 챗봇의 프론트엔드를 개발했습니다. 음성인식 솔루션 API를 연동해 STT(Speech To Text) 서비스를 제공했습니다.', 0),
                                                                                                     (5, '폭스바겐(VW) AVN Speech Adaptor 개발', '2019.04 ~ 2020.03',
                                                                                                      '차량용 AVN 시스템에 적용되는 Speech Adaptor를 C++/Linux 환경에서 개발했습니다. 중국 음성인식 엔진사의 인식 결과를 IPC 통신으로 전달받아, 차량용 AVN에 연동된 모든 파트에 REST API로 전달하는 역할을 했습니다.', 1);

INSERT INTO `education` (`period_label`, `school`, `major`, `sort_order`) VALUES
                                                                              ('2010 ~ 2016', '한림대학교 · 학사', '융합소프트웨어학과', 0),
                                                                              ('2016 ~ 2018', '한림대학교 · 석사', '융합소프트웨어학과 — 청취 음량 수집 애플리케이션 연구', 1);

-- ── 개인 프로젝트(05 섹션) — 상세 시드/마이그레이션은 migrate_portfolio_projects.sql 참고 ──

CREATE TABLE IF NOT EXISTS `personal_project` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `kind`         VARCHAR(20)  NOT NULL,               -- FEATURED | MINI
    `title`        VARCHAR(255) NOT NULL,
    `blurb`        TEXT         DEFAULT NULL,
    `repo_url`     VARCHAR(255) DEFAULT NULL,
    `period_label` VARCHAR(255) DEFAULT NULL,
    `tags`         TEXT         DEFAULT NULL,           -- 쉼표(,) 구분
    `sort_order`   INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `personal_project_feature` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`  BIGINT       NOT NULL,
    `icon`        VARCHAR(16)  DEFAULT NULL,
    `title`       VARCHAR(255) NOT NULL,
    `description` TEXT         DEFAULT NULL,
    `tags`        TEXT         DEFAULT NULL,            -- 쉼표(,) 구분
    `sort_order`  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_ppf_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `portfolio_troubleshoot` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `ref_label`     VARCHAR(40)  DEFAULT NULL,
    `title`         VARCHAR(255) NOT NULL,
    `removed_lines` TEXT         DEFAULT NULL,          -- 줄바꿈(\n) 구분
    `added_lines`   TEXT         DEFAULT NULL,          -- 줄바꿈(\n) 구분
    `sort_order`    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `portfolio_dependency` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `category`   VARCHAR(100) NOT NULL,
    `dep_key`    VARCHAR(150) NOT NULL,
    `note`       TEXT         DEFAULT NULL,
    `sort_order` INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;