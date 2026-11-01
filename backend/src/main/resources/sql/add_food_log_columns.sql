-- food_log 에 식품영양성분DB 연결 정보(식품코드·제조사·섭취량)를 추가한다.
-- Schema: riding_db / Table: food_log
-- ddl-auto=none 이므로 운영 DB 에 수동으로 적용한다(멱등: 여러 번 실행해도 안전).

ALTER TABLE `riding_db`.`food_log`
    ADD COLUMN IF NOT EXISTS `food_cd`     VARCHAR(40)  NULL COMMENT 'food_nutrition_master.food_cd' AFTER `food_name`,
    ADD COLUMN IF NOT EXISTS `maker_name`  VARCHAR(200) NULL COMMENT '제조사' AFTER `food_cd`,
    ADD COLUMN IF NOT EXISTS `amount`      DOUBLE       NULL COMMENT '섭취량' AFTER `maker_name`,
    ADD COLUMN IF NOT EXISTS `amount_unit` VARCHAR(10)  NULL COMMENT 'g | mL' AFTER `amount`;
