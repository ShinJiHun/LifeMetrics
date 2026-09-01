package com.lifemetrics.backend.dto;

import com.lifemetrics.backend.entity.PermanentCourse;
import lombok.Builder;
import lombok.Getter;

/**
 * 퍼머넌트 코스 1개 × gpx 파일 1개 조합.
 * 코스 폴더에 gpx가 여러 개면(예: 본코스/Plan B) permanentNo가 같은 행이 여러 개 나온다.
 * gpx가 아예 없으면 gpxFileName=null인 행 1개만 나온다.
 */
@Getter
@Builder
public class PermanentCourseDto {
    private String permanentNo;   // 예: "PT-01"
    private String name;
    private Integer distanceKm;
    private String timeLimitHm;   // "HH:MM"
    private String region;
    private String gpxFileName;   // 실제 파일명 (예: "PT-20_Plan_B_(2024).gpx"), 없으면 null
    private String gpxLabel;      // gpxFileName에서 확장자 제거한 표시용 라벨, 없으면 null
    private String polyline;      // 코스 대표 gpx를 인코딩한 경로 (DB에 미리 저장된 값, 코스당 1개)

    public static PermanentCourseDto from(PermanentCourse c, String gpxFileName) {
        return PermanentCourseDto.builder()
                .permanentNo(c.getPermanentNo())
                .name(c.getName())
                .distanceKm(c.getDistanceKm())
                .timeLimitHm(c.getTimeLimitHm())
                .region(c.getRegion())
                .gpxFileName(gpxFileName)
                .gpxLabel(gpxFileName == null ? null : gpxFileName.replaceAll("(?i)\\.gpx$", ""))
                .polyline(c.getPolyline())
                .build();
    }
}
