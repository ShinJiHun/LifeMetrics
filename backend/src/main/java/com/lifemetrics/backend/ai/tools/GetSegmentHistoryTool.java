package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.entity.Segment;
import com.lifemetrics.backend.entity.SegmentEffort;
import com.lifemetrics.backend.repository.SegmentEffortRepository;
import com.lifemetrics.backend.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 한 구간(세그먼트)의 내 기록 이력. id 또는 이름(부분 일치)으로 찾는다. */
@Component
@RequiredArgsConstructor
public class GetSegmentHistoryTool implements AthleteTool {

    static final int MAX_EFFORTS = 30;

    private final SegmentRepository segmentRepo;
    private final SegmentEffortRepository effortRepo;

    @Override
    public String name() {
        return "get_segment_history";
    }

    @Override
    public String description() {
        return "구간(세그먼트, 예: 업힐 코스) 하나의 내 기록 이력을 날짜순으로 조회한다: 날짜, 기록 시간, 평균파워, 평균심박, PR 순위. "
                + "segment_id 또는 name(부분 일치)으로 찾는다. 이름이 여러 구간에 맞으면 후보 목록을 돌려준다.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("segment_id", Map.of("type", "integer", "description", "구간 id"));
        p.put("name", Map.of("type", "string", "description", "구간 이름 일부 (segment_id 가 없을 때)"));
        return ToolInputs.schema(p, List.of());
    }

    @Override
    public String execute(JsonNode input, long userId, LocalDate today) {
        Segment seg = find(input);
        List<SegmentEffort> efforts = effortRepo.findBySegmentIdAndUserIdOrderByTime(seg.getId(), userId).stream()
                .filter(e -> e.getStartTime() != null)
                .sorted(Comparator.comparing(SegmentEffort::getStartTime))
                .toList();
        List<String> lines = efforts.stream()
                .skip(Math.max(0, efforts.size() - MAX_EFFORTS))
                .map(e -> e.getStartTime().toLocalDate() + " (활동 " + e.getActivityCoreId() + "): "
                        + GetActivityDetailTool.effortLine(e))
                .toList();
        StringBuilder header = new StringBuilder("구간 [" + seg.getId() + "] " + seg.getName());
        if (seg.getDistance() != null) header.append(String.format(Locale.ROOT, ", %.2fkm", seg.getDistance() / 1000));
        if (seg.getAvgGrade() != null) header.append(String.format(Locale.ROOT, ", 평균경사 %.1f%%", seg.getAvgGrade()));
        header.append(": 기록 ").append(efforts.size()).append("회")
                .append(efforts.size() > MAX_EFFORTS ? ", 최근 " + MAX_EFFORTS + "회 표시" : "");
        return ToolInputs.lines(header.toString(), lines, "");
    }

    private Segment find(JsonNode input) {
        JsonNode idNode = input == null ? null : input.get("segment_id");
        if (idNode != null && idNode.canConvertToLong()) {
            long id = idNode.asLong();
            return segmentRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("id " + id + " 인 구간이 없습니다."));
        }
        String name = ToolInputs.text(input, "name");
        if (name == null) throw new IllegalArgumentException("segment_id 또는 name 중 하나는 필요합니다.");
        String q = name.toLowerCase(Locale.ROOT).replace(" ", "");
        List<Segment> hits = segmentRepo.findByIsActiveTrue().stream()
                .filter(s -> s.getName() != null && s.getName().toLowerCase(Locale.ROOT).replace(" ", "").contains(q))
                .toList();
        if (hits.isEmpty()) throw new IllegalArgumentException("이름에 '" + name + "' 가 들어간 구간이 없습니다.");
        if (hits.size() > 1) {
            StringBuilder sb = new StringBuilder("'" + name + "' 에 맞는 구간이 " + hits.size() + "개입니다. segment_id 로 다시 호출하라: ");
            hits.stream().limit(15).forEach(s -> sb.append("[").append(s.getId()).append("] ").append(s.getName()).append("; "));
            throw new IllegalArgumentException(sb.toString().trim());
        }
        return hits.get(0);
    }
}
