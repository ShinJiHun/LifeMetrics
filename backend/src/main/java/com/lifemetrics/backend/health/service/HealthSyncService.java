package com.lifemetrics.backend.health.service;

import com.lifemetrics.backend.health.dto.HealthSyncRequest;
import com.lifemetrics.backend.health.dto.HealthSyncRequest.Day;
import com.lifemetrics.backend.health.dto.HealthSyncRequest.Exercise;
import com.lifemetrics.backend.health.dto.HealthSyncRequest.Nutrition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Health Connect(삼성헬스) 동기화 데이터를 riding_db 에 저장한다.
 * <p>
 * - 식단은 기존 food_log 에 data_source='health_connect' 로 넣어 일일 분석(daily_analysis.py)이 그대로 읽게 한다.
 * - 같은 묶음을 다시 보내도 결과가 같도록 모든 쓰기는 upsert 다 (식단·운동은 hc_record_id, 활동량은 날짜).
 * - 식단이 바뀐 날짜는 food_daily_summary 를 food_log 기준으로 다시 계산한다.
 */
@Slf4j
@Service
public class HealthSyncService {

    public static final String DATA_SOURCE = "health_connect";
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> MEAL_TYPES = Set.of("breakfast", "lunch", "dinner", "snack");

    static final int MAX_DAYS = 400;
    static final int MAX_RECORDS = 5000;

    private final NamedParameterJdbcTemplate jdbc;

