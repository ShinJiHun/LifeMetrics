package com.lifemetrics.backend.randonneuring;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.RandonneuringResult;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.PermanentCourseRepository;
import com.lifemetrics.backend.repository.RandonneuringResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KORA 기록 API 응답(JSON)을 randonneuring_result 로 가져온다.
 * 같은 기록은 원본 id(source_id) 로 찾아 갱신하고, 새 기록만 추가한다(여러 번 가져와도 안전).
 * 가져온 뒤 같은 날 GPS 라이딩을 연결하고, 연결된 라이딩의 비어 있는 유형을 채운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RandonneuringImportService {

    static final double MIN_LINK_METERS = 50_000;
    private static final Pattern PT_NO = Pattern.compile("^(PT-\\d+)");

    private final RandonneuringResultRepository repo;
    private final ActivityCoreRepository activityRepo;
    private final PermanentCourseRepository courseRepo;

    public record ImportResult(int total, int inserted, int updated, int skipped, int linked, int tagged,
                               List<String> warnings) {}

    /** 원본 항목 하나를 정규화한 값. */
    record Item(String sourceId, LocalDate date, String organizer, String type, String name, String permanentNo,
                String officialNo, Integer km, String status, Integer seconds, String homologationNo) {}

    @Transactional
    public ImportResult importKora(long userId, JsonNode root) {
        JsonNode items = root == null ? null : (root.isArray() ? root : root.path("items"));
        if (items == null || !items.isArray()) {
            throw new IllegalArgumentException("items 배열이 없습니다. KORA 기록 API 응답 JSON 전체를 붙여 넣어 주세요.");
        }
        int inserted = 0, updated = 0, skipped = 0;
        List<String> warnings = new ArrayList<>();
        List<RandonneuringResult> touched = new ArrayList<>();
        for (JsonNode n : items) {
            Item it;
            try {
                it = parse(n);
            } catch (IllegalArgumentException e) {
                skipped++;
                warnings.add(e.getMessage());
                continue;
            }
            Optional<RandonneuringResult> existing = it.sourceId() != null
                    ? repo.findBySourceId(it.sourceId()) : Optional.empty();
            RandonneuringResult r = existing.orElseGet(() -> {
                RandonneuringResult x = new RandonneuringResult();
                x.setUserId(userId);
                x.setEventName(displayName(it));
                return x;
            });
            if (existing.isEmpty() && repo.findByUserIdAndEventDateAndEventName(userId, it.date(), r.getEventName()).isPresent()) {
                skipped++;
                warnings.add(it.date() + " " + r.getEventName() + ": 같은 날짜·코스명 기록이 이미 있어 건너뜀");
                continue;
            }
            apply(r, it);
            repo.save(r);
            touched.add(r);
            if (existing.isPresent()) updated++; else inserted++;
        }
        int[] lt = linkAndTag(userId, touched);
        log.info("랜도너스 기록 가져오기: 전체 {} 추가 {} 갱신 {} 건너뜀 {} 연결 {} 유형 {}",
                items.size(), inserted, updated, skipped, lt[0], lt[1]);
        return new ImportResult(items.size(), inserted, updated, skipped, lt[0], lt[1], warnings);
    }

    static Item parse(JsonNode n) {
        String status = text(n, "status");
        if (status == null) throw new IllegalArgumentException("status 없는 항목 건너뜀: " + text(n, "course_name"));
        status = status.toUpperCase(Locale.ROOT);
        if (!status.equals(RandonneuringResult.FINISHED) && !status.equals(RandonneuringResult.DNF)) {
            throw new IllegalArgumentException("알 수 없는 상태 '" + status + "': " + text(n, "course_name"));
        }
        String dateText = Optional.ofNullable(text(n, "event_date")).orElse(text(n, "started_at"));
        if (dateText == null || dateText.length() < 10) {
            throw new IllegalArgumentException("날짜 없는 항목 건너뜀: " + text(n, "course_name"));
        }
        LocalDate date = LocalDate.parse(dateText.substring(0, 10));

        String name = Optional.ofNullable(text(n, "course_name")).orElse("(코스명 없음)");
        String rawNo = Optional.ofNullable(text(n, "permanent_no")).orElseGet(() -> {
            Matcher m = PT_NO.matcher(name);
            return m.find() ? m.group(1) : null;
        });
        String baseNo = null;
        if (rawNo != null) {
            Matcher m = PT_NO.matcher(rawNo);
            baseNo = m.find() ? m.group(1) : rawNo;
        }
        String code = Optional.ofNullable(text(n, "ride_type_code")).orElse("").toUpperCase(Locale.ROOT);
        String type = name.contains("플레쉬") || name.toUpperCase(Locale.ROOT).contains("FLECHE") ? "FLECHE"
                : code.equals("PERMANENT") || baseNo != null ? "PERMANENT"
                : code.equals("POPULAIRE") ? "POPULAIRE" : "BREVET";

        String sourceId = text(n, "kora_source_id");
        if (sourceId == null && n.hasNonNull("entry_id")) sourceId = "ebr:" + n.get("entry_id").asText();

        Integer km = null;
        JsonNode d = n.get("distance_km");
        if (d != null && !d.isNull() && !d.asText().isBlank()) km = new BigDecimal(d.asText().trim()).setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        Integer sec = n.hasNonNull("riding_time_sec") ? n.get("riding_time_sec").asInt() : null;
        String organizer = "kora".equalsIgnoreCase(text(n, "source")) ? "KORA" : null;
        return new Item(sourceId, date, organizer, type, name, baseNo, rawNo, km,
                status, status.equals(RandonneuringResult.DNF) ? null : sec, text(n, "homologation_no"));
    }

    /** 기존 표기와 맞춘 코스명: 퍼머넌트는 "[PT-xx] 이름" (이름 앞의 번호는 뺌). */
    static String displayName(Item it) {
        if (!"PERMANENT".equals(it.type()) || it.officialNo() == null) return it.name();
        String bare = it.name().replaceFirst("^\\[?PT-[\\d-]+]?\\s*", "").trim();
        return "[" + it.officialNo() + "] " + (bare.isEmpty() ? it.name() : bare);
    }

    private static void apply(RandonneuringResult r, Item it) {
        r.setEventDate(it.date());
        r.setOrganizer(it.organizer());
        r.setEventType(it.type());
        r.setPermanentNo(it.permanentNo());
        if (it.km() != null) r.setDistanceKm(it.km());
        r.setStatus(it.status());
        r.setRidingTimeSec(it.seconds());
        r.setSourceId(it.sourceId());
        r.setHomologationNo(it.homologationNo());
        if (it.officialNo() != null && !it.officialNo().equals(it.permanentNo())
                && (r.getNotes() == null || !r.getNotes().contains(it.officialNo()))) {
            r.setNotes((r.getNotes() == null ? "" : r.getNotes() + " / ") + "공식 번호 " + it.officialNo());
        }
    }

    /**
     * 연결이 없는 기록을 같은 날 GPS 라이딩과 잇는다: 유형(+퍼머넌트 번호)이 같은 라이딩 우선, 없으면 50km 이상 중 최장.
     * 같은 날 대회가 둘 이상이면 어느 라이딩인지 알 수 없어 잇지 않는다. 이은 라이딩의 유형이 비어 있으면 채운다.
     * @return [연결 수, 유형 채운 수]
     */
    int[] linkAndTag(long userId, List<RandonneuringResult> results) {
        int linked = 0, tagged = 0;
        for (RandonneuringResult r : results) {
            if (r.getActivityCoreId() != null) continue;
            if (repo.countByUserIdAndEventDate(userId, r.getEventDate()) > 1) continue;
            List<ActivityCore> rides = activityRepo.findByUserIdAndDateRange(userId,
                    r.getEventDate().atStartOfDay(), r.getEventDate().plusDays(1).atStartOfDay());
            Optional<ActivityCore> pick = rides.stream()
                    .filter(a -> r.getEventType().equals(a.getRideType())
                            && (r.getPermanentNo() == null || Objects.equals(r.getPermanentNo(), a.getPermanentNo())))
                    .findFirst()
                    .or(() -> rides.stream()
                            .filter(a -> a.getTotalDistance() != null && a.getTotalDistance() > MIN_LINK_METERS)
                            .max(Comparator.comparing(ActivityCore::getTotalDistance)));
            if (pick.isEmpty()) continue;
            ActivityCore a = pick.get();
            r.setActivityCoreId(a.getId());
            linked++;
            if (a.getRideType() == null) {
                a.setRideType(r.getEventType());
                if ("PERMANENT".equals(r.getEventType()) && a.getPermanentNo() == null
                        && r.getPermanentNo() != null && courseRepo.existsById(r.getPermanentNo())) {
                    a.setPermanentNo(r.getPermanentNo());
                }
                tagged++;
            }
        }
        return new int[]{linked, tagged};
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText().trim();
    }
}
