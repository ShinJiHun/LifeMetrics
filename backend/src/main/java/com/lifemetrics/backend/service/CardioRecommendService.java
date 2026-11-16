package com.lifemetrics.backend.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 유산소 추천: "한 달에 X kg 감량" 목표 → 하루 적자 목표를 만들고,
 * 그날 먹은 것(food_daily_summary)과 실제 활동(폰 걸음·활동 kcal, 따릉이·라이딩, 헬스장 유산소, 웨이트)을 빼서
 * 남은 적자를 헬스장 유산소(경사 걷기 등) 시간·강도로 바꿔 준다.
 *
 * 소비 = 기초대사(인바디 체성분, Katch-McArdle) + 식사 소화(섭취의 10%) + 활동.
 * 비어 있는 값(아직 동기화 안 된 걸음 수, 입력 안 한 식단)은 최근 7일 평균으로 채우고 "추정"으로 표시한다.
 * 1kg 지방 ≈ 7,700kcal. 운동 칼로리는 ACSM 대사 공식(순소비, 안정시 제외)으로 계산한다.
 */
@Service
public class CardioRecommendService {

    private static final double KCAL_PER_KG = 7700;
    private static final double WEIGHT_SET_KCAL = 5;      // 웨이트 1세트(휴식 포함) 대략 순소비
    private static final int MIN_SESSION = 20, MAX_SESSION = 60;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NamedParameterJdbcTemplate jdbc;

