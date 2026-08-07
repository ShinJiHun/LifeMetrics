package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.Bike;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BikeRepository extends JpaRepository<Bike, Long> {
    List<Bike> findByIsRetiredFalseOrderByIsDefaultDescIdDesc();

    /** 가동중 → 종료 순, 그 안에서 기본 자전거 우선. 목록 화면에서 두 그룹으로 나눠 쓴다. */
    List<Bike> findAllByOrderByIsRetiredAscIsDefaultDescIdDesc();
}