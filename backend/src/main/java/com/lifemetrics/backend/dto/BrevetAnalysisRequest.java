package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BrevetAnalysisRequest {

    private Long userId = 1L;
    private String eventName;       // 대회명 (예: BRM 200 수원-천안)
    private String eventDate;       // 대회 날짜 (예: 2026-05-10)
    private String startTime;       // 출발 시간 (예: 06:00)
    private Integer timeLimit;      // 제한 시간 (시간)
    private Double targetSpeed;     // 목표 평균 속도 (km/h)
    private Double totalDistance;   // 총 거리 (km)
    private Integer totalAscent;    // 총 획득 고도 (m)
    private List<CpWeatherDto> cps; // CP별 날씨 정보

    @Getter
    @Setter
    public static class CpWeatherDto {
        private String name;         // CP 이름 (예: CP1 남당항)
        private Integer distance;    // 누적 거리 (km)
        private Integer elevation;   // 누적 고도 (m)
        private String targetArrival;// 목표 도착 시간 (예: 11:32)
        private String deadline;     // 마감 시간 (예: 11:28)
        private Double lat;
        private Double lon;

        // 날씨
        private Double temp;         // 기온 (°C)
        private Double wind;         // 풍속 (km/h)
        private Integer windDir;     // 풍향 (도)
        private Integer precip;      // 강수확률 (%)
        private String weatherDesc;  // 날씨 설명 (예: 맑음)

        // 구간 풍향 분석 (프론트에서 계산해서 보내줌)
        private String windEffect;   // 역풍/순풍/측풍/무풍
    }
}
