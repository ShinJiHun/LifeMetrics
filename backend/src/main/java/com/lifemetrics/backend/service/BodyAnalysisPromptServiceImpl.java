package com.lifemetrics.backend.service;

import com.lifemetrics.backend.domain.GoalType;
import com.lifemetrics.backend.entity.BodyAnalysisSummaryState;
import com.lifemetrics.backend.entity.UserInbodyRecord;
import org.springframework.stereotype.Service;

@Service
public class BodyAnalysisPromptServiceImpl implements BodyAnalysisPromptService {

    @Override
    public String build(
            GoalType goalType,
            BodyAnalysisSummaryState summary,
            UserInbodyRecord prev,
            UserInbodyRecord curr
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
            sb.append("\n[이전 인바디]\n");
            sb.append(format(prev));
        }

        // ⏺ 현재 기록
        sb.append("\n[현재 인바디]\n");
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

    private String format(UserInbodyRecord r) {
        return String.format("""
        - 날짜: %s
        - 체중: %.1f kg
        - 골격근량: %.1f kg
        - 체지방률: %.1f %%
        - BMI: %.1f
        """,
                r.getRecordDate(),
                r.getWeight(),
                r.getSkeletalMuscleMass(),
                r.getBodyFatPercentage(),
                r.getBmi()
        );
    }
}
