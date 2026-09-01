package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 국민건강보험공단 일반건강검진 결과통보서 1건.
 * <p>
 * 검진은 1~2년에 한 번이라 항목이 많아도 단일 테이블로 둔다. 대시보드 시계열 쿼리를
 * 위해 주요 수치는 개별 컬럼으로 파싱해 저장하고, 원본 텍스트는 {@link #rawText} 에
 * 통째로 보관해 공단 양식 변경/누락 항목 대조에 대비한다.
 * <p>
 * 관리자만 조회할 수 있다({@code HealthCheckupController} 에서 강제).
 */
@Entity
@Table(name = "health_checkup")
@Getter
@Setter
public class HealthCheckup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // ── 메타 / 종합소견 ─────────────────────────────
    @Column(name = "checkup_date", nullable = false)
    private LocalDate checkupDate;

    @Column(name = "checkup_place")
    private String checkupPlace; // 내원 | 출장

    @Column(name = "checkup_org")
    private String checkupOrg; // 검진기관명

    @Column(name = "checkup_doctor")
    private String checkupDoctor; // 판정의사

    @Column(name = "overall_judgment")
    private String overallJudgment; // 정상A | 정상B(경계) | 일반 질환의심 | 고혈압·당뇨병 질환의심 | 유질환자

    @Column(name = "extra_exams")
    private String extraExams; // 그 외 받은 검사 (예: "우울증,조기정신증검사")

    @Column(name = "suspected_disease", columnDefinition = "TEXT")
    private String suspectedDisease; // 의심 질환

    @Column(name = "existing_disease", columnDefinition = "TEXT")
    private String existingDisease; // 유질환

    @Column(name = "lifestyle_advice", columnDefinition = "TEXT")
    private String lifestyleAdvice; // 생활습관 관리 소견

    @Column(name = "etc_advice", columnDefinition = "TEXT")
    private String etcAdvice; // 기타 소견

    // ── 계측검사 ────────────────────────────────────
    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "bmi")
    private Double bmi;

    @Column(name = "bmi_grade")
    private String bmiGrade; // 저체중 | 정상 | 과체중 | 비만

    @Column(name = "waist_cm")
    private Double waistCm;

    @Column(name = "waist_result")
    private String waistResult; // 정상 | 복부비만

    @Column(name = "vision_left")
    private Double visionLeft;

    @Column(name = "vision_right")
    private Double visionRight;

    @Column(name = "vision_corrected")
    private Boolean visionCorrected;

    @Column(name = "hearing_left")
    private String hearingLeft;

    @Column(name = "hearing_right")
    private String hearingRight;

    @Column(name = "hearing_result")
    private String hearingResult; // 정상 | 질환의심(40dB 이상)

    // ── 혈압 ────────────────────────────────────────
    @Column(name = "systolic_bp")
    private Integer systolicBp;

    @Column(name = "diastolic_bp")
    private Integer diastolicBp;

    @Column(name = "bp_result")
    private String bpResult; // 정상 | 고혈압 전단계 | 고혈압 의심

    // ── 혈액검사 ────────────────────────────────────
    @Column(name = "hemoglobin")
    private Double hemoglobin; // 혈색소 g/dL

    @Column(name = "anemia_result")
    private String anemiaResult; // 정상 | 빈혈 의심

    @Column(name = "fasting_blood_sugar")
    private Integer fastingBloodSugar; // 공복혈당 mg/dL

    @Column(name = "diabetes_result")
    private String diabetesResult; // 정상 | 공복혈당장애 의심 | 당뇨병 의심

    @Column(name = "total_cholesterol")
    private Integer totalCholesterol; // mg/dL (비해당이면 null)

    @Column(name = "hdl_cholesterol")
    private Integer hdlCholesterol;

    @Column(name = "triglyceride")
    private Integer triglyceride; // 중성지방

    @Column(name = "ldl_cholesterol")
    private Integer ldlCholesterol;

    @Column(name = "lipid_result")
    private String lipidResult; // 정상 | 고콜레스테롤혈증 의심 등

    @Column(name = "serum_creatinine")
    private Double serumCreatinine; // mg/dL

    @Column(name = "egfr")
    private Integer egfr; // 신사구체여과율 mL/min/1.73㎡

    @Column(name = "kidney_result")
    private String kidneyResult; // 정상 | 신장기능 이상 의심

    @Column(name = "ast")
    private Integer ast; // AST(SGOT) IU/L

    @Column(name = "alt")
    private Integer alt; // ALT(SGPT) IU/L

    @Column(name = "ggt")
    private Integer ggt; // 감마지티피 γ-GTP IU/L

    @Column(name = "liver_result")
    private String liverResult; // 정상 | 간기능 이상 의심

    // ── 요검사 / 영상검사 ───────────────────────────
    @Column(name = "urine_protein_result")
    private String urineProteinResult; // 정상 | 경계 | 단백뇨 의심

    @Column(name = "chest_xray_result")
    private String chestXrayResult; // 정상 | 비활동성 폐결핵 | 질환의심

    // ── 진찰(문진) ──────────────────────────────────
    @Column(name = "past_history")
    private String pastHistory; // 과거병력

    @Column(name = "medication")
    private String medication; // 약물치료

    @Column(name = "need_smoking_cessation")
    private Boolean needSmokingCessation;

    @Column(name = "need_drinking_reduction")
    private Boolean needDrinkingReduction;

    @Column(name = "need_physical_activity")
    private Boolean needPhysicalActivity;

    @Column(name = "need_strength_exercise")
    private Boolean needStrengthExercise;

    // ── 항목별 추가검사 (조건부 시행) ───────────────
    @Column(name = "hep_b_result")
    private String hepBResult; // B형간염

    @Column(name = "hep_c_result")
    private String hepCResult; // C형간염

    @Column(name = "depression_result")
    private String depressionResult; // 우울증상 없음 | 가벼운 | 중간 | 심한

    @Column(name = "depression_score")
    private Integer depressionScore; // PHQ-9 점수 (0~27), 있으면

    @Column(name = "psychosis_result")
    private String psychosisResult; // 조기정신증

    @Column(name = "cognitive_result")
    private String cognitiveResult; // 인지기능장애

    @Column(name = "bone_density_result")
    private String boneDensityResult; // 골밀도검사

    @Column(name = "urination_result")
    private String urinationResult; // 배뇨장애

    // ── 원본 보존 ───────────────────────────────────
    @Column(name = "raw_text", columnDefinition = "LONGTEXT")
    private String rawText; // 결과통보서 PDF 추출 원문

    @Column(name = "source_file")
    private String sourceFile; // 업로드한 원본 PDF 파일명

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
