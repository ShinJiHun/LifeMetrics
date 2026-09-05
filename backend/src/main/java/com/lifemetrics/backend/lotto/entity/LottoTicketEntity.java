package com.lifemetrics.backend.lotto.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 실제로 구매한 로또 용지(사진)를 OCR로 읽어 저장한 한 게임(줄) 기록.
 * 용지 한 장에 A~E 최대 5게임이 있을 수 있어, 같은 용지에서 나온 게임들은
 * ticketGroup(UUID)으로 묶고 gameNo(1=A, 2=B, ...)로 구분한다.
 */
@Entity
@Table(name = "lotto_ticket")
@Getter
@Setter
@NoArgsConstructor
public class LottoTicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_group", nullable = false, length = 36)
    private String ticketGroup;

    /** 용지에 인쇄된 회차. 인식 실패 시 null (추후 수동 보정). */
    @Column(name = "round")
    private Integer round;

    @Column(name = "game_no", nullable = false)
    private Integer gameNo;

    @Column(name = "n1", nullable = false)
    private int n1;

    @Column(name = "n2", nullable = false)
    private int n2;

    @Column(name = "n3", nullable = false)
    private int n3;

    @Column(name = "n4", nullable = false)
    private int n4;

    @Column(name = "n5", nullable = false)
    private int n5;

    @Column(name = "n6", nullable = false)
    private int n6;

    /** OCR | MANUAL */
    @Column(name = "source", nullable = false, length = 20)
    private String source = "OCR";

    /** NAS에 저장된 원본 사진 경로 */
    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "purchased_at")
    private LocalDate purchasedAt;

    /** 용지에 인쇄된 발행일시. 같은 용지를 중복 업로드했는지 판단하는 기준(round+issuedAt+번호)으로 쓰인다. */
    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public int[] numbers() {
        return new int[]{n1, n2, n3, n4, n5, n6};
    }
}
