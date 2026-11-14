package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.health.entity.HealthCheckup;
import com.lifemetrics.backend.health.repository.HealthCheckupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.lifemetrics.backend.service.AthleteContextService.num;

/** 국가 건강검진 결과. 원문(rawText)은 길어서 넣지 않고 판정·수치·소견만. */
@Component
@RequiredArgsConstructor
public class GetHealthCheckupsTool implements AthleteTool {

    private final HealthCheckupRepository checkupRepo;

    @Override
    public String name() {
        return "get_health_checkups";
    }

    @Override
    public String description() {
        return "건강검진 결과를 최신순으로 조회한다: 종합판정, 의심·유질환, 키·체중·BMI·허리둘레, 혈압, 혈색소, 공복혈당, "
                + "콜레스테롤(총·HDL·LDL·중성지방), 신장(크레아티닌·eGFR), 간(AST·ALT·GGT), 각 항목 판정, 생활습관 소견. "
                + "year 를 주면 그 해 검진만.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolInputs.schema(Map.of("year", Map.of("type", "integer", "description", "검진 연도. 없으면 전체")), List.of());
    }

    @Override
    public String execute(JsonNode input, long userId, LocalDate today) {
        Integer year = input != null && input.path("year").canConvertToInt() ? input.path("year").asInt() : null;
        List<String> lines = checkupRepo.findByUserIdOrderByCheckupDateDesc(userId).stream()
                .filter(c -> year == null || (c.getCheckupDate() != null && c.getCheckupDate().getYear() == year))
                .map(GetHealthCheckupsTool::line)
                .toList();
        return ToolInputs.lines("건강검진" + (year == null ? "" : " " + year + "년") + ": " + lines.size() + "회", lines, "year 로 좁혀라");
    }

    static String line(HealthCheckup c) {
        List<String> p = new ArrayList<>();
        opt(p, "종합판정", c.getOverallJudgment());
        opt(p, "의심질환", c.getSuspectedDisease());
        opt(p, "유질환", c.getExistingDisease());
        if (c.getWeightKg() != null) p.add("체중 " + num(c.getWeightKg(), "kg"));
        if (c.getBmi() != null) p.add("BMI " + num(c.getBmi(), "") + judged(c.getBmiGrade()));
        if (c.getWaistCm() != null) p.add("허리 " + num(c.getWaistCm(), "cm") + judged(c.getWaistResult()));
        if (c.getSystolicBp() != null) p.add("혈압 " + c.getSystolicBp() + "/" + c.getDiastolicBp() + judged(c.getBpResult()));
        if (c.getHemoglobin() != null) p.add("혈색소 " + num(c.getHemoglobin(), "g/dL") + judged(c.getAnemiaResult()));
        if (c.getFastingBloodSugar() != null) p.add("공복혈당 " + c.getFastingBloodSugar() + judged(c.getDiabetesResult()));
        List<String> lipid = new ArrayList<>();
        if (c.getTotalCholesterol() != null) lipid.add("총 " + c.getTotalCholesterol());
        if (c.getHdlCholesterol() != null) lipid.add("HDL " + c.getHdlCholesterol());
        if (c.getLdlCholesterol() != null) lipid.add("LDL " + c.getLdlCholesterol());
        if (c.getTriglyceride() != null) lipid.add("중성지방 " + c.getTriglyceride());
        if (!lipid.isEmpty()) p.add("지질 " + String.join("/", lipid) + judged(c.getLipidResult()));
        if (c.getEgfr() != null || c.getSerumCreatinine() != null) {
            p.add("신장 크레아티닌 " + num(c.getSerumCreatinine(), "") + ", eGFR " + c.getEgfr() + judged(c.getKidneyResult()));
        }
        if (c.getAst() != null) p.add("간 AST " + c.getAst() + "/ALT " + c.getAlt() + "/GGT " + c.getGgt() + judged(c.getLiverResult()));
        opt(p, "요단백", c.getUrineProteinResult());
        opt(p, "흉부", c.getChestXrayResult());
        opt(p, "생활습관 소견", c.getLifestyleAdvice());
        opt(p, "기타 소견", c.getEtcAdvice());
        return c.getCheckupDate() + (c.getCheckupOrg() == null ? "" : " " + c.getCheckupOrg()) + ": " + String.join(", ", p);
    }

    private static void opt(List<String> p, String label, String v) {
        if (v != null && !v.isBlank()) p.add(label + " " + v);
    }

    private static String judged(String result) {
        return result == null || result.isBlank() ? "" : " (" + result + ")";
    }
}
