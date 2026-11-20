package com.lifemetrics.backend.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 유산소 모아보기: 헬스장 유산소(exercise_log, 카테고리 '유산소'), 라이딩(activity_core),
 * 걷기·달리기(Health Connect hc_exercise_session)를 한 목록으로 합친다.
 * 원본은 각자 테이블에 그대로 두고 읽기만 한다.
 */
@Service
public class CardioService {

    public record CardioEntry(
            String source,      // GYM | RIDE | WALK
            String date,        // yyyy-MM-dd
            String startTime,   // 있으면 HH:mm
            String name,
            Double durationMin,
            Double distanceKm,
            Integer kcal,
            Integer avgHr,
            String memo,
            Long refId,         // RIDE: activity_core.id (상세 화면 링크), GYM: exercise_session.id
            Boolean sameDayWeights  // 같은 날 웨이트(유산소 아닌 헬스 기록)도 했는지
    ) {}

    private final NamedParameterJdbcTemplate jdbc;

    public CardioService(@Qualifier("ridingDataSource") DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    public List<CardioEntry> list(long userId, LocalDate from, LocalDate to) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("u", userId)
                .addValue("from", from)
                .addValue("to", to)
                .addValue("fromTs", from.atStartOfDay())
                .addValue("toTs", to.plusDays(1).atStartOfDay())
                .addValue("cardio", ExerciseLogService.CARDIO_CATEGORY);

        List<CardioEntry> out = new ArrayList<>();

        // 헬스장 유산소. 메모 앞의 시트 동기화 마커([cardio#...])는 떼고 보여준다.
        out.addAll(jdbc.query("""
                SELECT s.id AS session_id, s.session_date, i.name_ko, i.name,
                       l.duration_sec, l.distance_km, l.kcal, l.avg_hr,
                       TRIM(REGEXP_REPLACE(COALESCE(l.memo, ''), '^\\\\[cardio#[^]]*\\\\]', '')) AS memo
                FROM exercise_log l
                JOIN exercise_session s ON s.id = l.session_id
                JOIN exercise_item i ON i.id = l.exercise_item_id
                JOIN exercise_category c ON c.id = i.category_id
                WHERE s.user_id = :u AND s.session_date BETWEEN :from AND :to AND c.name = :cardio
                ORDER BY s.session_date, l.id
                """, p, (rs, i) -> new CardioEntry(
                "GYM",
                rs.getDate("session_date").toLocalDate().toString(),
                null,
                rs.getString("name_ko") != null ? rs.getString("name_ko") : rs.getString("name"),
                rs.getObject("duration_sec") != null ? Math.round(rs.getInt("duration_sec") / 6.0) / 10.0 : null,
                rs.getBigDecimal("distance_km") != null ? rs.getBigDecimal("distance_km").doubleValue() : null,
                (Integer) rs.getObject("kcal", Integer.class),
                (Integer) rs.getObject("avg_hr", Integer.class),
                blankToNull(rs.getString("memo")),
                rs.getLong("session_id"),
                null)));

        // 라이딩
        out.addAll(jdbc.query("""
                SELECT id, name, ride_type, start_time, total_distance, moving_time, elapsed_time,
                       calories, avg_heart_rate
                FROM activity_core
                WHERE user_id = :u AND start_time >= :fromTs AND start_time < :toTs
                ORDER BY start_time
                """, p, (rs, i) -> {
            var start = rs.getTimestamp("start_time").toLocalDateTime();
            Number moving = (Number) rs.getObject("moving_time");
            Number elapsed = (Number) rs.getObject("elapsed_time");
            Number sec = moving != null ? moving : elapsed;
            Number dist = (Number) rs.getObject("total_distance");
            Number kcal = (Number) rs.getObject("calories");
            Number hr = (Number) rs.getObject("avg_heart_rate");
            return new CardioEntry(
                    "RIDE",
                    start.toLocalDate().toString(),
                    String.format("%02d:%02d", start.getHour(), start.getMinute()),
                    rs.getString("name") != null ? rs.getString("name") : "라이딩",
                    sec != null ? Math.round(sec.doubleValue() / 6.0) / 10.0 : null,
                    dist != null ? Math.round(dist.doubleValue() / 10.0) / 100.0 : null,
                    kcal != null ? (int) Math.round(kcal.doubleValue()) : null,
                    hr != null ? (int) Math.round(hr.doubleValue()) : null,
                    rs.getString("ride_type"),
                    rs.getLong("id"),
                    null);
        }));

        // 폰(Health Connect) 걷기·달리기. 자전거는 라이딩과 겹치므로 제외.
        out.addAll(jdbc.query("""
                SELECT exercise_type, title, start_time, duration_min, active_kcal, distance_m, avg_heart_rate
                FROM hc_exercise_session
                WHERE user_id = :u AND start_time >= :fromTs AND start_time < :toTs
                  AND exercise_type <> 'BIKING'
                ORDER BY start_time
                """, p, (rs, i) -> {
            var start = rs.getTimestamp("start_time").toLocalDateTime();
            String type = rs.getString("exercise_type");
            Number dist = (Number) rs.getObject("distance_m");
            Number kcal = (Number) rs.getObject("active_kcal");
            return new CardioEntry(
                    "WALK",
                    start.toLocalDate().toString(),
                    String.format("%02d:%02d", start.getHour(), start.getMinute()),
                    rs.getString("title") != null ? rs.getString("title") : hcName(type),
                    (double) rs.getInt("duration_min"),
                    dist != null ? Math.round(dist.doubleValue() / 10.0) / 100.0 : null,
                    kcal != null ? (int) Math.round(kcal.doubleValue()) : null,
                    (Integer) rs.getObject("avg_heart_rate", Integer.class),
                    null,
                    null,
                    null);
        }));

        // 같은 날 웨이트 여부
        List<String> weightDays = jdbc.queryForList("""
                SELECT DISTINCT s.session_date
                FROM exercise_session s
                JOIN exercise_log l ON l.session_id = s.id
                JOIN exercise_item i ON i.id = l.exercise_item_id
                JOIN exercise_category c ON c.id = i.category_id
                WHERE s.user_id = :u AND s.session_date BETWEEN :from AND :to AND c.name <> :cardio
                """, p, java.sql.Date.class).stream().map(d -> d.toLocalDate().toString()).toList();

        return out.stream()
                .map(e -> new CardioEntry(e.source(), e.date(), e.startTime(), e.name(), e.durationMin(),
                        e.distanceKm(), e.kcal(), e.avgHr(), e.memo(), e.refId(), weightDays.contains(e.date())))
                .sorted(Comparator.comparing(CardioEntry::date).reversed()
                        .thenComparing(e -> e.startTime() == null ? "" : e.startTime(), Comparator.reverseOrder()))
                .toList();
    }

    private static String hcName(String type) {
        if (type == null) return "운동";
        return switch (type) {
            case "WALKING" -> "걷기";
            case "RUNNING" -> "달리기";
            case "HIKING" -> "등산";
            case "SWIMMING_POOL", "SWIMMING_OPEN_WATER" -> "수영";
            default -> type;
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
