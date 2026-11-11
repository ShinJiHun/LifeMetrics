package com.lifemetrics.backend.health.service;

import com.lifemetrics.backend.health.entity.HealthTreatment;
import com.lifemetrics.backend.health.repository.HealthTreatmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 치료·부상 기록. 분석 서비스들은 {@link #activeOn} / {@link #promptLines} 로 그 날짜에 진행 중이던 치료를 받아 간다.
 */
@Service
@RequiredArgsConstructor
public class HealthTreatmentService {

    private final HealthTreatmentRepository repo;

    public List<HealthTreatment> list(long userId) {
        return repo.findAllForList(userId);
    }

    public List<HealthTreatment> activeOn(long userId, LocalDate date) {
        return repo.findActiveOn(userId, date);
    }

    @Transactional
    public HealthTreatment create(long userId, HealthTreatment req) {
        HealthTreatment t = new HealthTreatment();
        t.setUserId(userId);
        apply(t, req);
        return repo.save(t);
    }

    @Transactional
    public Optional<HealthTreatment> update(long id, HealthTreatment req) {
        return repo.findById(id).map(t -> {
            apply(t, req);
            return repo.save(t);
        });
    }

    @Transactional
    public boolean delete(long id) {
        if (!repo.existsById(id)) return false;
        repo.deleteById(id);
        return true;
    }

    private static void apply(HealthTreatment t, HealthTreatment req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("진단명/부상명을 입력하세요.");
        }
        if (req.getStartDate() == null) {
            throw new IllegalArgumentException("시작일을 입력하세요.");
        }
        if (req.getEndDate() != null && req.getEndDate().isBefore(req.getStartDate())) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다.");
        }
        t.setName(req.getName().trim());
        t.setBodyPart(blankToNull(req.getBodyPart()));
        t.setStartDate(req.getStartDate());
        t.setEndDate(req.getEndDate());
        t.setRestrictions(blankToNull(req.getRestrictions()));
        t.setNotes(blankToNull(req.getNotes()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ── 분석 입력용 ──────────────────────────────────

    /** 일일분석 JSON 입력용. 값이 없는 항목은 뺀다. */
    public List<Map<String, Object>> inputOf(long userId, LocalDate date) {
        return activeOn(userId, date).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", t.getName());
            if (t.getBodyPart() != null) m.put("bodyPart", t.getBodyPart());
            m.put("startDate", t.getStartDate().toString());
            m.put("daysSinceStart", date.toEpochDay() - t.getStartDate().toEpochDay());
            if (t.getRestrictions() != null) m.put("restrictions", t.getRestrictions());
            if (t.getNotes() != null) m.put("notes", t.getNotes());
            return m;
        }).toList();
    }

    /** 라이딩·웨이트 세션 프롬프트용 블록. 그날 치료 중인 게 없으면 빈 문자열. */
    public String promptBlock(long userId, LocalDate date) {
        return promptBlock(activeOn(userId, date), date);
    }

    static String promptBlock(List<HealthTreatment> active, LocalDate date) {
        if (active.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("[진행 중인 치료·부상 (본인 입력)]\n");
        for (HealthTreatment t : active) {
            sb.append("- ").append(t.getName());
            if (t.getBodyPart() != null) sb.append(" (").append(t.getBodyPart()).append(')');
            sb.append(": ").append(t.getStartDate()).append("부터 치료 중, ")
                    .append(date.toEpochDay() - t.getStartDate().toEpochDay()).append("일째\n");
            if (t.getRestrictions() != null) sb.append("  운동 제한·권고: ").append(t.getRestrictions()).append('\n');
            if (t.getNotes() != null) sb.append("  메모: ").append(t.getNotes()).append('\n');
        }
        sb.append("""
                → 강도·부위 선택·볼륨을 이 치료 상황에 맞춰 해석하세요. 치료 때문에 일부러 가볍게 하거나 피한 것은
                  부족함이 아니라 적절한 조절로 평가하고, 제한·권고를 어긴 것으로 보이는 부분이 있으면 짚어 주세요.
                  개선점·다음 처방도 치료 중임을 전제로 제시하세요.

                """);
        return sb.toString();
    }
}
