package com.lifemetrics.backend.health.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 치료·부상 1건에 딸린 진료·처방 1회분. 처방전·약제비 영수증·진료비 세부내역서·처방조제내역 등을 AI 로 읽어 채운다.
 * 진료비 세부내역서처럼 여러 날짜가 한 문서에 있으면 날짜마다 1행이 된다. 원본 파일은 저장하지 않는다.
 */
@Entity
@Table(name = "health_treatment_visit")
@Getter
@Setter
@NoArgsConstructor
public class HealthTreatmentVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "treatment_id", nullable = false)
    private Long treatmentId;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    /** 의료기관명 (병원·약국) */
    private String institution;

    /** 진료과 (예: 정형외과) */
    private String department;

    /** 문서 종류 (처방전, 약제비 영수증, 진료비 세부내역서, 처방조제내역 …) */
    @Column(name = "doc_type")
    private String docType;

    /** 그날 적힌 상병 (예: 장경인대 증후군 M76.3) */
    private String diagnosis;

    /**
     * 진료·처방 항목.
     * [{"category":"약","name":"록소프로펜정","dose":"1정","timesPerDay":3,"days":5,"drugClass":"소염진통제(NSAID)","amount":1200}, …]
     * category: 진찰 / 검사 / 영상 / 주사 / 물리치료 / 처치 / 약 / 재료 / 기타
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items_json", columnDefinition = "json")
    private List<Map<String, Object>> items;

    /** 진료비 총액(원) */
    @Column(name = "total_amount")
    private Integer totalAmount;

    /** 본인부담금(원) */
    @Column(name = "patient_amount")
    private Integer patientAmount;

    /** 한 줄 요약 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "source_file")
    private String sourceFile;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
