package com.lifemetrics.backend.lotto.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "lotto_number")
@Getter
@Setter
@NoArgsConstructor
public class LottoNumberEntity {

    @Id
    @Column(name = "id")
    private Integer round;

    @Column(name = "num1")
    private int n1;

    @Column(name = "num2")
    private int n2;

    @Column(name = "num3")
    private int n3;

    @Column(name = "num4")
    private int n4;

    @Column(name = "num5")
    private int n5;

    @Column(name = "num6")
    private int n6;

    @Column(name = "bonus")
    private int bonus;

    @Column(name = "no_date")
    private LocalDate drawDate;
}
