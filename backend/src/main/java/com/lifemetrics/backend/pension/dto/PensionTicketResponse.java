package com.lifemetrics.backend.pension.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PensionTicketResponse {
    private boolean success;
    private String message;
    private List<PensionTicketDto> tickets;
    private int savedCount;
    private int duplicateCount;
}
