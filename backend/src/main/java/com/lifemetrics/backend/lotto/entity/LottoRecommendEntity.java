package com.lifemetrics.backend.lotto.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "lotto_recommend")
@Getter
public class LottoRecommendEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round")
    private Integer round;

    @Column(name = "game_no")
    private Integer gameNo;

    @Column(name = "n1")
    private int n1;

    @Column(name = "n2")
    private int n2;

    @Column(name = "n3")
    private int n3;

    @Column(name = "n4")
    private int n4;

    @Column(name = "n5")
    private int n5;

    @Column(name = "n6")
    private int n6;
}
