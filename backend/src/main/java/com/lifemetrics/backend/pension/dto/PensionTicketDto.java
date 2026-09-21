package com.lifemetrics.backend.pension.dto;

import com.lifemetrics.backend.pension.entity.PensionTicketEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PensionTicketDto {
    private Long id;
    private Integer round;
    private Integer jo;
    private Integer n1;
    private Integer n2;
    private Integer n3;
    private Integer n4;
    private Integer n5;
    private Integer n6;
    private String source;
    private LocalDate purchasedAt;
    private LocalDateTime createdAt;

    public static PensionTicketDto from(PensionTicketEntity entity) {
        return PensionTicketDto.builder()
                .id(entity.getId())
                .round(entity.getRound())
                .jo(entity.getJo())
                .n1(entity.getN1())
                .n2(entity.getN2())
                .n3(entity.getN3())
                .n4(entity.getN4())
                .n5(entity.getN5())
                .n6(entity.getN6())
                .source(entity.getSource())
                .purchasedAt(entity.getPurchasedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}