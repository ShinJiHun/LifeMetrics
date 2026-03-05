package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ExerciseMuscleMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExerciseMuscleMapRepository extends JpaRepository<ExerciseMuscleMap, Long> {

    List<ExerciseMuscleMap> findByExerciseItemId(Long exerciseItemId);

    List<ExerciseMuscleMap> findByExerciseItemIdIn(List<Long> exerciseItemIds);

    List<ExerciseMuscleMap> findByMuscleGroupId(Long muscleGroupId);

    /**
     * 특정 운동 목록에 대한 근육 매핑 + 근육 정보 조인
     */
    @Query("SELECT emm, mg FROM ExerciseMuscleMap emm " +
           "JOIN MuscleGroup mg ON emm.muscleGroupId = mg.id " +
           "WHERE emm.exerciseItemId IN :exerciseItemIds")
    List<Object[]> findMappingsWithMuscleGroup(@Param("exerciseItemIds") List<Long> exerciseItemIds);
}