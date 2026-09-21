package com.lifemetrics.backend.pension.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pension_numbers", schema = "lotto_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PensionNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회차 (예: 1회, 2회, ...)
    @Column(name = "round_number", nullable = false, unique = true)
    private Integer roundNumber;

    // 당첨 조 (1-5)
    @Column(name = "winning_jo", nullable = false)
    private Integer winningJo;

    // 당첨번호 (6자리, 예: "123456")
    @Column(name = "winning_numbers", nullable = false, length = 10)
    private String winningNumbers;

    // 보너스번호 (2자리, 예: "45")
    @Column(name = "bonus_number", nullable = false, length = 10)
    private String bonusNumber;

    // 당첨일자
    @Column(name = "draw_date")
    private String drawDate;

    // 1등 당첨금 (단위: 원)
    @Column(name = "first_prize_amount")
    private Long firstPrizeAmount;

    // 누적 당첨액 (단위: 원)
    @Column(name = "accumulated_prize_amount")
    private Long accumulatedPrizeAmount;

    // 데이터 생성일시
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 데이터 수정일시
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // DB에 저장되기 전 createdAt, updatedAt 자동 설정
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
