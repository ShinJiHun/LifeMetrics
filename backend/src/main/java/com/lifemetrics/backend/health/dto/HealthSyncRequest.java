package com.lifemetrics.backend.health.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 폰 앱(Health Connect)이 하루 1번 올리는 동기화 묶음.
 * <p>
 * 시간은 오프셋을 포함한 ISO-8601 문자열(예: 2026-11-01T12:30:00+09:00)로 받고,
 * 서버에서 KST 로 변환해 저장한다. 모든 목록은 비어 있어도 된다.
 */
public record HealthSyncRequest(
        String device,
        List<Day> days,
        List<Nutrition> nutrition,
        List<Exercise> exercises,
        /** 폰에서 삭제된 Health Connect 레코드 id (식단·운동 공통). */
        List<String> deletedIds
) {

    /** 하루 활동량. Health Connect aggregate 값(여러 앱 중복 제거 완료)을 보낸다. */
    public record Day(
            LocalDate date,
            Long steps,
            Double distanceM,
            Double activeKcal,
            Double totalKcal,
            Integer exerciseMinutes,
            Integer sleepMinutes,
            Integer restingHeartRate
    ) {}

    /** NutritionRecord 1건. 단위: 열량 kcal, 영양소 g, 나트륨·칼륨·콜레스테롤·칼슘·철 mg. */
    public record Nutrition(
            String id,
            OffsetDateTime startTime,
            String mealType,
            String name,
            Double energyKcal,
            Double carbsG,
            Double proteinG,
            Double fatG,
            Double saturatedFatG,
            Double unsaturatedFatG,
            Double polyunsaturatedFatG,
            Double monounsaturatedFatG,
            Double transFatG,
            Double cholesterolMg,
            Double sodiumMg,
            Double potassiumMg,
            Double fiberG,
            Double sugarG,
            Double vitaminAMcg,
            Double vitaminCMg,
            Double calciumMg,
            Double ironMg,
            String sourceApp
    ) {}

    /** ExerciseSessionRecord 1건. */
    public record Exercise(
            String id,
            String type,
            String title,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            Double activeKcal,
            Double distanceM,
            Integer avgHeartRate,
            String sourceApp
    ) {}

    public List<Day> daysOrEmpty() { return days == null ? List.of() : days; }
    public List<Nutrition> nutritionOrEmpty() { return nutrition == null ? List.of() : nutrition; }
    public List<Exercise> exercisesOrEmpty() { return exercises == null ? List.of() : exercises; }
    public List<String> deletedIdsOrEmpty() { return deletedIds == null ? List.of() : deletedIds; }
}
