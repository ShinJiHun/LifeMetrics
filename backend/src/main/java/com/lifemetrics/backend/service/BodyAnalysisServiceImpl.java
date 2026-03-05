package com.lifemetrics.backend.service;

import com.lifemetrics.backend.client.AiClient;
import com.lifemetrics.backend.client.AiClientFactory;
import com.lifemetrics.backend.client.AiProvider;
import com.lifemetrics.backend.dto.AiAnalysisResponse;
import com.lifemetrics.backend.domain.GoalType;
import com.lifemetrics.backend.entity.BodyAnalysisResult;
import com.lifemetrics.backend.entity.BodyAnalysisSummaryState;
import com.lifemetrics.backend.entity.UserInbodyRecord;
import com.lifemetrics.backend.repository.BodyAnalysisLifetimeCurrentRepository;
import com.lifemetrics.backend.repository.BodyAnalysisResultRepository;
import com.lifemetrics.backend.repository.BodyAnalysisSummaryStateRepository;
import com.lifemetrics.backend.repository.UserInbodyRecordRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BodyAnalysisServiceImpl implements BodyAnalysisService {

    private final UserInbodyRecordRepository inbodyRepo;
    private final BodyAnalysisResultRepository analysisRepo;
    private final BodyAnalysisSummaryStateRepository summaryRepo;
    private final BodyAnalysisLifetimeCurrentRepository currentRepo;

    private final BodyAnalysisPromptService promptService;
    private final AiClientFactory aiClientFactory;

    @Transactional
    @Override
    public void analyzeAndUpdate(
            Long userId,
            Long bodyRecordId,
            GoalType goalType,
            AiProvider provider
    ) {
        // 1️⃣ 현재 인바디
        UserInbodyRecord curr = inbodyRepo.findById(bodyRecordId)
                .orElseThrow(() -> new IllegalArgumentException("Inbody not found"));

        // 2️⃣ 이전 인바디 (없을 수 있음)
        UserInbodyRecord prev =
                inbodyRepo.findPrevious(userId, curr.getRecordDate())
                        .orElse(null);

        // 3️⃣ 누적 서사 (없으면 빈 상태)
        BodyAnalysisSummaryState summary =
                summaryRepo.findById(userId)
                        .orElseGet(() -> BodyAnalysisSummaryState.empty(userId));

        // 4️⃣ 프롬프트 생성
        String prompt =
                promptService.build(goalType, summary, prev, curr);

        // 5️⃣ AI 호출
        AiClient client = aiClientFactory.get(provider);
        AiAnalysisResponse response = client.analyze(prompt);

        // 6️⃣ 이번 분석 결과 저장 (히스토리)
        BodyAnalysisResult result =
                BodyAnalysisResult.from(curr, goalType, provider, response);
        analysisRepo.save(result);

        // 7️⃣ 현재(goal별) 최신 분석 갱신 🔥
        currentRepo.upsert(
                userId,
                goalType,
                result.getId(),
                provider.name()
        );

        // 8️⃣ 누적 서사 업데이트 (🔥 핵심)
        summary.updateFrom(response);
        summaryRepo.save(summary);
    }
}
