package com.lifemetrics.backend.dto;

import com.lifemetrics.backend.entity.HealthCheckup;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 건강검진 1건. 조회 응답 · 저장 요청 · PDF 추출 결과에 모두 같은 shape 를 쓴다.
 * id / createdAt / updatedAt 은 응답 전용(요청 시 무시).
 */
@Data
@NoArgsConstructor
public class HealthCheckupDto {

    private Long id;
    private LocalDate checkupDate;
    private String checkupPlace;
    private String checkupOrg;
    private String checkupDoctor;
    private String overallJudgment;
    private String extraExams;
    private String suspectedDisease;
    private String existingDisease;
    private String lifestyleAdvice;
    private String etcAdvice;

    private Double heightCm;
    private Double weightKg;
    private Double bmi;
    private String bmiGrade;
    private Double waistCm;
    private String waistResult;
    private Double visionLeft;
    private Double visionRight;
    private Boolean visionCorrected;
    private String hearingLeft;
    private String hearingRight;
    private String hearingResult;

    private Integer systolicBp;
    private Integer diastolicBp;
    private String bpResult;

    private Double hemoglobin;
    private String anemiaResult;
    private Integer fastingBloodSugar;
    private String diabetesResult;
    private Integer totalCholesterol;
    private Integer hdlCholesterol;
    private Integer triglyceride;
    private Integer ldlCholesterol;
    private String lipidResult;
    private Double serumCreatinine;
    private Integer egfr;
    private String kidneyResult;
    private Integer ast;
    private Integer alt;
    private Integer ggt;
    private String liverResult;

    private String urineProteinResult;
    private String chestXrayResult;

    private String pastHistory;
    private String medication;
    private Boolean needSmokingCessation;
    private Boolean needDrinkingReduction;
    private Boolean needPhysicalActivity;
    private Boolean needStrengthExercise;

    private String hepBResult;
    private String hepCResult;
    private String depressionResult;
    private Integer depressionScore;
    private String psychosisResult;
    private String cognitiveResult;
    private String boneDensityResult;
    private String urinationResult;

    private String rawText;
    private String sourceFile;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static HealthCheckupDto from(HealthCheckup e) {
        HealthCheckupDto d = new HealthCheckupDto();
        d.id = e.getId();
        d.checkupDate = e.getCheckupDate();
        d.checkupPlace = e.getCheckupPlace();
        d.checkupOrg = e.getCheckupOrg();
        d.checkupDoctor = e.getCheckupDoctor();
        d.overallJudgment = e.getOverallJudgment();
        d.extraExams = e.getExtraExams();
        d.suspectedDisease = e.getSuspectedDisease();
        d.existingDisease = e.getExistingDisease();
        d.lifestyleAdvice = e.getLifestyleAdvice();
        d.etcAdvice = e.getEtcAdvice();
        d.heightCm = e.getHeightCm();
        d.weightKg = e.getWeightKg();
        d.bmi = e.getBmi();
        d.bmiGrade = e.getBmiGrade();
        d.waistCm = e.getWaistCm();
        d.waistResult = e.getWaistResult();
        d.visionLeft = e.getVisionLeft();
        d.visionRight = e.getVisionRight();
        d.visionCorrected = e.getVisionCorrected();
        d.hearingLeft = e.getHearingLeft();
        d.hearingRight = e.getHearingRight();
        d.hearingResult = e.getHearingResult();
        d.systolicBp = e.getSystolicBp();
        d.diastolicBp = e.getDiastolicBp();
        d.bpResult = e.getBpResult();
        d.hemoglobin = e.getHemoglobin();
        d.anemiaResult = e.getAnemiaResult();
        d.fastingBloodSugar = e.getFastingBloodSugar();
        d.diabetesResult = e.getDiabetesResult();
        d.totalCholesterol = e.getTotalCholesterol();
        d.hdlCholesterol = e.getHdlCholesterol();
        d.triglyceride = e.getTriglyceride();
        d.ldlCholesterol = e.getLdlCholesterol();
        d.lipidResult = e.getLipidResult();
        d.serumCreatinine = e.getSerumCreatinine();
        d.egfr = e.getEgfr();
        d.kidneyResult = e.getKidneyResult();
        d.ast = e.getAst();
        d.alt = e.getAlt();
        d.ggt = e.getGgt();
        d.liverResult = e.getLiverResult();
        d.urineProteinResult = e.getUrineProteinResult();
        d.chestXrayResult = e.getChestXrayResult();
        d.pastHistory = e.getPastHistory();
        d.medication = e.getMedication();
        d.needSmokingCessation = e.getNeedSmokingCessation();
        d.needDrinkingReduction = e.getNeedDrinkingReduction();
        d.needPhysicalActivity = e.getNeedPhysicalActivity();
        d.needStrengthExercise = e.getNeedStrengthExercise();
        d.hepBResult = e.getHepBResult();
        d.hepCResult = e.getHepCResult();
        d.depressionResult = e.getDepressionResult();
        d.depressionScore = e.getDepressionScore();
        d.psychosisResult = e.getPsychosisResult();
        d.cognitiveResult = e.getCognitiveResult();
        d.boneDensityResult = e.getBoneDensityResult();
        d.urinationResult = e.getUrinationResult();
        d.rawText = e.getRawText();
        d.sourceFile = e.getSourceFile();
        d.createdAt = e.getCreatedAt();
        d.updatedAt = e.getUpdatedAt();
        return d;
    }

