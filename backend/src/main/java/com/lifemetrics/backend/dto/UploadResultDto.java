package com.lifemetrics.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class UploadResultDto {
    private List<FileResult> results;

    @Data
    @AllArgsConstructor
    public static class FileResult {
        private String filename;
        private String status;  // success / fail / skip
        private String error;
    }
}
