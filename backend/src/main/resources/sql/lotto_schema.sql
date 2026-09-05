-- lotto_schema.sql
-- 로또 페이지용 DB 스키마. riding_db/journal_db와 같은 MariaDB 인스턴스에
-- 별도 스키마(lotto_db)로 생성한다 (LottoDataSourceConfig 참고).
--
-- 적용:
--   mysql -h 127.0.0.1 -P 3307 -u <DB_USER> -p < backend/src/main/resources/sql/lotto_schema.sql

CREATE DATABASE IF NOT EXISTS lotto_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE lotto_db;

-- 회차별 당첨번호 (동행복권 공개 API로 백필: POST /api/lotto/admin/sync)
CREATE TABLE IF NOT EXISTS lotto_number (
    id      INT PRIMARY KEY,         -- 회차 (round)
    num1    INT NOT NULL,
    num2    INT NOT NULL,
    num3    INT NOT NULL,
    num4    INT NOT NULL,
    num5    INT NOT NULL,
    num6    INT NOT NULL,
    bonus   INT NOT NULL,
    no_date DATE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 회차별로 내가 고른/추천받은 번호 (자동 생성 추천 포함)
CREATE TABLE IF NOT EXISTS lotto_recommend (
    id       BIGINT PRIMARY KEY AUTO_INCREMENT,
    round    INT NOT NULL,
    game_no  INT NOT NULL,
    n1       INT NOT NULL,
    n2       INT NOT NULL,
    n3       INT NOT NULL,
    n4       INT NOT NULL,
    n5       INT NOT NULL,
    n6       INT NOT NULL,
    INDEX idx_lotto_recommend_round (round)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 실제로 구매한 로또 용지(사진)를 OCR로 읽어 저장한 기록.
-- 한 장의 용지에 A~E 최대 5게임이 있을 수 있어 game_no로 구분하고,
-- 같은 용지에서 나온 게임들은 ticket_group으로 묶는다.
CREATE TABLE IF NOT EXISTS lotto_ticket (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_group  VARCHAR(36)  NOT NULL,   -- 같은 용지 사진에서 나온 게임 묶음(UUID)
    round         INT              NULL,   -- 용지에 인쇄된 회차 (인식 실패 시 NULL → 추후 수동 보정)
    game_no       INT          NOT NULL,   -- 용지 내 게임 순번 (1=A, 2=B, ...)
    n1            INT          NOT NULL,
    n2            INT          NOT NULL,
    n3            INT          NOT NULL,
    n4            INT          NOT NULL,
    n5            INT          NOT NULL,
    n6            INT          NOT NULL,
    source        VARCHAR(20)  NOT NULL DEFAULT 'OCR',  -- OCR | MANUAL
    image_path    VARCHAR(500)     NULL,   -- NAS에 저장된 원본 사진 경로
    purchased_at  DATE             NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_lotto_ticket_round (round),
    INDEX idx_lotto_ticket_group (ticket_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
