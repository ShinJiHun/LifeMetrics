package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BrevetCourseDto {
    private String folderName;   // NAS 폴더명 (예: "1200_H-S_260219")
    private String courseName;   // GPX 파일명에서 확장자 제거 (표시용)
    private String gpxFileName;  // 실제 GPX 파일명 (예: "1200_H-S_260219.gpx")
    private String distance;     // "200K", "300K", ... 또는 "-"
    private double fileSizeKb;   // 파일 크기 (KB)
}