    /** 요청 값을 엔티티에 반영한다(id/감사 컬럼 제외). null 도 그대로 반영해 "값 지움"을 허용한다. */
    public void applyTo(HealthCheckup e) {
        e.setCheckupDate(checkupDate);
        e.setCheckupPlace(checkupPlace);
        e.setCheckupOrg(checkupOrg);
        e.setCheckupDoctor(checkupDoctor);
        e.setOverallJudgment(overallJudgment);
        e.setExtraExams(extraExams);
        e.setSuspectedDisease(suspectedDisease);
        e.setExistingDisease(existingDisease);
        e.setLifestyleAdvice(lifestyleAdvice);
        e.setEtcAdvice(etcAdvice);
        e.setHeightCm(heightCm);
        e.setWeightKg(weightKg);
        e.setBmi(bmi);
        e.setBmiGrade(bmiGrade);
        e.setWaistCm(waistCm);
        e.setWaistResult(waistResult);
        e.setVisionLeft(visionLeft);
        e.setVisionRight(visionRight);
        e.setVisionCorrected(visionCorrected);
        e.setHearingLeft(hearingLeft);
        e.setHearingRight(hearingRight);
        e.setHearingResult(hearingResult);
        e.setSystolicBp(systolicBp);
        e.setDiastolicBp(diastolicBp);
        e.setBpResult(bpResult);
        e.setHemoglobin(hemoglobin);
        e.setAnemiaResult(anemiaResult);
        e.setFastingBloodSugar(fastingBloodSugar);
        e.setDiabetesResult(diabetesResult);
        e.setTotalCholesterol(totalCholesterol);
        e.setHdlCholesterol(hdlCholesterol);
        e.setTriglyceride(triglyceride);
        e.setLdlCholesterol(ldlCholesterol);
        e.setLipidResult(lipidResult);
        e.setSerumCreatinine(serumCreatinine);
        e.setEgfr(egfr);
        e.setKidneyResult(kidneyResult);
        e.setAst(ast);
        e.setAlt(alt);
        e.setGgt(ggt);
        e.setLiverResult(liverResult);
        e.setUrineProteinResult(urineProteinResult);
        e.setChestXrayResult(chestXrayResult);
        e.setPastHistory(pastHistory);
        e.setMedication(medication);
        e.setNeedSmokingCessation(needSmokingCessation);
        e.setNeedDrinkingReduction(needDrinkingReduction);
        e.setNeedPhysicalActivity(needPhysicalActivity);
        e.setNeedStrengthExercise(needStrengthExercise);
        e.setHepBResult(hepBResult);
        e.setHepCResult(hepCResult);
        e.setDepressionResult(depressionResult);
        e.setDepressionScore(depressionScore);
        e.setPsychosisResult(psychosisResult);
        e.setCognitiveResult(cognitiveResult);
        e.setBoneDensityResult(boneDensityResult);
        e.setUrinationResult(urinationResult);
        e.setRawText(rawText);
        e.setSourceFile(sourceFile);
    }
}
