package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 포트폴리오 히어로/whoami 카드에 쓰는 집계값. 경력·프로젝트 테이블에서 계산해서 내려준다.
 */
@Getter
@Builder
public class ProfileStatsDto {
    private int totalCareerMonths;   // 재직 기간 합산(개월)
    private String totalCareerLabel; // "8년 4개월"
    private int companyCount;        // 소속 기업 수
    private int projectCount;        // 참여 프로젝트 수
    private boolean employed;        // 재직중인 회사가 있는지
    private String currentCompany;   // 재직중이면 회사 표기명(shortName 우선), 아니면 ""
    private String currentSince;     // 재직중이면 입사월 "yyyy-MM", 아니면 ""
}
