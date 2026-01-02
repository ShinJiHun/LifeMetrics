package com.lifemetrics.backend.service;

import com.lifemetrics.backend.domain.UserInbodyRecord;
import com.lifemetrics.backend.dto.BodyRecordResponse;
import com.lifemetrics.backend.repository.UserInbodyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BodyService {

    private final UserInbodyRecordRepository inbodyRepo;

    public BodyRecordResponse getBodyRecords(Long userId) {
        List<UserInbodyRecord> records =
                inbodyRepo.findByUserIdOrderByRecordDate(userId);

        if (records.isEmpty()) {
            return new BodyRecordResponse(List.of(), null);
        }

        UserInbodyRecord latest = records.get(records.size() - 1);

        if (records.size() >= 2) {
            UserInbodyRecord prev = records.get(records.size() - 2);
            latest.calculateDelta(prev);
        }

        return new BodyRecordResponse(records, latest);
    }
}
