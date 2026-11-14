package com.lifemetrics.backend.ai.tools;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 스프링 빈으로 등록된 AthleteTool 을 모아 API 용 tools 배열과 이름 조회를 제공한다. */
@Component
public class AthleteToolRegistry {

    private final Map<String, AthleteTool> byName = new LinkedHashMap<>();

    public AthleteToolRegistry(List<AthleteTool> tools) {
        tools.stream()
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .forEach(t -> {
                    if (byName.putIfAbsent(t.name(), t) != null) {
                        throw new IllegalStateException("도구 이름 중복: " + t.name());
                    }
                });
    }

    public Optional<AthleteTool> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** Messages API 의 tools 파라미터. 마지막 도구에 cache_control 을 달아 system+tools 를 캐시한다. */
    public List<Map<String, Object>> apiTools() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AthleteTool t : byName.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", t.name());
            m.put("description", t.description());
            m.put("input_schema", t.inputSchema());
            out.add(m);
        }
        if (!out.isEmpty()) out.get(out.size() - 1).put("cache_control", Map.of("type", "ephemeral"));
        return out;
    }

    public List<String> names() {
        return List.copyOf(byName.keySet());
    }
}