    public CardioRecommendService(@Qualifier("ridingDataSource") DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    // ── 응답 ──────────────────────────────────────────────────────
    public record Part(String key, String label, Integer kcal, boolean estimated, String detail) {}

    public record Option(String name, String setting, double speedKmh, double inclinePct,
                         int minutes, int kcal, double kcalPerMin) {}

    public record Recommendation(
            String status,          // NEED_TARGET | DONE | CARDIO
            Integer minutes, Integer kcal, Integer hrLow, Integer hrHigh,
            String headline, String note, List<Option> options) {}

    public record WeekPace(Integer avgDeficit, int days, Double kgPerMonth) {}

    public record Result(
            String date, boolean today,
            Double monthlyLossKg, Double suggestedMonthlyLossKg, Integer dailyDeficitTarget,
            Double weightKg, String bodyRecordDate, Integer bmr,
            Part intake, List<Part> burns, Integer expenditure,
            Integer currentDeficit, Integer remainingKcal,
            Recommendation recommendation, WeekPace week, List<String> notes) {}

    // ── 목표 저장/조회 ─────────────────────────────────────────────
    public Double getMonthlyLossKg(long userId) {
        try {
            Number n = jdbc.queryForObject(
                    "SELECT monthly_loss_kg FROM weight_loss_target WHERE user_id = :u",
                    Map.of("u", userId), Number.class);
            return n == null ? null : n.doubleValue();
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void saveMonthlyLossKg(long userId, Double kg) {
        if (kg == null || kg < 0.1 || kg > 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "한 달 감량 목표는 0.1 ~ 6kg 사이로 입력하세요");
        }
        jdbc.update("""
                INSERT INTO weight_loss_target (user_id, monthly_loss_kg) VALUES (:u, :kg)
                ON DUPLICATE KEY UPDATE monthly_loss_kg = VALUES(monthly_loss_kg)
                """, new MapSqlParameterSource("u", userId).addValue("kg", Math.round(kg * 10) / 10.0));
    }

    // ── 추천 계산 ─────────────────────────────────────────────────
    public Result recommend(long userId, LocalDate date) {
        LocalDate today = LocalDate.now(KST);
        if (date == null) date = today;
        boolean isToday = date.equals(today);
        List<String> notes = new ArrayList<>();

        MapSqlParameterSource p = new MapSqlParameterSource("u", userId)
                .addValue("d", date)
                .addValue("from7", date.minusDays(7))
                .addValue("dTs", date.atStartOfDay())
                .addValue("nextTs", date.plusDays(1).atStartOfDay())
                .addValue("cardio", ExerciseLogService.CARDIO_CATEGORY);

        // 1) 몸 상태: 해당 날짜 이전 가장 최근 인바디
        Map<String, Object> body = first(jdbc.queryForList("""
                SELECT record_date, weight, body_fat_mass, basal_metabolic_rate
                FROM user_body_record
                WHERE user_id = :u AND record_date <= :d AND weight IS NOT NULL
                ORDER BY record_date DESC LIMIT 1
                """, p));
        Double weight = body == null ? null : num(body.get("weight"));
        Double fat = body == null ? null : num(body.get("body_fat_mass"));
        Double bmrRecorded = body == null ? null : num(body.get("basal_metabolic_rate"));
        String bodyDate = body == null ? null : String.valueOf(body.get("record_date"));
        if (weight == null) {
            weight = 75.0;
            notes.add("인바디 기록이 없어 체중 75kg 로 가정했습니다.");
        }
        double bmr;
        if (bmrRecorded != null && bmrRecorded > 800) {
            bmr = bmrRecorded;
        } else if (fat != null) {
            bmr = 370 + 21.6 * (weight - fat);                 // Katch-McArdle (제지방량 기준)
        } else {
            bmr = 22 * weight;                                  // 체성분이 없으면 대략치
        }
        p.addValue("w", weight);

        // 2) 목표
        Double monthly = getMonthlyLossKg(userId);
        Double suggested = suggestFromBodyGoal(userId);
        Integer dailyTarget = monthly == null ? null : (int) Math.round(monthly * KCAL_PER_KG / 30);

        // 3) 섭취
        Integer intakeKcal = intOrNull(firstValue(jdbc.queryForList(
                "SELECT total_calories FROM food_daily_summary WHERE user_id = :u AND log_date = :d", p)));
        boolean intakeEst = false;
        String intakeDetail = null;
        if (intakeKcal == null || intakeKcal <= 0) {
            Double avg = num(firstValue(jdbc.queryForList("""
                    SELECT AVG(total_calories) FROM food_daily_summary
                    WHERE user_id = :u AND log_date >= :from7 AND log_date < :d AND total_calories > 0
                    """, p)));
            intakeKcal = avg == null ? null : (int) Math.round(avg);
            intakeEst = true;
            intakeDetail = avg == null ? "식단 기록 없음" : "식단 미입력 → 최근 7일 평균";
        } else if (isToday) {
            intakeDetail = "지금까지 입력한 식단";
        }
        if (intakeKcal == null) {
            intakeKcal = (int) Math.round(bmr * 1.4);
            intakeDetail = "식단 기록이 없어 기초대사×1.4 로 가정";
        }
        Part intake = new Part("intake", "먹은 것", intakeKcal, intakeEst, intakeDetail);

        // 4) 소비
        List<Part> burns = new ArrayList<>();
        burns.add(new Part("bmr", "기초대사", (int) Math.round(bmr), false,
                bmrRecorded != null ? "인바디" : (fat != null ? "인바디 체성분으로 계산" : "체중으로 대략 계산")));
        burns.add(new Part("tef", "식사 소화", (int) Math.round(intakeKcal * 0.1), intakeEst, "섭취의 10%"));

        // 4-1) 폰 걸음·활동 kcal (Health Connect, 다음 날 동기화되는 경우가 많음)
        Map<String, Object> hc = first(jdbc.queryForList(
                "SELECT steps, active_kcal FROM hc_daily_activity WHERE user_id = :u AND activity_date = :d", p));
        Integer steps = hc == null ? null : intOrNull(hc.get("steps"));
        Integer activeKcal = hc == null ? null : intOrNull(hc.get("active_kcal"));
        boolean stepsEst = false;
        String stepsDetail;
        if (steps == null || steps <= 0 || activeKcal == null) {
            Map<String, Object> avg = first(jdbc.queryForList("""
                    SELECT AVG(steps) AS steps, AVG(active_kcal) AS kcal FROM hc_daily_activity
                    WHERE user_id = :u AND activity_date >= :from7 AND activity_date < :d
                      AND steps > 0 AND active_kcal IS NOT NULL
                    """, p));
            Integer avgSteps = avg == null ? null : intOrNull(avg.get("steps"));
            Integer avgKcal = avg == null ? null : intOrNull(avg.get("kcal"));
            stepsEst = true;
            steps = avgSteps;
            activeKcal = avgKcal != null ? avgKcal : 0;
            stepsDetail = avgSteps == null ? "걸음 기록 없음"
                    : String.format("아직 동기화 전 → 최근 7일 평균 %,d보", avgSteps);
        } else {
            stepsDetail = String.format("%,d보", steps);
        }
        burns.add(new Part("steps", "걸음·일상 활동", activeKcal, stepsEst, stepsDetail));

        // 4-2) 따릉이·라이딩
        List<Map<String, Object>> rides = jdbc.queryForList("""
                SELECT ride_type, calories, moving_time, total_distance FROM activity_core
                WHERE user_id = :u AND start_time >= :dTs AND start_time < :nextTs
                """, p);
        int rideKcal = 0;
        boolean rideEst = false;
        double rideKm = 0;
        int ddareungi = 0;
        for (Map<String, Object> r : rides) {
            Double c = num(r.get("calories"));
            Double mv = num(r.get("moving_time"));
            Double dist = num(r.get("total_distance"));
            if ("DDAREUNGI".equals(r.get("ride_type"))) ddareungi++;
            if (dist != null) rideKm += dist / 1000;
            if (c != null && c > 0) {
                rideKcal += (int) Math.round(c);
            } else if (mv != null) {                     // 칼로리 없으면 여가 자전거 4 MET(순 3)로 추정
                rideKcal += (int) Math.round(3 * 3.5 * weight / 200 * mv / 60);
                rideEst = true;
            }
        }
        if (!rides.isEmpty()) {
            String what = ddareungi == rides.size() ? "따릉이" : (ddareungi > 0 ? "따릉이·라이딩" : "라이딩");
            burns.add(new Part("ride", what, rideKcal, rideEst,
                    String.format("%d회 · %.1fkm", rides.size(), rideKm)));
        }

        // 4-3) 헬스장 유산소 (이미 한 것)
        Map<String, Object> gym = first(jdbc.queryForList("""
                SELECT COUNT(*) AS n, SUM(l.duration_sec) AS sec, SUM(l.kcal) AS kcal,
                       SUM(CASE WHEN l.kcal IS NULL THEN COALESCE(l.duration_sec, 0) ELSE 0 END) AS sec_no_kcal
                FROM exercise_log l
                JOIN exercise_session s ON s.id = l.session_id
                JOIN exercise_item i ON i.id = l.exercise_item_id
                JOIN exercise_category c ON c.id = i.category_id
                WHERE s.user_id = :u AND s.session_date = :d AND c.name = :cardio
                """, p));
        int gymN = gym == null ? 0 : intOrZero(gym.get("n"));
        int gymMin = 0;
        if (gymN > 0) {
            gymMin = (int) Math.round(intOrZero(gym.get("sec")) / 60.0);
            int kcal = intOrZero(gym.get("kcal"));
            double noKcalMin = intOrZero(gym.get("sec_no_kcal")) / 60.0;
            boolean est = noKcalMin > 0;
            kcal += (int) Math.round(noKcalMin * walkKcalPerMin(5.5, 8, weight));  // 칼로리 없는 기록은 경사 걷기로 가정
            burns.add(new Part("gym", "헬스장 유산소", kcal, est, gymMin + "분"));
        }

        // 4-4) 웨이트 (세트 수로 대략)
        Integer sets = intOrNull(firstValue(jdbc.queryForList("""
                SELECT COALESCE(SUM(GREATEST(COALESCE(cnt.n, 0), COALESCE(l.sets, 0))), 0)
                FROM exercise_log l
                JOIN exercise_session s ON s.id = l.session_id
                JOIN exercise_item i ON i.id = l.exercise_item_id
                JOIN exercise_category c ON c.id = i.category_id
                LEFT JOIN (SELECT log_id, COUNT(*) AS n FROM exercise_set GROUP BY log_id) cnt ON cnt.log_id = l.id
                WHERE s.user_id = :u AND s.session_date = :d AND c.name <> :cardio
                """, p)));
        if (sets != null && sets > 0) {
            burns.add(new Part("weights", "웨이트", (int) Math.round(sets * WEIGHT_SET_KCAL), true, sets + "세트 (세트당 약 5kcal)"));
        }

        int expenditure = burns.stream().mapToInt(b -> b.kcal() == null ? 0 : b.kcal()).sum();
        int deficit = expenditure - intakeKcal;

        // 5) 추천
        int hrLow = 114, hrHigh = 133;                    // 기본: 최대심박 190 의 60~70% (존 2)
        Integer maxHr = intOrNull(firstValue(jdbc.queryForList(
                "SELECT max_hr FROM rider_zone_setting WHERE user_id = :u", p)));
        if (maxHr != null && maxHr > 120) {
            hrLow = (int) Math.round(maxHr * 0.60);
            hrHigh = (int) Math.round(maxHr * 0.70);
        }

        Recommendation rec;
        Integer remaining = null;
        if (dailyTarget == null) {
            rec = new Recommendation("NEED_TARGET", null, null, hrLow, hrHigh,
                    "한 달 감량 목표를 입력하면 오늘 유산소를 계산해 드려요", null, List.of());
        } else {
            remaining = dailyTarget - deficit;
            rec = buildRecommendation(remaining, weight, hrLow, hrHigh, gymMin, isToday);
        }

        if (isToday && !intakeEst) notes.add("오늘 식단을 더 입력하면 추천 시간이 늘어날 수 있어요.");
        if (stepsEst && isToday) notes.add("오늘 걸음 수는 폰 동기화 후(보통 다음 날) 반영돼요. 지금은 최근 평균으로 계산했습니다.");
        if (!rides.isEmpty() && !stepsEst) notes.add("따릉이 칼로리 일부가 폰 활동 칼로리와 겹칠 수 있어 실제 소비는 조금 적을 수 있어요.");

        WeekPace week = weekPace(userId, date, bmr, weight);
        return new Result(date.toString(), isToday, monthly, suggested, dailyTarget,
                round1(weight), bodyDate, (int) Math.round(bmr), intake, burns, expenditure,
                deficit, remaining, rec, week, notes);
    }

    private Recommendation buildRecommendation(int remaining, double w, int hrLow, int hrHigh, int gymMinDone, boolean isToday) {
        List<Option> opts = new ArrayList<>();
        if (remaining <= 0) {
            String head = (isToday ? "오늘" : "그날") + " 목표 적자를 이미 채웠어요";
            String note = gymMinDone > 0
                    ? "추가 유산소는 필요 없어요. 회복에 집중하세요."
                    : "추가 유산소는 선택 — 하고 싶다면 가벼운 걷기 20분(존 1~2) 정도면 충분해요.";
            opts.add(option("회복 걷기", 4.5, 3, MIN_SESSION, w));
            return new Recommendation("DONE", 0, 0, hrLow, hrHigh, head, note, opts);
        }

        double perMinMain = walkKcalPerMin(5.5, 8, w);
        int minutes = (int) Math.ceil(remaining / perMinMain);
        String note = null;
        if (minutes < MIN_SESSION) {
            minutes = MIN_SESSION;
        } else if (minutes > MAX_SESSION) {
            int over = (int) Math.round(remaining - MAX_SESSION * perMinMain);
            note = String.format("유산소는 %d분까지만 권장해요. 남는 약 %,dkcal 는 식단(간식·저녁 줄이기)으로 맞추는 게 좋아요.",
                    MAX_SESSION, over);
            minutes = MAX_SESSION;
        }
        opts.add(option("경사 걷기 (추천)", 5.5, 8, minutes, w));
        int easyMin = clamp((int) Math.ceil(Math.min(remaining, MAX_SESSION * perMinMain) / walkKcalPerMin(5.0, 5, w)));
        opts.add(option("가벼운 경사 걷기", 5.0, 5, easyMin, w));
        int runMin = clamp((int) Math.ceil(Math.min(remaining, MAX_SESSION * perMinMain) / runKcalPerMin(8.0, 1, w)));
        opts.add(option("가벼운 달리기", 8.0, 1, runMin, w));

        int kcal = (int) Math.round(minutes * perMinMain);
        String head = String.format("러닝머신 경사 8%% · 5.5km/h 로 %d분", minutes);
        return new Recommendation("CARDIO", minutes, kcal, hrLow, hrHigh, head, note, opts);
    }

    private Option option(String name, double speed, double incline, int minutes, double w) {
        double perMin = speed >= 7 ? runKcalPerMin(speed, incline, w) : walkKcalPerMin(speed, incline, w);
        String setting = String.format("속도 %.1fkm/h · 경사 %.0f%%", speed, incline);
        return new Option(name, setting, speed, incline, minutes, (int) Math.round(perMin * minutes),
                Math.round(perMin * 10) / 10.0);
    }

    /** 최근 7일(해당 날짜 제외) 중 식단·걸음이 둘 다 있는 날의 평균 적자 → 월 감량 페이스 */
    private WeekPace weekPace(long userId, LocalDate date, double bmr, double w) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT f.log_date, f.total_calories AS intake, h.active_kcal AS active,
                       (SELECT COALESCE(SUM(a.calories), 0) FROM activity_core a
                         WHERE a.user_id = f.user_id AND DATE(a.start_time) = f.log_date) AS ride,
                       (SELECT COALESCE(SUM(l.kcal), 0) FROM exercise_log l
                         JOIN exercise_session s ON s.id = l.session_id
                         JOIN exercise_item i ON i.id = l.exercise_item_id
                         JOIN exercise_category c ON c.id = i.category_id
                         WHERE s.user_id = f.user_id AND s.session_date = f.log_date AND c.name = :cardio) AS gym
                FROM food_daily_summary f
                JOIN hc_daily_activity h ON h.user_id = f.user_id AND h.activity_date = f.log_date
                WHERE f.user_id = :u AND f.log_date >= :from AND f.log_date < :d
                  AND f.total_calories > 0 AND h.steps > 0 AND h.active_kcal IS NOT NULL
                """, new MapSqlParameterSource("u", userId).addValue("d", date)
                .addValue("from", date.minusDays(7)).addValue("cardio", ExerciseLogService.CARDIO_CATEGORY));
        if (rows.isEmpty()) return new WeekPace(null, 0, null);
        double sum = 0;
        for (Map<String, Object> r : rows) {
            double in = num(r.get("intake"));
            double out = bmr + in * 0.1 + num(r.get("active")) + num(r.get("ride")) + num(r.get("gym"));
            sum += out - in;
        }
        double avg = sum / rows.size();
        return new WeekPace((int) Math.round(avg), rows.size(), round1(avg * 30 / KCAL_PER_KG));
    }

    /** body_goal(ACTIVE) 의 시작→목표 체중을 남은 개월로 나눈 값 — 입력 칸 기본값으로만 쓴다 */
    private Double suggestFromBodyGoal(long userId) {
        Map<String, Object> g = first(jdbc.queryForList("""
                SELECT start_weight, target_weight, start_date, target_date FROM body_goal
                WHERE user_id = :u AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1
                """, Map.of("u", userId)));
        if (g == null) return null;
        Double sw = num(g.get("start_weight")), tw = num(g.get("target_weight"));
        Object sd = g.get("start_date"), td = g.get("target_date");
        if (sw == null || tw == null || sd == null || td == null || sw <= tw) return null;
        long days = ChronoUnit.DAYS.between(LocalDate.parse(sd.toString()), LocalDate.parse(td.toString()));
        if (days <= 0) return null;
        return round1((sw - tw) / (days / 30.0));
    }

    // ── ACSM 대사 공식 (순소비: 안정시 3.5 ml/kg/min 제외), kcal/min = VO2 × kg / 1000 × 5 ──
    static double walkKcalPerMin(double kmh, double gradePct, double kg) {
        double s = kmh * 1000 / 60;
        double vo2 = 0.1 * s + 1.8 * s * gradePct / 100;
        return vo2 * kg / 1000 * 5;
    }

    static double runKcalPerMin(double kmh, double gradePct, double kg) {
        double s = kmh * 1000 / 60;
        double vo2 = 0.2 * s + 0.9 * s * gradePct / 100;
        return vo2 * kg / 1000 * 5;
    }

    private static int clamp(int m) {
        return Math.max(MIN_SESSION, Math.min(MAX_SESSION, m));
    }

    private static Map<String, Object> first(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static Object firstValue(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return null;
        return rows.get(0).values().stream().findFirst().orElse(null);
    }

    private static Double num(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intOrNull(Object o) {
        Double d = num(o);
        return d == null ? null : (int) Math.round(d);
    }

    private static int intOrZero(Object o) {
        Integer i = intOrNull(o);
        return i == null ? 0 : i;
    }

    private static Double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
