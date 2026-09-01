-- 포트폴리오 "개인 프로젝트"(05 섹션) DB 이전 (journal_db, ddl-auto=none)
--   mysql -h 127.0.0.1 -P 3307 -u tho881 -p journal_db < migrate_portfolio_projects.sql
-- 모든 문장은 재실행해도 안전하다(테이블/행이 이미 있으면 건너뜀).

USE `journal_db`;

-- 1) 테이블
CREATE TABLE IF NOT EXISTS `personal_project` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `kind`         VARCHAR(20)  NOT NULL,               -- FEATURED | MINI
    `title`        VARCHAR(255) NOT NULL,
    `blurb`        TEXT         DEFAULT NULL,
    `repo_url`     VARCHAR(255) DEFAULT NULL,           -- 비면 contact.github 사용
    `period_label` VARCHAR(255) DEFAULT NULL,           -- MINI 카드용
    `tags`         TEXT         DEFAULT NULL,           -- 쉼표(,) 구분
    `sort_order`   INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `personal_project_feature` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`  BIGINT       NOT NULL,
    `icon`        VARCHAR(16)  DEFAULT NULL,            -- 이모지 1개
    `title`       VARCHAR(255) NOT NULL,
    `description` TEXT         DEFAULT NULL,
    `tags`        TEXT         DEFAULT NULL,            -- 쉼표(,) 구분
    `sort_order`  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_ppf_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `portfolio_troubleshoot` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `ref_label`     VARCHAR(40)  DEFAULT NULL,          -- 예: "#42"
    `title`         VARCHAR(255) NOT NULL,
    `removed_lines` TEXT         DEFAULT NULL,          -- 줄바꿈(\n) 구분 — diff '-'
    `added_lines`   TEXT         DEFAULT NULL,          -- 줄바꿈(\n) 구분 — diff '+'
    `sort_order`    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `portfolio_dependency` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `category`   VARCHAR(100) NOT NULL,                 -- 그룹 헤더
    `dep_key`    VARCHAR(150) NOT NULL,                 -- 따옴표 없이 저장
    `note`       TEXT         DEFAULT NULL,
    `sort_order` INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 2) 시드 (PersonaPortfolioPage.tsx 05 섹션에 하드코딩돼 있던 내용)
INSERT INTO `personal_project` (`kind`, `title`, `blurb`, `repo_url`, `period_label`, `tags`, `sort_order`)
SELECT * FROM (
    SELECT 'FEATURED' AS `kind`,
           'LifeMetrics' AS `title`,
           '라이드·체성분·코스 데이터를 하나의 파이프라인으로 묶는 개인 사이클링 데이터 플랫폼 — 지금 보고 있는 이 페이지도 이 프로젝트의 일부입니다.' AS `blurb`,
           NULL AS `repo_url`, NULL AS `period_label`, NULL AS `tags`, 0 AS `sort_order`
    UNION ALL SELECT 'MINI', '청취 음량 수집 애플리케이션',
           '안드로이드 스마트폰에 이어폰을 연결해 음악을 청취하는 동안의 청취 음압(dBA)을 측정하는 앱을 개발했습니다. 시간별·일별·주별·월별 평균 청취 음량을 집계해 청력 손상 위험을 스스로 확인할 수 있게 했습니다.',
           NULL, '대학원 논문 주제 · 2016 ~ 2018', 'Java,Tomcat,MySQL,JQuery', 1
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `personal_project`);

INSERT INTO `personal_project_feature` (`project_id`, `icon`, `title`, `description`, `tags`, `sort_order`)
SELECT (SELECT `id` FROM `personal_project` WHERE `kind` = 'FEATURED' ORDER BY `id` LIMIT 1),
       s.`icon`, s.`title`, s.`description`, s.`tags`, s.`sort_order`
FROM (
    SELECT '📐' AS `icon`, '코스-라이드 비교' AS `title`,
           '같은 코스를 여러 번 탄 기록을 이동시간·평균속도·상승고도·평균 파워/심박/케이던스로 비교합니다. JPQL 프로젝션으로 DTO를 직접 구성했습니다.' AS `description`,
           'CourseRideComparisonDto,JPQL' AS `tags`, 0 AS `sort_order`
    UNION ALL SELECT '🚲', '자전거 장비 관리',
           '모델명 검색(웹 검색), 스펙 이미지 업로드(비전), 제품 URL(web_fetch) 세 가지 방식으로 자전거를 등록합니다.',
           'Claude Vision,web_fetch', 1
    UNION ALL SELECT '🧭', '퍼소나 게이트웨이',
           '방문자가 개발/라이더/휴먼 중 원하는 페르소나를 골라 각기 다른 DB 기반 데이터로 채팅할 수 있는 라우팅 구조입니다. 지금 이 페이지가 그 결과물입니다.',
           'PersonaGate,Gemini · Vertex AI', 2
) AS s
WHERE NOT EXISTS (SELECT 1 FROM `personal_project_feature`)
  AND EXISTS (SELECT 1 FROM `personal_project` WHERE `kind` = 'FEATURED');

INSERT INTO `portfolio_troubleshoot` (`ref_label`, `title`, `removed_lines`, `added_lines`, `sort_order`)
SELECT * FROM (
    SELECT '#42' AS `ref_label`, '활동 42건의 자전거가 뒤바뀌어 있었다' AS `title`,
           'activity_core의 bike_id 42건이 실제로 탄 자전거와 다르게 기록돼 있었습니다.\n원인: 인제스천 단계에서 FIT 타임스탬프의 월/일이 뒤바뀐 채 파싱되고 있었습니다.' AS `removed_lines`,
           '백업 테이블 생성 후 UPDATE로 42건을 정정하고, 저장 필드인 bike.total_distance를 수동 재계산했습니다.\n"자동 계산될 것 같은 필드"는 스키마를 직접 확인하기 전까지 가정하지 않기로 했습니다.' AS `added_lines`,
           0 AS `sort_order`
    UNION ALL SELECT '#null', 'null 허용 필드에서 생성자 표현식이 깨졌다',
           'nullable DOUBLE 컬럼을 JPA 엔티티에 primitive int로 매핑했더니 생성자 표현식에서 예외가 났습니다.\nDB 스키마를 재조회해 해당 컬럼이 NULL을 허용한다는 걸 확인했습니다.',
           '필드 타입을 Double로 바꿔 null을 그대로 표현했습니다.\n엔티티 작성 전 nullable 여부를 먼저 확인하는 걸 원칙으로 삼았습니다.', 1
    UNION ALL SELECT '#weather', '날씨 API가 뒤섞인 테스트 데이터를 주고 있었다',
           'Windy API 무료 티어 연동 후 실제 관측값과 맞지 않는 값이 계속 나왔습니다.\n같은 좌표·시각으로 반복 요청해보니 셔플된 테스트 데이터를 반환한다는 걸 확인했습니다.',
           'API 키 없이 실제 GFS/ECMWF/기상청 데이터를 제공하는 Open-Meteo로 전환했습니다.\n무료 티어 응답을 눈으로 검증하는 단계를 체크리스트에 추가했습니다.', 2
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `portfolio_troubleshoot`);

INSERT INTO `portfolio_dependency` (`category`, `dep_key`, `note`, `sort_order`)
SELECT * FROM (
    SELECT 'backend — package.json' AS `category`, 'spring-boot + jpa' AS `dep_key`, '관계형 조인이 많은 도메인, JPQL 프로젝션으로 DTO 직접 구성' AS `note`, 0 AS `sort_order`
    UNION ALL SELECT 'backend — package.json', 'mariadb@11.6 / 10.4', 'riding_db · journal_db를 분리해 스키마 변경 영향 범위를 좁힘', 1
    UNION ALL SELECT 'backend — package.json', 'python + uvicorn', 'FIT 파싱은 파이썬 생태계가 압도적으로 풍부', 2
    UNION ALL SELECT 'ai-integration', 'claude-api', 'InBody OCR, 자전거 스펙 추출 등 비정형 입력 처리', 3
    UNION ALL SELECT 'ai-integration', 'gemini + vertex-ai(adc)', '개인 문서 RAG — AI Studio 키 대신 ADC로 학습 데이터 사용 방지', 4
    UNION ALL SELECT 'infra', 'gcp-vm + docker + nginx', '단일 VM 컨테이너 운영, deploy.sh로 배포 표준화', 5
    UNION ALL SELECT 'infra', 'open-meteo', 'Windy 무료 티어가 셔플 데이터 반환 확인 후 교체', 6
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `portfolio_dependency`);