    public HealthSyncService(@Qualifier("ridingDataSource") DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    public record Result(int days, int nutrition, int exercises, int deleted, Set<LocalDate> summaryDates) {}

    @Transactional
    public Result sync(long userId, HealthSyncRequest req) {
        validate(req);

        Set<LocalDate> touchedFoodDates = new TreeSet<>();

        for (Day d : req.daysOrEmpty()) {
            upsertDay(userId, d);
        }
        if (!req.nutritionOrEmpty().isEmpty()) {
            // 수정으로 날짜가 옮겨진 기록이면 이전 날짜 요약도 다시 계산해야 한다
            touchedFoodDates.addAll(foodDatesOf(req.nutritionOrEmpty().stream().map(Nutrition::id).toList()));
        }
        for (Nutrition n : req.nutritionOrEmpty()) {
            touchedFoodDates.add(upsertNutrition(userId, n));
        }
        for (Exercise e : req.exercisesOrEmpty()) {
            upsertExercise(userId, e);
        }
        int deleted = 0;
        if (!req.deletedIdsOrEmpty().isEmpty()) {
            touchedFoodDates.addAll(foodDatesOf(req.deletedIdsOrEmpty()));
            deleted = deleteRecords(req.deletedIdsOrEmpty());
        }
        for (LocalDate date : touchedFoodDates) {
            recomputeFoodSummary(userId, date);
        }

        jdbc.update("""
                INSERT INTO hc_sync_log (user_id, device, synced_at, day_count, nutrition_count, exercise_count, deleted_count)
                VALUES (:u, :device, :syncedAt, :days, :nutrition, :exercises, :deleted)
                """, new MapSqlParameterSource()
                .addValue("u", userId)
                // DB 서버 시계는 UTC 라 CURRENT_TIMESTAMP 대신 KST 로 직접 넣는다
                .addValue("syncedAt", LocalDateTime.now(KST))
                .addValue("device", truncate(req.device(), 100))
                .addValue("days", req.daysOrEmpty().size())
                .addValue("nutrition", req.nutritionOrEmpty().size())
                .addValue("exercises", req.exercisesOrEmpty().size())
                .addValue("deleted", deleted));

        log.info("🩺 Health Connect 동기화: 일 {} / 식단 {} / 운동 {} / 삭제 {} (요약 재계산 {}일)",
                req.daysOrEmpty().size(), req.nutritionOrEmpty().size(), req.exercisesOrEmpty().size(),
                deleted, touchedFoodDates.size());
        return new Result(req.daysOrEmpty().size(), req.nutritionOrEmpty().size(),
                req.exercisesOrEmpty().size(), deleted, touchedFoodDates);
    }

    /** 마지막 동기화 시각과 건수. 한 번도 없으면 null. */
    public Map<String, Object> lastSync(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT device, synced_at, day_count, nutrition_count, exercise_count, deleted_count
                FROM hc_sync_log WHERE user_id = :u ORDER BY synced_at DESC, id DESC LIMIT 1
                """, Map.of("u", userId));
        if (rows.isEmpty()) return null;
        Map<String, Object> row = new java.util.LinkedHashMap<>(rows.get(0));
        if (row.get("synced_at") instanceof java.sql.Timestamp ts) {
            row.put("synced_at", ts.toLocalDateTime().toString()); // KST 로 저장된 값을 그대로 노출
        }
        return row;
    }

    // ── 검증 ─────────────────────────────────────────

    static void validate(HealthSyncRequest req) {
        if (req == null) throw new IllegalArgumentException("요청 본문이 비어 있습니다.");
        if (req.daysOrEmpty().size() > MAX_DAYS)
            throw new IllegalArgumentException("days 는 한 번에 " + MAX_DAYS + "일까지 보낼 수 있습니다.");
        int records = req.nutritionOrEmpty().size() + req.exercisesOrEmpty().size() + req.deletedIdsOrEmpty().size();
        if (records > MAX_RECORDS)
            throw new IllegalArgumentException("레코드는 한 번에 " + MAX_RECORDS + "건까지 보낼 수 있습니다. 나눠서 보내세요.");

        Set<LocalDate> seen = new HashSet<>();
        for (Day d : req.daysOrEmpty()) {
            if (d.date() == null) throw new IllegalArgumentException("days[].date 가 없습니다.");
            if (!seen.add(d.date())) throw new IllegalArgumentException("days 에 같은 날짜가 두 번 있습니다: " + d.date());
        }
        for (Nutrition n : req.nutritionOrEmpty()) {
            requireId(n.id(), "nutrition");
            if (n.startTime() == null) throw new IllegalArgumentException("nutrition[" + n.id() + "].startTime 이 없습니다.");
        }
        for (Exercise e : req.exercisesOrEmpty()) {
            requireId(e.id(), "exercises");
            if (e.startTime() == null || e.endTime() == null)
                throw new IllegalArgumentException("exercises[" + e.id() + "] 의 startTime/endTime 이 없습니다.");
            if (e.type() == null || e.type().isBlank())
                throw new IllegalArgumentException("exercises[" + e.id() + "].type 이 없습니다.");
        }
        for (String id : req.deletedIdsOrEmpty()) requireId(id, "deletedIds");
    }

    private static void requireId(String id, String field) {
        if (id == null || id.isBlank() || id.length() > 64)
            throw new IllegalArgumentException(field + " 의 id 가 비었거나 64자를 넘습니다.");
    }

    // ── 활동량 ───────────────────────────────────────

    private void upsertDay(long userId, Day d) {
        jdbc.update("""
                INSERT INTO hc_daily_activity
                    (user_id, activity_date, steps, distance_m, active_kcal, total_kcal,
                     exercise_minutes, sleep_minutes, resting_heart_rate)
                VALUES (:u, :date, :steps, :distance, :active, :total, :exercise, :sleep, :rhr)
                ON DUPLICATE KEY UPDATE
                    steps = VALUES(steps), distance_m = VALUES(distance_m),
                    active_kcal = VALUES(active_kcal), total_kcal = VALUES(total_kcal),
                    exercise_minutes = VALUES(exercise_minutes), sleep_minutes = VALUES(sleep_minutes),
                    resting_heart_rate = VALUES(resting_heart_rate)
                """, new MapSqlParameterSource()
                .addValue("u", userId)
                .addValue("date", d.date())
                .addValue("steps", d.steps())
                .addValue("distance", d.distanceM())
                .addValue("active", d.activeKcal())
                .addValue("total", d.totalKcal())
                .addValue("exercise", d.exerciseMinutes())
                .addValue("sleep", d.sleepMinutes())
                .addValue("rhr", d.restingHeartRate()));
    }

    // ── 식단 ─────────────────────────────────────────

    /** @return 이 레코드가 속한 KST 날짜 */
    private LocalDate upsertNutrition(long userId, Nutrition n) {
        LocalDateTime eatTime = toKst(n.startTime());
        LocalDate logDate = eatTime.toLocalDate();

        jdbc.update("""
                INSERT INTO food_log
                    (user_id, log_date, meal_type, food_name, calories, carbs, protein, fat,
                     saturated_fat, unsaturated_fat, polyunsaturated_fat, monounsaturated_fat, trans_fat,
                     cholesterol, sodium, potassium, dietary_fiber, sugar,
                     vitamin_a, vitamin_c, calcium, iron, eat_time, data_source, hc_record_id, memo)
                VALUES
                    (:u, :logDate, :meal, :name, :kcal, :carbs, :protein, :fat,
                     :satFat, :unsatFat, :polyFat, :monoFat, :transFat,
                     :chol, :sodium, :potassium, :fiber, :sugar,
                     :vitA, :vitC, :calcium, :iron, :eatTime, :source, :hcId, :memo)
                ON DUPLICATE KEY UPDATE
                    log_date = VALUES(log_date), meal_type = VALUES(meal_type), food_name = VALUES(food_name),
                    calories = VALUES(calories), carbs = VALUES(carbs), protein = VALUES(protein), fat = VALUES(fat),
                    saturated_fat = VALUES(saturated_fat), unsaturated_fat = VALUES(unsaturated_fat),
                    polyunsaturated_fat = VALUES(polyunsaturated_fat), monounsaturated_fat = VALUES(monounsaturated_fat),
                    trans_fat = VALUES(trans_fat), cholesterol = VALUES(cholesterol), sodium = VALUES(sodium),
                    potassium = VALUES(potassium), dietary_fiber = VALUES(dietary_fiber), sugar = VALUES(sugar),
                    vitamin_a = VALUES(vitamin_a), vitamin_c = VALUES(vitamin_c), calcium = VALUES(calcium),
                    iron = VALUES(iron), eat_time = VALUES(eat_time), memo = VALUES(memo)
                """, new MapSqlParameterSource()
                .addValue("u", userId)
                .addValue("logDate", logDate)
                .addValue("meal", normalizeMealType(n.mealType()))
                .addValue("name", truncate(n.name(), 300))
                .addValue("kcal", zeroIfNull(n.energyKcal()))
                .addValue("carbs", zeroIfNull(n.carbsG()))
                .addValue("protein", zeroIfNull(n.proteinG()))
                .addValue("fat", zeroIfNull(n.fatG()))
                .addValue("satFat", n.saturatedFatG())
                .addValue("unsatFat", n.unsaturatedFatG())
                .addValue("polyFat", n.polyunsaturatedFatG())
                .addValue("monoFat", n.monounsaturatedFatG())
                .addValue("transFat", n.transFatG())
                .addValue("chol", n.cholesterolMg())
                .addValue("sodium", n.sodiumMg())
                .addValue("potassium", n.potassiumMg())
                .addValue("fiber", n.fiberG())
                .addValue("sugar", n.sugarG())
                .addValue("vitA", n.vitaminAMcg())
                .addValue("vitC", n.vitaminCMg())
                .addValue("calcium", n.calciumMg())
                .addValue("iron", n.ironMg())
                .addValue("eatTime", eatTime)
                .addValue("source", DATA_SOURCE)
                .addValue("hcId", n.id())
                .addValue("memo", n.sourceApp() == null ? null : "source_app=" + truncate(n.sourceApp(), 200)));
        return logDate;
    }

    private List<LocalDate> foodDatesOf(List<String> hcIds) {
        return jdbc.queryForList("SELECT DISTINCT log_date FROM food_log WHERE hc_record_id IN (:ids)",
                Map.of("ids", hcIds), LocalDate.class);
    }

    /** 해당 날짜의 food_log 전체로 일일 요약을 다시 만든다. 남은 기록이 없으면 health_connect 요약을 지운다. */
    private void recomputeFoodSummary(long userId, LocalDate date) {
        var p = new MapSqlParameterSource().addValue("u", userId).addValue("d", date).addValue("source", DATA_SOURCE);
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM food_log WHERE user_id = :u AND log_date = :d", p, Integer.class);
        if (rows == null || rows == 0) {
            jdbc.update("DELETE FROM food_daily_summary WHERE user_id = :u AND log_date = :d AND data_source = :source", p);
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
                       :source
                FROM food_log WHERE user_id = :u AND log_date = :d
                ON DUPLICATE KEY UPDATE
                    total_calories = VALUES(total_calories), total_carbs = VALUES(total_carbs),
                    total_protein = VALUES(total_protein), total_fat = VALUES(total_fat),
                    total_sodium = VALUES(total_sodium), total_sugar = VALUES(total_sugar),
                    total_dietary_fiber = VALUES(total_dietary_fiber), meal_count = VALUES(meal_count),
                    data_source = VALUES(data_source), updated_at = CURRENT_TIMESTAMP
                """, p);
    }

