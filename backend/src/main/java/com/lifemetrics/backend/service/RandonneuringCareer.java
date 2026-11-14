package com.lifemetrics.backend.service;

import com.lifemetrics.backend.entity.RandonneuringResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.lifemetrics.backend.entity.RandonneuringResult.DNF;
import static com.lifemetrics.backend.entity.RandonneuringResult.FINISHED;

/** 랜도너스 공식 기록을 사람이 읽는 요약·한 줄로 만든다 (챗 요약판과 get_randonneuring_record 가 공유). */
public final class RandonneuringCareer {

    private static final Set<Integer> SR_SERIES = Set.of(200, 300, 400, 600);

    private RandonneuringCareer() {}

    /** "공식 참가 36회 (완주 26, DNF 10), 완주 거리 6,394km, 최장 완주 …, 연도별 …" 한 줄. 기록이 없으면 null. */
    public static String summary(List<RandonneuringResult> all) {
        if (all.isEmpty()) return null;
        long finished = all.stream().filter(r -> FINISHED.equals(r.getStatus())).count();
        long dnf = all.stream().filter(r -> DNF.equals(r.getStatus())).count();
        int km = all.stream().filter(r -> FINISHED.equals(r.getStatus()) && r.getDistanceKm() != null)
                .mapToInt(RandonneuringResult::getDistanceKm).sum();
        StringBuilder l = new StringBuilder(String.format(Locale.ROOT,
                "공식 참가 %d회 (완주 %d, DNF %d), 완주 거리 %,dkm", all.size(), finished, dnf, km));

        all.stream()
                .filter(r -> FINISHED.equals(r.getStatus()) && r.getDistanceKm() != null && "BREVET".equals(r.getEventType()))
                .max(Comparator.comparing(RandonneuringResult::getDistanceKm).thenComparing(RandonneuringResult::getEventDate))
                .ifPresent(r -> l.append(", 최장 완주 브레베 ").append(r.getDistanceKm()).append("km (")
                        .append(r.getEventDate()).append(' ').append(r.getEventName()).append(')'));

        Map<Integer, List<RandonneuringResult>> byYear = new TreeMap<>(all.stream()
                .collect(Collectors.groupingBy(r -> r.getEventDate().getYear())));
        List<String> years = new ArrayList<>();
        List<Integer> srYears = new ArrayList<>();
        byYear.forEach((y, rs) -> {
            long f = rs.stream().filter(r -> FINISHED.equals(r.getStatus())).count();
            years.add(y + "년 " + rs.size() + "회(완주 " + f + ")");
            Set<Integer> done = rs.stream()
                    .filter(r -> FINISHED.equals(r.getStatus()) && "BREVET".equals(r.getEventType()))
                    .map(RandonneuringResult::getDistanceKm).filter(Objects::nonNull).collect(Collectors.toSet());
            if (done.containsAll(SR_SERIES)) srYears.add(y);
        });
        l.append(", 연도별 ").append(String.join(" · ", years));
        if (!srYears.isEmpty()) {
            l.append(", 같은 해 200·300·400·600km 브레베를 모두 완주한 해: ")
                    .append(srYears.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
        all.stream().filter(r -> DNF.equals(r.getStatus())).max(Comparator.comparing(RandonneuringResult::getEventDate))
                .ifPresent(r -> l.append(", 최근 DNF ").append(r.getEventDate()).append(' ').append(r.getEventName()));
        return l.toString();
    }

    /** "2026-10-03 [BREVET] 서울-부산-서울 1000K 1000km DNF (활동 24596908919) / 메모: …" */
    public static String line(RandonneuringResult r) {
        StringBuilder l = new StringBuilder(r.getEventDate().toString())
                .append(" [").append(r.getEventType()).append("] ").append(r.getEventName());
        l.append(r.getDistanceKm() == null ? " 거리 미상" : " " + r.getDistanceKm() + "km");
        l.append(' ').append(r.getStatus());
        if (r.getActivityCoreId() != null) l.append(" (활동 ").append(r.getActivityCoreId()).append(')');
        if (r.getNotes() != null && !r.getNotes().isBlank()) l.append(" / 메모: ").append(r.getNotes());
        return l.toString();
    }
}
