package com.lifemetrics.backend.health.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDate;

/**
 * food_daily_summary 를 그날 food_log 전체(출처 무관)로 다시 만든다.
 * <p>
 * food_daily_summary 는 (user_id, log_date) 당 한 줄인 "그날 합계"라서, Health Connect 동기화와
 * 식단 저장 API 가 같은 날 써도 누가 마지막에 계산하든 결과가 같다.
 * data_source 는 출처가 하나면 그 값, 섞이면 'mixed'. 남은 기록이 없으면 요약을 지운다.
 * <p>
 * JPA 로 쓴 직후에 부르면 호출하는 쪽이 먼저 flush 해야 이 SQL 이 바뀐 행을 본다.
 */
@Service
public class FoodDailySummaryService {

    private final NamedParameterJdbcTemplate jdbc;

    public FoodDailySummaryService(@Qualifier("ridingDataSource") DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Transactional
    public void recompute(long userId, LocalDate date) {
        var p = new MapSqlParameterSource().addValue("u", userId).addValue("d", date);
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM food_log WHERE user_id = :u AND log_date = :d", p, Integer.class);
        if (rows == null || rows == 0) {
            jdbc.update("DELETE FROM food_daily_summary WHERE user_id = :u AND log_date = :d", p);
            return;
        }
        jdbc.update("""
                INSERT INTO food_daily_summary
                    (user_id, log_date, total_calories, total_carbs, total_protein, total_fat,
                     total_sodium, total_sugar, total_dietary_fiber, meal_count, data_source)
                SELECT :u, :d,
                       COALESCE(SUM(calories), 0), COALESCE(SUM(carbs), 0),
                       COALESCE(SUM(protein), 0), COALESCE(SUM(fat), 0),
                       SUM(sodium), SUM(sugar), SUM(dietary_fiber),
                       COUNT(DISTINCT CASE WHEN meal_type <> 'unknown' THEN meal_type END),
                       CASE WHEN COUNT(DISTINCT COALESCE(data_source, '')) = 1 THEN MAX(data_source)
                            ELSE 'mixed' END
                FROM food_log WHERE user_id = :u AND log_date = :d
                ON DUPLICATE KEY UPDATE
                    total_calories = VALUES(total_calories), total_carbs = VALUES(total_carbs),
                    total_protein = VALUES(total_protein), total_fat = VALUES(total_fat),
                    total_sodium = VALUES(total_sodium), total_sugar = VALUES(total_sugar),
                    total_dietary_fiber = VALUES(total_dietary_fiber), meal_count = VALUES(meal_count),
                    data_source = VALUES(data_source), updated_at = CURRENT_TIMESTAMP
                """, p);
    }
}
