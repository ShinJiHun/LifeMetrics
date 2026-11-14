package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.ActivityWeather;
import com.lifemetrics.backend.entity.Segment;
import com.lifemetrics.backend.entity.SegmentEffort;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.ActivityWeatherRepository;
import com.lifemetrics.backend.repository.SegmentEffortRepository;
import com.lifemetrics.backend.repository.SegmentRepository;
import com.lifemetrics.backend.service.AthleteContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.lifemetrics.backend.service.AthleteContextService.hm;
import static com.lifemetrics.backend.service.AthleteContextService.num;

/** 라이딩 한 건의 상세: 속도·케이던스·파워·심박 최대값, 지형 분포, 날씨, 구간 기록. */
@Component
@RequiredArgsConstructor
public class GetActivityDetailTool implements AthleteTool {

    static final int MAX_SEGMENTS = 20;

    private final ActivityCoreRepository activityRepo;
    private final ActivityWeatherRepository weatherRepo;
    private final SegmentEffortRepository effortRepo;
    private final SegmentRepository segmentRepo;

    @Override
    public String name() {
        return "get_activity_detail";
    }

    @Override
    public String description() {
        return "라이딩 한 건의 상세를 조회한다: 기본 기록, 경과시간, 평균/최대 속도(km/h)·케이던스·심박·파워, NP, 좌우 밸런스, "
                + "오르막/평지/내리막 거리, 상대 강도, 날씨(기온·습도·바람), 지난 구간(세그먼트) 기록과 PR 순위. "
                + "activity_id 는 get_activities 결과의 [id] 값을 쓴다.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolInputs.schema(Map.of("activity_id", Map.of("type", "integer", "description", "라이딩 기록 id")),
                List.of("activity_id"));
    }

    @Override
    public String execute(JsonNode input, long userId, LocalDate today) {
        JsonNode idNode = input == null ? null : input.get("activity_id");
        if (idNode == null || !idNode.canConvertToLong()) throw new IllegalArgumentException("activity_id(정수)는 필수입니다.");
        long id = idNode.asLong();
        ActivityCore a = activityRepo.findById(id)
                .filter(x -> Objects.equals(x.getUserId(), userId))
                .orElseThrow(() -> new IllegalArgumentException("id " + id + " 인 라이딩 기록이 없습니다."));

        List<String> lines = new ArrayList<>();
        lines.add("기록: " + AthleteContextService.rideLine(a));
        List<String> p = new ArrayList<>();
        if (a.getElapsedTime() != null) p.add("경과 " + hm(a.getElapsedTime()));
        pair(p, "속도", a.getAvgSpeed(), a.getMaxSpeed(), "km/h");
        pair(p, "케이던스", a.getAvgCadence(), a.getMaxCadence(), "rpm");
        pair(p, "심박", a.getAvgHeartRate(), a.getMaxHeartRate(), "bpm");
        pair(p, "파워", a.getAvgPower(), a.getMaxPower(), "W");
        if (a.getNormalizedPower() != null) p.add("NP " + num(a.getNormalizedPower(), "W"));
        if (a.getLeftRightBalance() != null) p.add("좌우 밸런스 " + num(a.getLeftRightBalance(), ""));
        if (a.getPowerSource() != null) p.add("파워 출처 " + a.getPowerSource());
        if (a.getRelativeEffort() != null) p.add("상대 강도 " + a.getRelativeEffort());
        if (!p.isEmpty()) lines.add("측정값: " + String.join(", ", p));

        List<String> terrain = new ArrayList<>();
        km(terrain, "오르막", a.getUphillDistance());
        km(terrain, "평지", a.getFlatDistance());
        km(terrain, "내리막", a.getDownDistance());
        if (a.getTotalDescent() != null) terrain.add("하강 " + Math.round(a.getTotalDescent()) + "m");
        if (!terrain.isEmpty()) lines.add("지형: " + String.join(", ", terrain));
        if (a.getLocationName() != null) lines.add("지역: " + a.getLocationName());

        weatherRepo.findByActivityCoreId(id).ifPresent(w -> lines.add("날씨: " + weatherLine(w)));

        List<SegmentEffort> efforts = effortRepo.findByActivityCoreId(id);
        for (SegmentEffort e : efforts.stream().limit(MAX_SEGMENTS).toList()) {
            String seg = segmentRepo.findById(e.getSegmentId()).map(Segment::getName).orElse("구간 " + e.getSegmentId());
            lines.add("구간 [" + e.getSegmentId() + "] " + seg + ": " + effortLine(e));
        }
        if (efforts.size() > MAX_SEGMENTS) lines.add("(구간 " + efforts.size() + "개 중 " + MAX_SEGMENTS + "개만 표시)");

        return ToolInputs.lines("라이딩 상세 [" + id + "]", lines, "");
    }

    static String weatherLine(ActivityWeather w) {
        List<String> p = new ArrayList<>();
        if (w.getTemperature() != null) p.add("기온 " + num(w.getTemperature(), "°C"));
        if (w.getHumidity() != null) p.add("습도 " + num(w.getHumidity(), "%"));
        if (w.getWindSpeed() != null) p.add("풍속 " + num(w.getWindSpeed(), "m/s"));
        if (w.getWindDeg() != null) p.add("풍향 " + w.getWindDeg() + "°");
        return p.isEmpty() ? "값 없음" : String.join(", ", p);
    }

    static String effortLine(SegmentEffort e) {
        List<String> p = new ArrayList<>();
        if (e.getElapsedTimeSec() != null) p.add(mmss(e.getElapsedTimeSec()));
        if (e.getAvgPower() != null) p.add("평균파워 " + Math.round(e.getAvgPower()) + "W");
        if (e.getAvgHeartRate() != null) p.add("평균심박 " + e.getAvgHeartRate());
        if (e.getAvgCadence() != null) p.add("케이던스 " + e.getAvgCadence());
        if (e.getPrRank() != null) p.add("PR " + e.getPrRank() + "위");
        return String.join(", ", p);
    }

    static String mmss(int sec) {
        return sec >= 3600 ? hm(sec) : String.format(Locale.ROOT, "%d분 %02d초", sec / 60, sec % 60);
    }

    private static void pair(List<String> p, String label, Double avg, Double max, String unit) {
        if (avg == null && max == null) return;
        p.add(label + " 평균 " + num(avg, unit) + " / 최대 " + num(max, unit));
    }

    private static void km(List<String> p, String label, Double meters) {
        if (meters != null) p.add(label + String.format(Locale.ROOT, " %.1fkm", meters / 1000));
    }
}
