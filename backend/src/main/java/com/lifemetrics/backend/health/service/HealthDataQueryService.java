package com.lifemetrics.backend.health.service;

import com.lifemetrics.backend.health.dto.HealthDataDto.DailyActivity;
import com.lifemetrics.backend.health.dto.HealthDataDto.ExerciseSession;
import com.lifemetrics.backend.health.dto.HealthDataDto.NutritionEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Health Connect 동기화로 저장된 활동량·운동·식단을 날짜 범위(KST, 양끝 포함)로 읽는다. 결과는 시간 오름차순. */
@Service
public class HealthDataQueryService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 400;

    private static final String NUTRITION_SELECT = """
            SELECT hc_record_id, log_date, meal_type, food_name, eat_time,
                   calories, carbs, protein, fat, sodium, sugar, dietary_fiber
            FROM food_log
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public HealthDataQueryService(@Qualifier("ridingDataSource") DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    public record DateRange(LocalDate from, LocalDate to) {}

    /** 생략된 끝을 채우고 검증한다. 둘 다 없으면 오늘까지 최근 30일. */
    public static DateRange resolveRange(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = to != null ? to : (from != null ? from.plusDays(DEFAULT_DAYS - 1) : today);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("from 은 to 보다 늦을 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("조회 기간은 " + MAX_DAYS + "일까지입니다.");
        }
        return new DateRange(start, end);
    }

    public List<DailyActivity> dailyActivity(long userId, DateRange range) {
        return jdbc.query("""
                SELECT activity_date, steps, distance_m, active_kcal, total_kcal,
                       exercise_minutes, sleep_minutes, resting_heart_rate
                FROM hc_daily_activity
                WHERE user_id = :u AND activity_date BETWEEN :from AND :to
                ORDER BY activity_date
                """, params(userId, range), (rs, i) -> new DailyActivity(
                rs.getDate("activity_date").toLocalDate(),
                rs.getObject("steps", Long.class),
                nullableDouble(rs, "distance_m"),
                nullableDouble(rs, "active_kcal"),
                nullableDouble(rs, "total_kcal"),
                rs.getObject("exercise_minutes", Integer.class),
                rs.getObject("sleep_minutes", Integer.class),
                rs.getObject("resting_heart_rate", Integer.class)));
    }

    public List<ExerciseSession> exercises(long userId, DateRange range) {
        // start_time 인덱스를 타도록 날짜 함수 대신 시각 범위로 비교한다
        return jdbc.query("""
                SELECT hc_record_id, exercise_type, title, start_time, end_time, duration_min,
                       active_kcal, distance_m, avg_heart_rate, source_app
                FROM hc_exercise_session
                WHERE user_id = :u AND start_time >= :from AND start_time < :toExclusive
                ORDER BY start_time
                """, params(userId, range), (rs, i) -> new ExerciseSession(
                rs.getString("hc_record_id"),
                rs.getString("exercise_type"),
                rs.getString("title"),
                rs.getTimestamp("start_time").toLocalDateTime(),
                rs.getTimestamp("end_time").toLocalDateTime(),
                rs.getInt("duration_min"),
                nullableDouble(rs, "active_kcal"),
                nullableDouble(rs, "distance_m"),
                rs.getObject("avg_heart_rate", Integer.class),
                rs.getString("source_app")));
    }

    public List<NutritionEntry> nutrition(long userId, DateRange range) {
        return jdbc.query(NUTRITION_SELECT + """
                WHERE user_id = :u AND log_date BETWEEN :from AND :to AND data_source = :source
                ORDER BY log_date, eat_time, id
                """, params(userId, range).addValue("source", HealthSyncService.DATA_SOURCE),
                HealthDataQueryService::mapNutrition);
    }

    /** 일일 분석용: 출처와 상관없이 그날 먹은 기록 전부. */
    public List<NutritionEntry> allNutritionOfDay(long userId, LocalDate date) {
        return jdbc.query(NUTRITION_SELECT + """
                WHERE user_id = :u AND log_date BETWEEN :from AND :to
                ORDER BY eat_time, id
                """, params(userId, new DateRange(date, date)), HealthDataQueryService::mapNutrition);
    }

    private static NutritionEntry mapNutrition(ResultSet rs, int rowNum) throws SQLException {
        return new NutritionEntry(
                rs.getString("hc_record_id"),
                rs.getDate("log_date").toLocalDate(),
                rs.getString("meal_type"),
                rs.getString("food_name"),
                nullableDateTime(rs, "eat_time"),
                nullableDouble(rs, "calories"),
                nullableDouble(rs, "carbs"),
                nullableDouble(rs, "protein"),
                nullableDouble(rs, "fat"),
                nullableDouble(rs, "sodium"),
                nullableDouble(rs, "sugar"),
                nullableDouble(rs, "dietary_fiber"));
    }

    private static MapSqlParameterSource params(long userId, DateRange range) {
        return new MapSqlParameterSource()
                .addValue("u", userId)
                .addValue("from", range.from())
                .addValue("to", range.to())
                .addValue("toExclusive", range.to().plusDays(1));
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }

    private static LocalDateTime nullableDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toLocalDateTime();
    }
}
