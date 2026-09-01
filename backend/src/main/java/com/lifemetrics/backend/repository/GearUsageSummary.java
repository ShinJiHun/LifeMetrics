package com.lifemetrics.backend.repository;

/** 자전거 한 대에 대해 (앞기어, 뒷기어, 지형)별로 합산한 기어 사용량. */
public interface GearUsageSummary {
    Integer getFrontGear();

    Integer getRearGear();

    String getTerrain();

    Double getDurationSec();

    Double getDistance();
}
