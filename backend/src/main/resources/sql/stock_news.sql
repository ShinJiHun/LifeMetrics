-- 증권 페르소나: 보유 종목 소식(뉴스·공시) (2026-11-27) — journal_db
-- 수집은 data_pipeline 이 한다(네이버 뉴스, DART 공시). 백엔드는 읽기만 한다.
-- 보유 종목(riding_db.invest_snapshot)과는 DB 가 달라 조인하지 않고 symbol 값으로만 맞춘다.
-- 여러 번 실행해도 안전하다.

-- market: KR | US, source: NAVER_NEWS | DART
-- published_at / collected_at 은 KST.
-- url 은 같은 기사 중복 저장을 막는 키. 인덱스 길이 제한(3072바이트) 때문에 700자, 대소문자 구분(bin).
-- dart_rcept_no: DART 접수번호(공시만, 14자리)
CREATE TABLE IF NOT EXISTS `journal_db`.`stock_news` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `market`        VARCHAR(4)    NOT NULL,
    `symbol`        VARCHAR(20)   NOT NULL,
    `name`          VARCHAR(200)  DEFAULT NULL,
    `source`        VARCHAR(16)   NOT NULL,
    `title`         VARCHAR(500)  NOT NULL,
    `summary`       TEXT          DEFAULT NULL,
    `url`           VARCHAR(700)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    `publisher`     VARCHAR(100)  DEFAULT NULL,
    `published_at`  DATETIME      NOT NULL,
    `collected_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `dart_rcept_no` VARCHAR(20)   DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_stock_news_url` (`url`),
    KEY `idx_stock_news_symbol_pub` (`symbol`, `published_at`),
    KEY `idx_stock_news_pub` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
