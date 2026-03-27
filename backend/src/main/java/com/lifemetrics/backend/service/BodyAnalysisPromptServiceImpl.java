package com.lifemetrics.backend.service;

import com.lifemetrics.backend.domain.GoalType;
import com.lifemetrics.backend.entity.BodyAnalysisSummaryState;
import com.lifemetrics.backend.entity.MeasurementType;
import com.lifemetrics.backend.entity.UserBodyRecord;
import org.springframework.stereotype.Service;

@Service
public class BodyAnalysisPromptServiceImpl implements BodyAnalysisPromptService {

    @Override
    public String build(
            GoalType goalType,
            BodyAnalysisSummaryState summary,
            UserBodyRecord prev,
            UserBodyRecord curr
    ) {

        StringBuilder sb = new StringBuilder();

        sb.append("""
        당신은 전문 트레이너이자 헬스 데이터 분석 AI입니다.
        반드시 한국어로만 답변하세요.
        결과는 JSON 형식으로 반환하세요.
        """);

        // 🎯 목표
        sb.append("\n[분석 목표]\n");
        sb.append(goalType.name()).append("\n");

        // 📜 누적 서사
        sb.append("\n[기존 요약 서사]\n");
        if (summary.isEmpty()) {
            sb.append("아직 요약 서사가 없습니다.\n");
        } else {
            sb.append(summary.getSummaryJson()).append("\n");
        }

        // ⏮ 이전 기록
        if (prev != null) {
            sb.append("\n[이전 측정 기록]\n");
            sb.append(format(prev));
        }

        // ⏺ 현재 기록
        sb.append("\n[현재 측정 기록]\n");
        sb.append(format(curr));

        // 🧠 요청
        sb.append("""
        \n[요청]
        1. 이전 기록 대비 변화 요약
        2. 전체 누적 흐름에 대한 평가
        3. 현재 상태에 대한 해석
        4. 요약 서사를 업데이트할 문장

        JSON 스키마:
        {
          "delta_summary": "...",
          "trend_summary": "...",
          "current_analysis": "...",
          "updated_lifetime_summary": "..."
        }
        """);

        return sb.toString();
    }

    private String format(UserBodyRecord r) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("- 날짜: %s%n", r.getRecordDate()));
        sb.append(String.format("- 측정 방식: %s%n", r.getMeasurementType() == MeasurementType.INBODY ? "인바디 기기" : "FitDays 체중계"));
        sb.append(String.format("- 체중: %.1f kg%n", r.getWeight()));

        if (r.getBmi() != null) {
            sb.append(String.format("- BMI: %.1f%n", r.getBmi()));
        }
        if (r.getBodyFatPercentage() != null) {
            sb.append(String.format("- 체지방률: %.1f %%%n", r.getBodyFatPercentage()));
        }
        if (r.getBodyFatMass() != null) {
            sb.append(String.format("- 체지방량: %.1f kg%n", r.getBodyFatMass()));
        }

        // INBODY 전용
        if (r.getSkeletalMuscleMass() != null) {
            sb.append(String.format("- 골격근량: %.1f kg%n", r.getSkeletalMuscleMass()));
        }
        if (r.getVisceralFatLevel() != null) {
            sb.append(String.format("- 내장지방레벨: %d%n", r.getVisceralFatLevel()));
        }

        // FITDAYS 전용
        if (r.getBodyWater() != null) {
            sb.append(String.format("- 체수분: %.1f kg%n", r.getBodyWater()));
        }
        if (r.getBoneMass() != null) {
            sb.append(String.format("- 골량: %.1f kg%n", r.getBoneMass()));
        }
        if (r.getBasalMetabolicRate() != null) {
            sb.append(String.format("- 기초대사량: %.0f kcal%n", r.getBasalMetabolicRate()));
        }

        return sb.toString();
    }
}
