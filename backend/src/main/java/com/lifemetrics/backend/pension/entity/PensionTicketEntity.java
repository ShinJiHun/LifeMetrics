package com.lifemetrics.backend.pension.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 실제로 구매한 연금복권 정보를 저장한 기록.
 * 사용자가 6개 번호를 입력하면 세트로 자동 저장.
 * 연금복권은 고정 형식이라 게임별 구분 필요 없음.
 */
@Entity
@Table(name = "pension_ticket", schema = "lotto_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PensionTicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 회차 번호 (예: 1, 2, 3, ...) */
    @Column(name = "round", nullable = false)
    private Integer round;

    /** 당첨조 (1-5) */
    @Column(name = "jo", nullable = false)
    private Integer jo;

    /** 6자리 번호 */
    @Column(name = "n1", nullable = false)
    private Integer n1;

    @Column(name = "n2", nullable = false)
    private Integer n2;

    @Column(name = "n3", nullable = false)
    private Integer n3;

    @Column(name = "n4", nullable = false)
    private Integer n4;

    @Column(name = "n5", nullable = false)
    private Integer n5;

    @Column(name = "n6", nullable = false)
    private Integer n6;

    /** QR | MANUAL */
    @Column(name = "source", nullable = false, length = 20)
    private String source = "MANUAL";

    /** 구매일자 */
    @Column(name = "purchased_at")
    private LocalDate purchasedAt;

    /** 데이터 생성일시 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public int[] numbers() {
        return new int[]{n1, n2, n3, n4, n5, n6};
    }

    public String numbersAsString() {
        return String.format("%d%d%d%d%d%d", n1, n2, n3, n4, n5, n6);
    }
}
