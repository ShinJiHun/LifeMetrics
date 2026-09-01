package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.PermanentCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermanentCourseRepository extends JpaRepository<PermanentCourse, String> {

    List<PermanentCourse> findByIsActiveTrueOrderByPermanentNoAsc();
}