    // ── 운동 ─────────────────────────────────────────

    private void upsertExercise(long userId, Exercise e) {
        LocalDateTime start = toKst(e.startTime());
        LocalDateTime end = toKst(e.endTime());
        long minutes = Math.max(0, Duration.between(e.startTime(), e.endTime()).toMinutes());
        jdbc.update("""
                INSERT INTO hc_exercise_session
                    (user_id, hc_record_id, exercise_type, title, start_time, end_time, duration_min,
                     active_kcal, distance_m, avg_heart_rate, source_app)
                VALUES (:u, :hcId, :type, :title, :start, :end, :minutes, :kcal, :distance, :hr, :source)
                ON DUPLICATE KEY UPDATE
                    exercise_type = VALUES(exercise_type), title = VALUES(title),
                    start_time = VALUES(start_time), end_time = VALUES(end_time), duration_min = VALUES(duration_min),
                    active_kcal = VALUES(active_kcal), distance_m = VALUES(distance_m),
                    avg_heart_rate = VALUES(avg_heart_rate), source_app = VALUES(source_app)
                """, new MapSqlParameterSource()
                .addValue("u", userId)
                .addValue("hcId", e.id())
                .addValue("type", truncate(e.type().toUpperCase(Locale.ROOT), 50))
                .addValue("title", truncate(e.title(), 200))
                .addValue("start", start)
                .addValue("end", end)
                .addValue("minutes", minutes)
                .addValue("kcal", e.activeKcal())
                .addValue("distance", e.distanceM())
                .addValue("hr", e.avgHeartRate())
                .addValue("source", truncate(e.sourceApp(), 200)));
    }

    private int deleteRecords(List<String> hcIds) {
        var p = Map.of("ids", hcIds);
        return jdbc.update("DELETE FROM food_log WHERE hc_record_id IN (:ids)", p)
                + jdbc.update("DELETE FROM hc_exercise_session WHERE hc_record_id IN (:ids)", p);
    }

    // ── 변환 유틸 ────────────────────────────────────

    static LocalDateTime toKst(OffsetDateTime t) {
        return t.atZoneSameInstant(KST).toLocalDateTime();
    }

    static String normalizeMealType(String raw) {
        if (raw == null) return "unknown";
        String v = raw.trim().toLowerCase(Locale.ROOT).replace("meal_type_", "");
        return MEAL_TYPES.contains(v) ? v : "unknown";
    }

    private static double zeroIfNull(Double v) {
        return v == null ? 0 : v;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
