package com.lifemetrics.backend.dto;

import com.lifemetrics.backend.dto.BodyRecordResponse;

import java.util.List;

public class BodyRecordsResponse {

    private List<BodyRecordResponse> records;
    private BodyRecordResponse latest;

    public BodyRecordsResponse(
            List<BodyRecordResponse> records,
            BodyRecordResponse latest
    ) {
        this.records = records;
        this.latest = latest;
    }

    public List<BodyRecordResponse> getRecords() {
        return records;
    }

    public BodyRecordResponse getLatest() {
        return latest;
    }
}
